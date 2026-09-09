package io.toterra.subterra.runtime.save;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

import io.toterra.subterra.Subterra;
import io.toterra.subterra.engine.datapack.DatapackRules;
import io.toterra.subterra.engine.save.migrate.LevelDatReader;
import io.toterra.subterra.engine.save.migrate.LevelDatum;
import io.toterra.subterra.engine.save.migrate.LevelZdt;
import io.toterra.subterra.engine.zd.ZdHeader;

/**
 * p.2.3.2 runtime 壳最小接线：在 {@code ServerStartedEvent} 旁路挂载 level.dat → zdt 迁移（默认不接管
 * 原版加载，渐进增强）。门控 {@code -Dsubterra.probe.save}：不设则完全 no-op。门控开启时读
 * {@code server.getWorldPath(LevelResource.LEVEL_DATA_FILE)} 的 {@code level.dat} 字节 → 经
 * {@code LevelDatReader.read}（纯 JDK NBT）→ {@code LevelDatum} → {@code LevelZdt.toTd}，向 stdout 打
 * 3 行确定性 marker（探针按前缀 {@code [Subterra save]} 匹配）。经 {@code @EventBusSubscriber} 自注册
 * 到 NeoForge 游戏总线（与 SameSeedCompareHook/WorldProfilerHook 同风格，无需改 Subterra.java）。
 * {@code ServerStoppingEvent} 挂空钩子保持对称 —— p.2.3 后续子项的增量写盘挂在此处。
 * <p>
 * p.2.3.2 minimal runtime shell wiring: hooks the level.dat → zdt migration alongside
 * {@code ServerStartedEvent} (off by default — vanilla loading is untouched, progressive). Gated by
 * {@code -Dsubterra.probe.save}: absent → fully no-op. When gated on it reads the {@code level.dat}
 * bytes at {@code server.getWorldPath(LevelResource.LEVEL_DATA_FILE)} → {@code LevelDatReader.read}
 * (pure-JDK NBT) → {@code LevelDatum} → {@code LevelZdt.toTd}, printing 3 deterministic markers to
 * stdout (probes match by the prefix {@code [Subterra save]}). It self-registers on the NeoForge game
 * bus via {@code @EventBusSubscriber} (same style as SameSeedCompareHook / WorldProfilerHook — no
 * Subterra.java edit). A {@code ServerStoppingEvent} no-op hook is kept for symmetry — later p.2.3
 * sub-items hang incremental write-back here.
 */
@EventBusSubscriber(modid = Subterra.MODID, bus = EventBusSubscriber.Bus.GAME)
public final class SaveRuntime {

    /** 探针 marker 前缀 / probe marker prefix. */
    public static final String MARKER = "[Subterra save]";

    private SaveRuntime() {
    }

    /**
     * 探针门控：{@code -Dsubterra.probe.save} 存在且非空且非 {@code 0}/{@code false} 即视为开启
     * （E2E 走 {@code -Psubterra.probe.save=1}）。缺失 / 空白 → no-op。Probe gate: enabled when
     * {@code -Dsubterra.probe.save} is present, non-blank and not {@code 0}/{@code false}
     * (E2E uses {@code -Psubterra.probe.save=1}). Absent/blank → no-op.
     */
    private static boolean savedProbeGated() {
        String v = System.getProperty("subterra.probe.save");
        if (v == null || v.isBlank()) {
            return false;
        }
        String t = v.trim();
        return !"0".equals(t) && !"false".equalsIgnoreCase(t);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onServerStarted(ServerStartedEvent event) {
        if (!savedProbeGated()) {
            return; // no probe gate -> zero impact; vanilla save loading untouched
        }
        try {
            MinecraftServer server = event.getServer();
            if (server == null) {
                print("level name=\"\" seed=0 time=0 rules=null-server");
                return;
            }
            Path levelDat = server.getWorldPath(LevelResource.LEVEL_DATA_FILE);
            System.out.println(MARKER + " level-dat-path=" + levelDat);
            if (!Files.isRegularFile(levelDat)) {
                print("level name=\"\" seed=0 time=0 rules=missing");
                return;
            }
            byte[] bytes = Files.readAllBytes(levelDat);
            LevelDatum d = LevelDatReader.read(bytes);
            String zdt = LevelZdt.toTd(d);
            byte[] zd = LevelZdt.zdPayload(zdt);
            String rulesText = d.rules().isEmpty()
                    ? "count:0"
                    : DatapackRules.render(d.rules());
            print("level name=\"" + d.name() + "\"");
            print("level seed=" + d.seed() + " time=" + d.dayTime() + " rules=" + rulesText);
            print("zdt td-bytes=" + zdt.getBytes(StandardCharsets.UTF_8).length
                    + " zd-header=TIEDBZD:" + ZdHeader.parseVersion(zd, 0));
        } catch (Throwable t) {
            // never propagate past the event dispatch; the boot gate stays green
            print("level name=\"\" seed=0 time=0 rules=error:" + t);
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        // p.2.3 后续子项：把增量的 zdt 写盘逻辑挂在此处（当前为空钩子保持对称）。
        // Later p.2.3 sub-items hang incremental zdt write-back here (empty for symmetry).
        if (!savedProbeGated()) {
            return;
        }
    }

    private static void print(String body) {
        System.out.println(MARKER + " " + body);
    }
}