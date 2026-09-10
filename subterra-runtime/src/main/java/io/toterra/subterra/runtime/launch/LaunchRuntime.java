package io.toterra.subterra.runtime.launch;

import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import org.slf4j.Logger;

import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.List;

/**
 * p.2.19.2 — launch runtime 壳：把 L1 启动层只读校验（{@link JvmEnv} + {@link JvmLaunchArgs}）
 * 收编为确定性健康检查，并挂在 {@code Subterra.java} 的 {@code commonSetup} 挂接点
 * （{@link #bootstrap()}）——保留既有 L1 日志文本（{@code Subterra L1: Java ... verified} /
 * {@code JVM argument package present}，ServerBootProbe 断言面不变）。校验项固定序、禁时序：
 * JVM 版本基线（{@code JvmEnv.MIN_FEATURE}）、ZGC（{@code -XX:+UseZGC}）、堆策略
 * （{@code InitialRAMPercentage/MaxRAMPercentage=75}）、预触摸（{@code -XX:+AlwaysPreTouch}）、
 * Java 25 强封装兼容 opens（{@code --add-opens java.base/java.lang=ALL-UNNAMED}）。
 * <p>
 * 确定性 E2E 钩子与其余探针壳同模式：{@code subterra.probe.launch} 门控（经 gradle -P →
 * runServer system property 转发），非 null 才在 {@code ServerStartedEvent} 重跑同一
 * {@link #check()} 并打 {@code [Subterra launch] ok (checks=N, gaps=...)} 或 mismatch marker。
 * 缺省纯 no-op 壳——门控缺失时对既有 marker 与启动生命周期零影响。
 * <p>
 * p.2.19.2 — the launch runtime shell: folds the L1 launch-layer read-only checks
 * ({@link JvmEnv} + {@link JvmLaunchArgs}) into one deterministic health check, wired at the
 * {@code Subterra.java} {@code commonSetup} hook ({@link #bootstrap()}) — the existing L1 log
 * text ({@code Subterra L1: Java ... verified} / {@code JVM argument package present}) is kept
 * verbatim so the ServerBootProbe assertions stand. Fixed check order, no timing: JVM feature
 * baseline ({@code JvmEnv.MIN_FEATURE}), ZGC ({@code -XX:+UseZGC}), heap policy
 * ({@code InitialRAMPercentage/MaxRAMPercentage=75}), pre-touch ({@code -XX:+AlwaysPreTouch}),
 * and the Java 25 strong-encapsulation compat opens
 * ({@code --add-opens java.base/java.lang=ALL-UNNAMED}).
 * <p>
 * The deterministic E2E hook mirrors the other probe shells: gated by {@code subterra.probe.launch}
 * (forwarded gradle -P → runServer system property), runs only when non-null — re-runs the same
 * {@link #check()} on {@code ServerStartedEvent} and prints a {@code [Subterra launch]
 * ok (checks=N, gaps=...)} or mismatch marker. A pure no-op shell by default — absent gate leaves
 * the existing markers and the boot lifecycle untouched.
 */
public final class LaunchRuntime {

    /** 探针 marker 前缀 / probe marker prefix. */
    public static final String MARKER = "[Subterra launch]";

    /** 固定校验项数（JVM 版本 + 4 静态调优标志 + 1 组 --add-opens 配对）。Fixed check count
     * (JVM feature + 4 static tuning flags + 1 --add-opens pair). */
    public static final int CHECK_COUNT = 6;

    public static final Logger LOGGER = LogUtils.getLogger();

    private LaunchRuntime() {
    }

    /**
     * 确定性健康报告：JVM 基线 + 静态调优标志 + --add-opens 存在性（固定序，无时序）。
     * Deterministic health report: JVM baseline + static tuning flags + --add-opens presence
     * (fixed order, no timing).
     */
    public record LaunchReport(int javaVersion, boolean jvmOk, List<String> missingTuning,
                               List<String> missingOpens) {

        /** 缺失项合并（先 tuning 后 opens，固定序）。Merged missing entries (tuning first, then
         * opens — fixed order). */
        public List<String> gaps() {
            List<String> gaps = new ArrayList<>(missingTuning.size() + missingOpens.size());
            gaps.addAll(missingTuning);
            gaps.addAll(missingOpens);
            return List.copyOf(gaps);
        }

        /** 全部校验通过（版本达标且无缺项）。Healthy when every check passes. */
        public boolean healthy() {
            return jvmOk && missingTuning.isEmpty() && missingOpens.isEmpty();
        }
    }

    /**
     * 运行 {@link #check()}：以 {@code RuntimeMXBean.getInputArguments()} 实参驱动确定性检查。
     * Runs {@link #check()}: drives the deterministic checks from
     * {@code RuntimeMXBean.getInputArguments()}.
     */
    public static LaunchReport check() {
        JvmEnv.Report env = JvmEnv.verify();
        List<String> applied = ManagementFactory.getRuntimeMXBean().getInputArguments();
        List<String> missingTuning = JvmLaunchArgs.missingStaticFlags(applied);
        List<String> missingOpens = missingCompatOpens(applied);
        return new LaunchReport(env.javaVersion(), env.verified(), missingTuning, missingOpens);
    }

    /**
     * {@code --add-opens} 兼容参数存在性检查：{@code java25CompatOpens()} 为成对的
     * {@code --add-opens} + 模块目标。JVM 会把注入的双 token（{@code --add-opens} + 目标）在
     * {@code RuntimeMXBean.getInputArguments()} 中规范化为单 token 等号形式
     * {@code --add-opens=目标}，故两种形式都接受；缺一即记一条缺失（固定序）。Compat-opens
     * presence check: {@code java25CompatOpens()} yields {@code --add-opens} + module-target
     * pairs. The JVM normalizes an injected two-token pair ({@code --add-opens} + target) into the
     * single-token {@code --add-opens=target} form seen from {@code RuntimeMXBean.getInputArguments()},
     * so both forms are accepted; a pair with either half missing is recorded once (fixed order).
     */
    private static List<String> missingCompatOpens(List<String> applied) {
        List<String> opens = JvmLaunchArgs.java25CompatOpens();
        List<String> missing = new ArrayList<>();
        for (int i = 0; i + 1 < opens.size(); i += 2) {
            String flag = opens.get(i);
            String target = opens.get(i + 1);
            boolean mergedForm = containsArg(applied, flag + "=" + target);
            boolean splitForm = containsArg(applied, flag) && containsArg(applied, target);
            if (!mergedForm && !splitForm) {
                missing.add(flag + " " + target);
            }
        }
        return missing;
    }

    private static boolean containsArg(List<String> applied, String arg) {
        if (applied == null) {
            return false;
        }
        for (String a : applied) {
            if (a.equals(arg)) {
                return true;
            }
        }
        return false;
    }

    /**
     * {@code Subterra.java} {@code commonSetup} 挂接点：替换原 JvmEnv/JvmLaunchArgs 只读校验段
     * （日志文本逐字保留），并注册 {@code ServerStarted} 门控（缺省 no-op）。Wired at the
     * {@code Subterra.java} {@code commonSetup} hook: replaces the former JvmEnv/JvmLaunchArgs
     * read-only check block (log text kept verbatim) and registers the {@code ServerStarted}
     * gate (no-op by default).
     */
    public static void bootstrap() {
        LaunchReport report = check();
        LOGGER.info("Subterra L1: Java {} (min supported 21, verified: {})", report.javaVersion(), report.jvmOk());
        if (!report.jvmOk()) {
            LOGGER.warn("Subterra L1: Java {} is below the supported baseline; launch arguments may be incomplete.", report.javaVersion());
        }
        List<String> missing = report.missingTuning();
        if (!missing.isEmpty()) {
            LOGGER.warn("Subterra L1: missing JVM tuning flags: {} — inject the Subterra launch argument package before starting the game (see subterra-launch JvmLaunchArgs).", missing);
        } else {
            LOGGER.info("Subterra L1: JVM argument package present ({})", JvmLaunchArgs.staticTuningFlags().size() + " static flags");
        }
        NeoForge.EVENT_BUS.register(LaunchRuntime.class);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onServerStarted(ServerStartedEvent event) {
        // p.2.19.2 deterministic E2E hook: re-run the same launch health core at startup when a
        // probe flag is forwarded (subterra.probe.launch) — mirrors the other probe-shell gates.
        String probe = System.getProperty("subterra.probe.launch");
        if (probe == null || probe.isBlank()) {
            return;
        }
        LaunchReport report = check();
        if (report.healthy()) {
            LOGGER.info("{} ok (checks={}, gaps=none)", MARKER, CHECK_COUNT);
        } else {
            LOGGER.warn("{} mismatch (checks={}, gaps={})", MARKER, CHECK_COUNT, report.gaps());
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        // no held state -> pure no-op; symmetric empty hook kept (as RulesRuntime/DatapackRuntime).
    }
}
