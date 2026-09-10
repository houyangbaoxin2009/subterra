package io.toterra.subterra.runtime.tie;

import com.mojang.logging.LogUtils;
import io.toterra.subterra.engine.tie.TieFunction;
import io.toterra.subterra.engine.tie.TieLibrary;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import org.slf4j.Logger;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;

/**
 * p.2.19.4 — tie runtime 壳：把 tiec → DLL → FFM 桥（engine.tie.{@link TieLibrary}）收编进
 * boot 生命周期——{@link #bootstrap()}（{@code Subterra.java} 初始化调用）注册
 * {@code ServerStarted} 门控。门控 {@code subterra.probe.tie}（经 gradle -P → runServer system
 * property 转发，与其余探针壳同模式），非 null 才跑；缺省纯 no-op 壳。
 *
 * <p>装载来源（确定性、固定序）：① 系统属性 {@code subterra.tie.lib}（dev 环境由 gradle
 * {@code -Psubterra.tie.lib=<abs path>} 转发，指向 tiec 编译的 dll——与 TieBridgeProbe 同源
 * 同 ABI 的 {@code tiefib_probe.dll}，导出面 {@code tiefib$add} 等）；② 类路径资源
 * {@code /tie/tiefib_probe.dll}（若主 jar 捆绑，提取到临时文件后装载）；③ 两者皆缺 →
 * 确定性 {@code skip (no tie lib)}，不失败（与 ExportHubCommand 的 {@code skip (no save source)}
 * 语义一致）。装载成功后做确定性校验调用（{@code tiefib$add(2,3)==5}，禁时序），打
 * {@code [Subterra tie] ok (lib=..., call=ok)}；符号缺失/调用值不符/装载异常 → mismatch marker
 * （warn，不抛、不失败）。共享装载逻辑直接复用 engine.tie.{@link TieLibrary}（FFM
 * libraryLookup + Arena 生命周期，运行期需 {@code --enable-native-access}，ModDevGradle dev
 * run 默认注入）；devkit TieBridgeProbe 与其 tieBridgeProbe 任务未改动。
 * <p>
 * p.2.19.4 — the tie runtime shell: folds the tiec → DLL → FFM bridge (engine.tie.{@link TieLibrary})
 * into the boot lifecycle — {@link #bootstrap()} (called from the {@code Subterra.java}
 * initialization) registers the {@code ServerStarted} gate. Gated by {@code subterra.probe.tie}
 * (forwarded gradle -P → runServer system property, same pattern as the other probe shells), runs
 * only when non-null; a pure no-op shell by default.
 *
 * <p>Load sources (deterministic, fixed order): ① system property {@code subterra.tie.lib} (a dev
 * environment forwards it via gradle {@code -Psubterra.tie.lib=<abs path>} — a tiec-built dll with
 * the TieBridgeProbe ABI, e.g. {@code tiefib_probe.dll} exporting {@code tiefib$add} etc.);
 * ② classpath resource {@code /tie/tiefib_probe.dll} (when the main jar bundles it; extracted to a
 * temp file before loading); ③ neither present → deterministic {@code skip (no tie lib)}, not a
 * failure (same semantics as ExportHubCommand's {@code skip (no save source)}). After a successful
 * load it runs one deterministic verification call ({@code tiefib$add(2,3)==5}, no timing) and
 * prints {@code [Subterra tie] ok (lib=..., call=ok)}; missing symbol / wrong value / load failure
 * → mismatch marker (warn, never throws, never fails). The shared load path is reused directly from
 * engine.tie.{@link TieLibrary} (FFM libraryLookup + Arena lifecycle; needs
 * {@code --enable-native-access}, which the ModDevGradle dev runs inject by default); the devkit
 * TieBridgeProbe and its tieBridgeProbe task are untouched.
 */
public final class TieRuntime {

    /** 探针 marker 前缀 / probe marker prefix. */
    public static final String MARKER = "[Subterra tie]";

    public static final Logger LOGGER = LogUtils.getLogger();

    /** 类路径捆绑的 tiec 动态库资源（与 TieBridgeProbe 同源同 ABI）。Bundled tiec dll resource
     * (same source/ABI as TieBridgeProbe). */
    private static final String BUNDLED_DLL = "/tie/tiefib_probe.dll";

    /** 类路径资源提取后的临时文件名（固定名，进程内单实例，确定性）。Temp filename after
     * extracting the classpath resource (fixed name, single instance per JVM, deterministic). */
    private static final String STAGED_NAME = "subterra_tie_runtime_probe.dll";

    private TieRuntime() {
    }

    /** 装载来源解析结果：动态库路径 + 是否临时提取（临时文件由壳负责清理）。Resolved load
     * source: the dll path + whether it is a temp extraction (the shell cleans temp files up). */
    private record TieLibSource(Path path, boolean temp) {
    }

    /** 注册 NeoForge 生命周期监听（mod 初始化调用）。Registers the NeoForge lifecycle listeners
     * (call from the mod initialization). */
    public static void bootstrap() {
        NeoForge.EVENT_BUS.register(TieRuntime.class);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onServerStarted(ServerStartedEvent event) {
        // p.2.19.4 deterministic E2E hook: load the tie bridge and run one deterministic ABI call
        // when a probe flag is forwarded (subterra.probe.tie) — mirrors the other probe-shell gates.
        String probe = System.getProperty("subterra.probe.tie");
        if (probe == null || probe.isBlank()) {
            return;
        }
        TieLibSource source = locate();
        if (source == null) {
            LOGGER.info("{} skip (no tie lib)", MARKER);
            return;
        }
        Path dll = source.path();
        try (TieLibrary lib = TieLibrary.load(dll)) {
            Optional<TieFunction> add = lib.find("tiefib$add");
            if (add.isEmpty()) {
                LOGGER.warn("{} mismatch (lib={}, call=symbol missing)", MARKER, dll);
                return;
            }
            if (add.get().invokeI64I64(2, 3) == 5L) {
                LOGGER.info("{} ok (lib={}, call=ok)", MARKER, dll);
            } else {
                LOGGER.warn("{} mismatch (lib={}, call=value)", MARKER, dll);
            }
        } catch (Throwable t) {
            LOGGER.warn("{} mismatch (lib={}, load failed)", MARKER, dll);
            LOGGER.debug("tie load failure detail", t);
        } finally {
            if (source.temp()) {
                try {
                    Files.deleteIfExists(dll);
                } catch (Exception ignored) {
                    // best-effort temp cleanup
                }
            }
        }
    }

    /** 定位 tiec 动态库（固定序：{@code subterra.tie.lib} 属性 → 类路径捆绑资源）；两者皆缺
     * 返回 null。Locates the tiec dll (fixed order: {@code subterra.tie.lib} property → bundled
     * classpath resource); returns null when neither is present. */
    private static TieLibSource locate() {
        String external = System.getProperty("subterra.tie.lib");
        if (external != null && !external.isBlank()) {
            Path p = Path.of(external);
            if (Files.isRegularFile(p)) {
                return new TieLibSource(p, false);
            }
        }
        Path staged = Path.of(System.getProperty("java.io.tmpdir"), STAGED_NAME);
        try (InputStream in = TieRuntime.class.getResourceAsStream(BUNDLED_DLL)) {
            if (in == null) {
                return null;
            }
            Files.copy(in, staged, StandardCopyOption.REPLACE_EXISTING);
            return new TieLibSource(staged, true);
        } catch (Exception e) {
            return null;
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        // no held state -> pure no-op; symmetric empty hook kept (as RulesRuntime/DatapackRuntime).
    }
}
