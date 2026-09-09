package io.toterra.subterra.runtime.saveverify;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.stream.Stream;

import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

import io.toterra.subterra.Subterra;
import io.toterra.subterra.engine.config.TdTable;
import io.toterra.subterra.engine.config.TdValue;
import io.toterra.subterra.engine.save.SaveContainer;
import io.toterra.subterra.engine.save.SaveExportArchive;
import io.toterra.subterra.engine.save.SaveSlot;
import io.toterra.subterra.engine.saveverify.SaveVerifyArchive;
import io.toterra.subterra.engine.saveverify.SaveVerifyLedger;
import io.toterra.subterra.engine.saveverify.VerifyResult;
import io.toterra.subterra.engine.saveverify.sig.Ed25519Keys;

/**
 * p.2.10.5 runtime 壳：把 p.2.10 engine.saveverify「可验证存档案」桥到真实游戏生命周期（默认
 * {@code no-op}，渐进增强）。与 {@code runtime.world.WorldRuntime}（p.2.9.6）完全同构：在
 * {@code ServerStartedEvent} 上于真实游戏 JVM 内做一次确定性「注入密钥 → demo save → export →
 * saveverify encode → verify」闭环，证明可验证存档案底座在游戏 JVM 装载且合同保持。安全起见
 * **只做纯数据闭环，不碰 MC 世界数据**：demo save 用固定数据构建、不读写服务器存档；信封文本
 * 只写隔离 staging（{@code build/tmp/saveverify-e2e}），绝不写服务器原目录。门控
 * {@code -Dsubterra.probe.saveverify}：不设则完全 no-op，对原版存档/世界生命周期零影响；也不与
 * 任何既有 marker 交互（async/sim/world marker 与原路径保持字节原样）。门控开启时向 stdout 打
 * 确定性 marker（探针按前缀 {@code [Subterra saveverify]} 匹配）。经
 * {@code @EventBusSubscriber} 自注册到 NeoForge 游戏总线（同 WorldRuntime/SimRuntime/SaveRuntime，
 * 无需改 Subterra.java）；默认纯 no-op 壳——任意异常被兜底为 {@code FAILED: ...} marker 并带异常栈
 * （供探针断言失败路径，失败不吞，绝不断言中断服务启动）。依赖铁律：runtime 可依赖 engine
 * （{@code engine.saveverify/save/config}）与 MC 事件，禁依赖 migrate/devkit。
 * <p>
 * 本壳刻意不含游戏内存档接管、不写服务器存档；确定性与隔离性由纯数据闭环证明。marker 内容固定格式
 * （无时间戳无随机），供 {@code AsyncE2EProbe} 断言。
 * <p>
 * p.2.10.5 runtime shell: bridges the p.2.10 {@code engine.saveverify} verifiable-save archive to the
 * real game lifecycle (off by default — vanilla save/world lifecycle untouched, progressive enhancement).
 * On {@code ServerStartedEvent} it runs one deterministic <em>injected-key → demo save → export →
 * saveverify encode → verify</em> closed loop inside the real game JVM, proving the verifiable-save base
 * loads in the game JVM and its contract holds. For safety this is a pure-data loop only — no MC world
 * data is touched: the demo save is built from fixed data (never the server's saves) and the envelope
 * text only lands in isolated staging ({@code build/tmp/saveverify-e2e}), never the server's live dirs.
 * Gated by {@code -Dsubterra.probe.saveverify}: absent → fully no-op (never touches existing markers —
 * the async/sim/world markers plus their original-path lines stay byte-identical). When gated on it
 * prints deterministic markers to stdout (probes match by prefix {@code [Subterra saveverify]}). It
 * self-registers on the NeoForge game bus via {@code @EventBusSubscriber} (same style as
 * WorldRuntime/SimRuntime/SaveRuntime — no Subterra.java edit); a pure no-op shell by default — any
 * throwable is caught and reported as a {@code FAILED: ...} marker with the stack (so the probe can
 * assert the failure path — the failure is not swallowed, but it never breaks the boot gate). Dependency
 * rule: the runtime may depend on engine ({@code engine.saveverify/save/config}) and MC events, never on
 * migrate/devkit.
 * <p>
 * This shell deliberately owns no game path and writes no server save; the closure is proven by a pure
 * data loop. The marker body is a fixed format (no timestamps, no randomness), asserted by
 * {@code AsyncE2EProbe}.
 */
@EventBusSubscriber(modid = Subterra.MODID, bus = EventBusSubscriber.Bus.GAME)
public final class SaveVerifyRuntime {

    /** 探针 marker 前缀 / probe marker prefix. */
    public static final String MARKER = "[Subterra saveverify]";

    /** Staging root segments: {@code build/tmp/saveverify-e2e} under the base dir. */
    private static final String STAGING_ROOT = "saveverify-e2e";

    /** 存档文本落盘名。Ledger text file name in staging. */
    private static final String LEDGER_FILE = "ledger.td";

    /**
     * 固定注入密钥（与 p.2.10.4 SaveVerifyProbe 同对，验证「确定性注入契约」）：PKCS#8 私钥 48B +
     * RFC 8032 公钥点 32B 的 base64 常量。Same fixed injected keypair as the p.2.10.4 SaveVerifyProbe
     * (proving the deterministic-keypair-injection contract); PKCS#8 private 48B + RFC 8032 public point
     * 32B, base64.
     */
    private static final String KEY1_PKCS8 = "MC4CAQAwBQYDK2VwBCIEIKdQ9/NsXKkgSo0xd4xcdD+nftR7/sD8D5l0h3xS8twS";
    private static final String KEY1_POINT = "r0zwwEvbROGDiTlj8CRpgiJPg5eupBavwvcoZ5kF2sM=";

    private SaveVerifyRuntime() {
    }

    /**
     * 探针门控：{@code -Dsubterra.probe.saveverify} 存在且非空且非 {@code 0}/{@code false} 即视为开启
     * （E2E 走 {@code -Psubterra.probe.saveverify=1}，经根 build.gradle server run 块转发到游戏 JVM）。
     * 缺失 / 空白 → no-op。Probe gate: enabled when {@code -Dsubterra.probe.saveverify} is present,
     * non-blank and not {@code 0}/{@code false} (E2E uses {@code -Psubterra.probe.saveverify=1},
     * forwarded to the game JVM by the root server run block). Absent/blank → no-op.
     */
    private static boolean saveVerifyProbeGated() {
        String v = System.getProperty("subterra.probe.saveverify");
        if (v == null || v.isBlank()) {
            return false;
        }
        String t = v.trim();
        return !"0".equals(t) && !"false".equalsIgnoreCase(t);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onServerStarted(ServerStartedEvent event) {
        if (!saveVerifyProbeGated()) {
            return; // no probe gate -> zero impact; vanilla save/world lifecycle untouched
        }
        try {
            print("saveverify-shell-gate=on");
            runClosedLoop(event);
        } catch (Throwable t) {
            // never propagate past the event dispatch; the boot gate stays green.
            // The failure is NOT swallowed: report it with a stack for probe assertion.
            print("FAILED: " + t);
            t.printStackTrace();
            writeFailedMarker();
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        // 无持有状态 -> 纯 no-op；对称保留空钩子（同 WorldRuntime/SaveRuntime）。
        // No held state -> pure no-op; empty hook kept for symmetry (as WorldRuntime/SaveRuntime).
        if (!saveVerifyProbeGated()) {
            return;
        }
    }

    /**
     * The deterministic closed loop (no MC world data touched): a fixed demo {@code SaveContainer} →
     * {@code SaveExportArchive.export} → UTF-8 payload; the fixed injected keys encode a verifiable-save
     * archive at {@code seq} (gate property {@code subterra.probe.saveverify.seq}, default 1); the text is
     * written to staging {@code ledger.td}; {@code SaveVerifyLedger.verify(archive, keys, seq-1)} must be
     * valid. Prints the fixed PASS marker, otherwise throws → FAILED marker.
     *
     * 确定性闭环（不碰 MC 世界数据）：固定 demo {@code SaveContainer} → {@code SaveExportArchive.export}
     * → UTF-8 payload；固定注入密钥在 {@code seq}（门控属性 {@code subterra.probe.saveverify.seq}，默认
     * 1）编码可验证存档案；文本写 staging {@code ledger.td}；{@code SaveVerifyLedger.verify(archive, keys, seq-1)}
     * 必须 valid。通过打固定 PASS marker，否则抛出让 FAILED marker。
     */
    private static void runClosedLoop(ServerStartedEvent event) throws IOException {
        // Staging base: <projectDir|java.io.tmpdir>/build/tmp/saveverify-e2e. Fixed per boot; cleaned first.
        Path staging = stagingBase().resolve("build").resolve("tmp").resolve(STAGING_ROOT).toAbsolutePath().normalize();
        deleteRecursively(staging);
        Files.createDirectories(staging);

        long seq = resolveSeq();

        // Deterministic demo save (pure engine data, never the server's own saves).
        byte[] payload = SaveExportArchive.export(demoContainer()).getBytes(StandardCharsets.UTF_8);

        // Deterministic keypair injection (same key as the p.2.10.4 SaveVerifyProbe).
        Ed25519Keys keys = Ed25519Keys.fromEncoded(
                Base64.getDecoder().decode(KEY1_PKCS8),
                Base64.getDecoder().decode(KEY1_POINT));

        String ledger = SaveVerifyArchive.encode(keys, payload, seq);

        // Land the envelope text only in isolated staging (never the server's live save dirs).
        Files.writeString(staging.resolve(LEDGER_FILE), ledger, StandardCharsets.UTF_8);

        // Replay-guard verify against seq-1 (fresh history): must be valid.
        VerifyResult result = SaveVerifyLedger.verify(ledger, keys, seq - 1);
        if (!result.valid() || !result.reason().isEmpty()) {
            throw new IllegalStateException("verify failed with reason=" + result.reason());
        }

        // Fixed format, deterministic, no timestamp / no randomness.
        print("PASS seq=" + seq + " valid");
    }

    /** Staging base dir: {@code subterra.projectDir} property (root project, as WorldRuntime/AsyncE2EProbe) or {@code java.io.tmpdir}. */
    private static Path stagingBase() {
        String prop = System.getProperty("subterra.projectDir");
        if (prop != null && !prop.isBlank()) {
            return Path.of(prop);
        }
        return Path.of(System.getProperty("java.io.tmpdir"));
    }

    /** Resolves the archive sequence from the gate property {@code subterra.probe.saveverify.seq} (default 1). */
    private static long resolveSeq() {
        String v = System.getProperty("subterra.probe.saveverify.seq", "1");
        return Long.parseLong(v == null || v.isBlank() ? "1" : v.trim());
    }

    /**
     * Builds a deterministic demo {@code SaveContainer} (world + ledger + relic slots, fixed td docs) —
     * the same shape family as the p.2.10.4 SaveVerifyProbe fixture, but self-contained here.
     */
    private static SaveContainer demoContainer() {
        TdTable worldDoc = TdTable.builder()
                .put("gametime", TdValue.of(12000L))
                .put("dayTime", TdValue.of(6000L))
                .put("spawnX", TdValue.of(0L))
                .put("spawnY", TdValue.of(64L))
                .put("spawnZ", TdValue.of(0L))
                .put("dimension", TdValue.str("toterra:overworld"))
                .build();
        TdTable ledgerDoc = TdTable.builder()
                .put("runes.ancient", TdTable.builder()
                        .put("count", TdValue.of(1L)).put("rarity", TdValue.of(1L)).put("seed", TdValue.str("seed-ancient")).build())
                .put("relics.dusk-brooch", TdTable.builder()
                        .put("count", TdValue.of(1L)).put("rarity", TdValue.of(2L)).put("seed", TdValue.str("seed-brooch")).build())
                .build();
        TdTable relicDoc = TdTable.builder()
                .put("relics.dusk-brooch", TdTable.builder()
                        .put("stack", TdValue.of(1L)).put("enchant", TdValue.str("protection_3")).put("durability", TdValue.of(120L)).build())
                .build();
        return new SaveContainer()
                .attach(SaveSlot.WORLD, worldDoc)
                .attach(SaveSlot.LEDGER, ledgerDoc)
                .attach(SaveSlot.RELIC, relicDoc);
    }

    /** Recursively deletes a directory tree if present (staging hygiene). */
    private static void deleteRecursively(Path dir) {
        if (!Files.exists(dir)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(dir)) {
            for (Path p : walk.sorted(java.util.Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(p);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("staging cleanup failed: " + dir, e);
        }
    }

    /** Writes a fixed fail marker file into staging (optional side-channel for probe assertion). */
    private static void writeFailedMarker() {
        try {
            Path staging = stagingBase().resolve("build").resolve("tmp").resolve(STAGING_ROOT).toAbsolutePath().normalize();
            Files.createDirectories(staging);
            Files.writeString(staging.resolve("FAILED"), "saveverify shell FAILED", StandardCharsets.UTF_8);
        } catch (IOException e) {
            // best-effort; the stdout FAILED marker is the primary signal.
        }
    }

    private static void print(String body) {
        System.out.println(MARKER + " " + body);
    }
}