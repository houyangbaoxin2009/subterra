package io.toterra.subterra.runtime.render;

import com.mojang.logging.LogUtils;
import io.toterra.subterra.engine.render.instancing.BackendRegistry;
import io.toterra.subterra.engine.render.instancing.InstanceFormat;
import io.toterra.subterra.engine.render.instancing.InstanceFormat.ScalarType;
import io.toterra.subterra.engine.render.instancing.RenderBackend;
import io.toterra.subterra.engine.render.instancing.ShaderTemplate;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import org.slf4j.Logger;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * p.2.27.1.2 — render runtime 壳：把 p.2.27.1.1 的 engine.render.instancing（Flywheel 1.0.6
 * instancing-core 纯 JDK 移植：{@link InstanceFormat} / {@link ShaderTemplate} /
 * {@link RenderBackend} + {@link BackendRegistry}）收编进 boot 生命周期的确定性装载验证。
 * {@link #bootstrap()}（{@code Subterra.java} 构造调用）注册 {@code ServerStartedEvent} 门控；
 * 门控 {@code subterra.probe.render}（经 gradle -P → runServer system property 转发，与其余探针壳
 * 同模式）非 null 才跑——缺省纯 no-op 壳，对启动生命周期零影响。
 * <p>
 * 确定性样例装载（固定序、禁时序、禁 sleep）：实例化 {@link BackendRegistry} 并注册三个固定序
 * 示例后端（两个平局优先级 + 一个更高优先级，供确定性缺省选择语义演示），构造一个多字段
 * {@link InstanceFormat}（标量/向量/矩阵/数组，声明序固定）与一个 {@link ShaderTemplate}
 * （部件拼装 + 占位符替换），并对 canonical 编码做逐字节复验（同声明再构建一次 →
 * {@code canonicalBytes()} 逐字节一致）。全部通过打
 * {@code [Subterra render] ok (backends=N, format=<canonical 摘要>, shader=<字节数>, verify=ok)}
 * （canonical 摘要 = 规范文本长度 + 确定性 hashCode 十六进制）；装载违约/程序错误（样例合法，
 * 正常不可达）打 {@code render mismatch (error=...)} marker。示例后端注册进静态注册表属探针门控
 * 的 dev 装载面，真实 GPU/MC 后端注入留 p.2.27。
 * <p>
 * <b>p.2.27.2 三 MC 钩子真实注入（确定性校验接线）</b>：Flywheel 式实例化渲染挂进 Minecraft
 * 客户端渲染管线，p.2.27.2 在以下三点接入（各钩子均以本壳的确定性装载/格式校验为前置；真实
 * MC/OpenGL 渲染接管不在本子项——只读核对 + 确定性 marker，不改变渲染输出）：
 * <ol>
 *   <li>{@code net.minecraft.client.renderer.LevelRenderer}（区块级实例化渲染编译/装载面——
 *       {@link RenderStage} 固定序经 {@link RenderHooks#serverWiringCheck()} 只读核对，客户端
 *       实际 LevelRenderer 实例与 NeoForge 阶段面经 {@link RenderHooksClient} 核对）；</li>
 *   <li>{@code net.minecraft.client.renderer.block.ModelBlockRenderer}（方块模型实例数据面——
 *       {@code VertexLayout.BLOCK} 32B 布局/偏移只读核对，实例字段布局对齐该布局）；</li>
 *   <li>{@code net.minecraft.client.renderer.entity.EntityRenderDispatcher}（实体渲染调度面——
 *       按确定性方块实例格式经 {@link BackendRegistry#defaultFor} 选缺省后端）。</li>
 * </ol>
 * 门控同 {@code subterra.probe.render}（默认 no-op）：服务端门控内打 {@code hooks ok (level=..,
 * blockModel=.., entity=..)}（供 E2E 断言），客户端首个 level 渲染阶段打 {@code hooks client ok
 * (...)}（runClient/单机 dev 校验面）。接续表另见仓库 runtime 接线文档；本子项交付门控 marker、
 * 装载确定性证明与三钩子校验接线。
 * <p>
 * p.2.27.1.2 — the render runtime shell: folds the p.2.27.1.1 engine.render.instancing
 * (the Flywheel 1.0.6 instancing-core pure-JDK port: {@link InstanceFormat} /
 * {@link ShaderTemplate} / {@link RenderBackend} + {@link BackendRegistry}) into the boot
 * lifecycle as a deterministic load verification. {@link #bootstrap()} (called from the
 * {@code Subterra.java} constructor) registers the {@code ServerStartedEvent} gate; gated by
 * {@code subterra.probe.render} (forwarded gradle -P → runServer system property, same pattern
 * as the other probe shells), runs only when non-null — a pure no-op shell by default, zero
 * impact on the boot lifecycle.
 * <p>
 * Deterministic sample load (fixed order, no timing, no sleeps): instantiates
 * {@link BackendRegistry} and registers three fixed-order sample backends (two tied priorities
 * + one higher priority, demonstrating the deterministic default-selection semantics), builds a
 * multi-field {@link InstanceFormat} (scalar / vector / matrix / array, fixed declaration
 * order) and a {@link ShaderTemplate} (parts assembly + placeholder substitution), and
 * re-verifies the canonical encoding byte-for-byte (building the same declarations again →
 * {@code canonicalBytes()} byte-identical). On full success it prints
 * {@code [Subterra render] ok (backends=N, format=<canonical digest>, shader=<byte count>,
 * verify=ok)} (canonical digest = canonical-text length + deterministic hashCode in hex); a
 * load violation / program error (the samples are legal, so normally unreachable) prints a
 * {@code render mismatch (error=...)} marker instead. The sample backends registered into the
 * static registry are a probe-gated dev load surface; the real GPU/MC backend injection lands
 * with p.2.27.
 * <p>
 * <b>p.2.27.2 real injection of the three MC hooks (deterministic check wiring)</b>:
 * Flywheel-style instanced rendering hooks the Minecraft client render pipeline; p.2.27.2 wires
 * the following three points (each hook builds on this shell's deterministic load / format
 * verification — real MC/OpenGL render takeover is out of scope here: read-only verification +
 * deterministic marker, render output untouched):
 * <ol>
 *   <li>{@code net.minecraft.client.renderer.LevelRenderer} (chunk-level instanced-render
 *       compile/load surface — the {@link RenderStage} fixed order is read-only checked by
 *       {@link RenderHooks#serverWiringCheck()}, and the live LevelRenderer + the NeoForge stage
 *       surface are checked by {@link RenderHooksClient});</li>
 *   <li>{@code net.minecraft.client.renderer.block.ModelBlockRenderer} (block-model instance
 *       data surface — read-only check of the {@code VertexLayout.BLOCK} 32B layout/offsets, the
 *       instance field layout aligns to it);</li>
 *   <li>{@code net.minecraft.client.renderer.entity.EntityRenderDispatcher} (entity render
 *       dispatch surface — picks the default backend per the deterministic block-instance format
 *       via {@link BackendRegistry#defaultFor}).</li>
 * </ol>
 * Same gate {@code subterra.probe.render} (default no-op): the server-side gate prints
 * {@code hooks ok (level=.., blockModel=.., entity=..)} (E2E-asserted), and the first client
 * level-render stage prints {@code hooks client ok (...)} (runClient / singleplayer dev-check
 * surface). The wiring table also lives in the repository runtime wiring docs; this sub-item
 * delivers the gated marker, the load-determinism proof and the three-hook check wiring.
 */
public final class RenderRuntime {

    /** 探针 marker 前缀 / probe marker prefix. */
    public static final String MARKER = "[Subterra render]";

    public static final Logger LOGGER = LogUtils.getLogger();

    private RenderRuntime() {
    }

    /** 注册 NeoForge 生命周期监听（mod 构造调用）。Registers the NeoForge lifecycle listeners (call
     * from the mod constructor). */
    public static void bootstrap() {
        NeoForge.EVENT_BUS.register(RenderRuntime.class);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onServerStarted(ServerStartedEvent event) {
        // p.2.27.1.2 deterministic E2E hook: run the engine.render.instancing sample load at
        // startup when a probe flag is forwarded (subterra.probe.render) — mirrors the other
        // probe-shell gates, so no console command round-trips through the gradle-forked server
        // JVM stdin. Default no-op.
        String probe = System.getProperty("subterra.probe.render");
        if (probe == null || probe.isBlank()) {
            return;
        }
        try {
            // Fixed-order sample registration (no timing, no randomness); the static registry
            // is empty in a fresh game JVM, and this probe-gated dev load is additive.
            BackendRegistry.register(sampleBackend("sample.instanced", 10, "sample instanced-arrays backend"));
            BackendRegistry.register(sampleBackend("sample.indexed", 10, "sample indexed backend"));
            BackendRegistry.register(sampleBackend("sample.universal", 20, "sample universal backend"));
            int backends = BackendRegistry.backends().size();

            // Deterministic sample format: multi-field, fixed declaration order.
            InstanceFormat format = sampleFormat();
            byte[] canonical = format.canonicalBytes();
            // Byte-for-byte re-verification: same declarations -> same canonical bytes.
            if (!Arrays.equals(canonical, sampleFormat().canonicalBytes())) {
                throw new IllegalStateException("format canonical bytes not deterministic");
            }

            // Deterministic sample shader template: parts assembly + fixed-order substitution.
            ShaderTemplate template = sampleTemplate();
            byte[] shader = template.compileBytes();
            if (!Arrays.equals(shader, template.compileBytes())) {
                throw new IllegalStateException("shader bytes not deterministic");
            }
            if (!template.placeholderNames().isEmpty()) {
                throw new IllegalStateException("sample shader placeholders unresolved");
            }

            String digest = "len:" + canonical.length + " hash:"
                    + Integer.toHexString(format.canonicalText().hashCode());
            LOGGER.info("{} ok (backends={}, format={}, shader={} bytes, verify=ok)",
                    MARKER, backends, digest, shader.length);

            // p.2.27.2: three MC hooks deterministic check wiring (RenderStage fixed order /
            // VertexLayout.BLOCK 32B layout / BackendRegistry.defaultFor backend selection),
            // gated by the same subterra.probe.render property — read-only, emits the
            // "hooks ok" marker (E2E-asserted) or a "hooks mismatch" marker on a violation.
            RenderHooks.serverWiringCheck();
        } catch (RuntimeException e) {
            // the samples are legal, so a mismatch is a program error — never emit a false ok.
            LOGGER.warn("{} render mismatch (error={})", MARKER, e.getMessage());
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        // no held state -> pure no-op; symmetric empty hook kept (as the other shells).
    }

    /** 固定序示例格式：标量 + 向量 + 矩阵 + 数组，声明序固定（布局确定性由 engine 保证）。
     * Fixed-order sample format: scalar + vector + matrix + array, fixed declaration order
     * (layout determinism guaranteed by the engine). */
    private static InstanceFormat sampleFormat() {
        return InstanceFormat.builder()
                .scalar("model_index", ScalarType.U32)
                .vector("position", ScalarType.F32, 3)
                .matrix("transform", ScalarType.F32, 4)
                .scalarArray("tint", ScalarType.U8, 8)
                .build();
    }

    /** 固定序示例着色器模板：占位符替换后无残留（{@code compileBytes()} 可用）。
     * Fixed-order sample shader template: fully substituted, no leftover placeholders
     * ({@code compileBytes()} usable). */
    private static ShaderTemplate sampleTemplate() {
        ShaderTemplate template = ShaderTemplate.fromParts(
                List.of("#version 450",
                        "#extension GL_ARB_bindless_texture : require"),
                "layout(location = 0) in vec3 position;\n"
                        + "layout(location = 1) in vec4 {{color}};\n"
                        + "void main() { gl_Position = vec4(position, 1.0); fragColor = {{color}}; }",
                List.of("// end of sample vertex stage"));
        return template.substituteAll(Map.of("color", "vec4(1.0, 1.0, 1.0, 1.0)"));
    }

    /** 确定性示例后端：支持任意非 null 格式，优先级固定。Deterministic sample backend: supports any
     * non-null format, fixed priority. */
    private static RenderBackend sampleBackend(String name, int priority, String description) {
        return new RenderBackend() {
            @Override
            public String name() {
                return name;
            }

            @Override
            public boolean supports(InstanceFormat format) {
                return format != null;
            }

            @Override
            public String description() {
                return description;
            }

            @Override
            public int priority() {
                return priority;
            }
        };
    }
}
