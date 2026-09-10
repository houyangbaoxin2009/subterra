package io.toterra.subterra.runtime.fix;

import com.mojang.logging.LogUtils;
import io.toterra.subterra.runtime.fix.Java25Gaps.Gap;
import io.toterra.subterra.runtime.fix.Java25Gaps.Status;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;

/**
 * p.2.19.2 — runtime-fix 壳：把 {@link Java25Gaps} registry（纯 RCA 登记，Status.OPEN/PATCHED）
 * 挂进 boot 生命周期——{@link #bootstrap()}（{@code Subterra.java} 构造/初始化调用）注册
 * {@code ServerStarted} 门控。只读消费 {@link Java25Gaps#registry()}，不改 registry、不改
 * CompatProbe。确定性、固定序（registry 为不可变 List，遍历即注册序）、禁时序：open=0 时打
 * {@code [Subterra fix] ok (open=N, patched=M)}；有 OPEN 时打 warn marker 并列出 OPEN gap 名
 * （{@code module} 字段，固定序）。门控 {@code subterra.probe.fix}（经 gradle -P → runServer
 * system property 转发），非 null 才跑；缺省纯 no-op 壳。
 * <p>
 * p.2.19.2 — the runtime-fix shell: wires the {@link Java25Gaps} registry (pure RCA register,
 * Status.OPEN/PATCHED) into the boot lifecycle — {@link #bootstrap()} (called from the
 * {@code Subterra.java} initialization) registers the {@code ServerStarted} gate. It consumes
 * {@link Java25Gaps#registry()} read-only; the registry and CompatProbe stay untouched.
 * Deterministic, fixed order (the registry is an immutable list, iteration order = registration
 * order), no timing: with zero OPEN gaps it prints {@code [Subterra fix] ok (open=N, patched=M)};
 * with OPEN gaps it prints a warn marker listing the OPEN gap names ({@code module} field, fixed
 * order). Gated by {@code subterra.probe.fix} (forwarded gradle -P → runServer system property),
 * runs only when non-null; a pure no-op shell by default.
 */
public final class FixRuntime {

    /** 探针 marker 前缀 / probe marker prefix. */
    public static final String MARKER = "[Subterra fix]";

    public static final Logger LOGGER = LogUtils.getLogger();

    private FixRuntime() {
    }

    /** 注册 NeoForge 生命周期监听（mod 初始化调用）。Registers the NeoForge lifecycle listeners
     * (call from the mod initialization). */
    public static void bootstrap() {
        NeoForge.EVENT_BUS.register(FixRuntime.class);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onServerStarted(ServerStartedEvent event) {
        // p.2.19.2 deterministic E2E hook: fold the Java25Gaps registry into the boot gate when a
        // probe flag is forwarded (subterra.probe.fix) — mirrors the other probe-shell gates.
        String probe = System.getProperty("subterra.probe.fix");
        if (probe == null || probe.isBlank()) {
            return;
        }
        List<Gap> registry = Java25Gaps.registry(); // immutable, fixed order
        int open = 0;
        int patched = 0;
        for (Gap gap : registry) {
            if (gap.status() == Status.OPEN) {
                open++;
            } else {
                patched++;
            }
        }
        if (open == 0) {
            LOGGER.info("{} ok (open={}, patched={})", MARKER, open, patched);
        } else {
            List<String> openGaps = new ArrayList<>();
            for (Gap gap : registry) {
                if (gap.status() == Status.OPEN) {
                    openGaps.add(gap.module());
                }
            }
            LOGGER.warn("{} open gaps (open={}, patched={}): {}", MARKER, open, patched, openGaps);
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        // no held state -> pure no-op; symmetric empty hook kept (as RulesRuntime/DatapackRuntime).
    }
}
