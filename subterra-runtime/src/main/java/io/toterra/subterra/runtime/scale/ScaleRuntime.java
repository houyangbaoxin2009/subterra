package io.toterra.subterra.runtime.scale;

import com.mojang.logging.LogUtils;
import io.toterra.subterra.engine.config.TdTable;
import io.toterra.subterra.engine.config.TdValue;
import io.toterra.subterra.engine.scale.ScaleData;
import io.toterra.subterra.engine.scale.ScaleModifier;
import io.toterra.subterra.engine.scale.ScaleRuleDocument;
import io.toterra.subterra.engine.scale.ScaleType;
import io.toterra.subterra.engine.scale.ScaledEntityData;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import org.slf4j.Logger;

import java.util.List;
import java.util.Set;

/**
 * p.2.25.2 — scale runtime 壳：把 p.2.25 的 engine.scale（p.2.25.1 Pehkui-Rebuilt 核心移植：
 * 固定 9 维 {@link ScaleType}、每实体每维度 {@link ScaleData}、乘法修饰符 {@link ScaleModifier}、
 * 按标签基础缩放 {@link ScaledEntityData}、td 化数据包规则 {@link ScaleRuleDocument}）收编进 boot
 * 生命周期的门控确定性核对。{@link #bootstrap()}（{@code Subterra.java} 构造调用）注册
 * {@code ServerStartedEvent} 门控；门控 {@code subterra.probe.scale}（经 gradle -P → runServer
 * system property 转发，与其余探针壳同模式）非 null 才跑——缺省纯 no-op 壳，对启动生命周期零影响。
 * <p>
 * 确定性样例消费（固定序、禁时序、禁 sleep）：构造固定 {@link ScaleData}（三类型值：BASE/HEIGHT/
 * runSample 里嵌定的三类型）→ 规则 td 文档 {@link ScaleRuleDocument#fromTd} 装载（含重复
 * {@code type+tag} 首现胜出丢弃）→ {@link ScaleRuleDocument#applyRules} 以覆盖语义应用命中僵尸标签
 * 的规则 → 两个固定 {@link ScaleModifier} 以注册序 {@link ScaleModifier#applyAll} 折叠 → 对最终
 * {@link ScaleData} 打 {@code type=value} 固定 {@link ScaleType#values()} 序摘要；全部对「同一驱动从
 * 头重建」做摘要复验（确定性证明）。全部通过打
 * {@code [Subterra scale] ok (types=N, data=<摘要>, rules=M, verify=ok)}
 * （types = {@link ScaleType#values()} 维度数，固定 9；data = 最终缩放按固定维度序 {@code type=value}
 * 逗号连接摘要；rules = 装载后规则条数，重复 type+tag 首现胜出后计；verify=ok）；违约/程序错误
 * （样例合法，正常不可达）打 {@code [Subterra scale] mismatch (error=...)} marker，绝不打假 ok。
 * <p>
 * <b>MC 实体缩放生命周期接线面（scale binding，p.2.25 后接线点，本子项只做门控确定性核对，不引
 * MC 实体注入）</b>：engine.scale 是纯 JDK 确定性缩放数据面，真实 MC 实体的缩放装载/驱动/渲染接管
 * 留后续里程碑（避免范围膨胀）。三面接线面文档化如下（沿用 p.2.27.2 RenderHooks/RenderHooksClient
 * 的「server 侧只读核对 + 客户端事件注入」范式，均以本壳确定性核对为前置）：
 * <ol>
 *   <li><b>数据装载面（data load）</b>：把数据包的 {@code scale_rules} td 文档装载为
 *       {@link ScaleRuleDocument}（{@code fromTd}，重复 type+tag 首现胜出），并把实体的类型标签
 *       （原版 {@code EntityType} 的 tag，如 {@code minecraft:zombie}）解析成 {@link Set} 供
 *       {@link ScaleRuleDocument#applyRules} 消费；后续接线点为数据包加载事件 → 规则装载 → 实体
 *       tag → 规则应用，本子项以固定 td 文档 + 固定僵尸标签代偿；</li>
 *   <li><b>驱动更新面（drive update）</b>：逐实体 tick 把装载后的基础 {@link ScaleData} 与
 *       修饰符队列（攻击/药效/方块状态等乘法修饰符）经 {@link ScaleModifier#applyAll} 按注册序
 *       折叠出当前维度值，并把标签基础缩放经 {@link ScaledEntityData#merge} 并入；后续接线点为
 *       实体 tick → 修饰符递增注册 → 维度折叠，本子项以两个固定修饰符注册序应用代偿；</li>
 *   <li><b>渲染消费面（render consume）</b>：客户端把最终维度值映射到 MC 实体渲染管线
 *       （原版 {@code EntityRenderDispatcher} 的缩放字段与模型矩阵/碰撞箱），按
 *       {@link ScaleType} 逐维度消费 {@link ScaleData#get}；后续接线点为实体渲染位移/缩放 →
 *       {@code scaleValues} 注入，本子项仅消费 {@code ScaleData} 数据面，不引渲染注入。</li>
 * </ol>
 * 门控同 {@code subterra.probe.scale}（默认 no-op）：服务端门控内打 {@code ok (types=.., data=..,
 * rules=.., verify=ok)}（供 E2E 断言）。接续表另见仓库 runtime 接线文档；本子项交付门控 marker 与
 * engine.scale 数据装载/驱动/渲染消费数据面在真实 boot 生命周期上的确定性证明。
 * <p>
 * p.2.25.2 — the scale runtime shell: folds the p.2.25 engine.scale (the p.2.25.1 Pehkui-Rebuilt
 * core port: fixed nine-dimension {@link ScaleType}, per-entity per-dimension {@link ScaleData},
 * multiplicative {@link ScaleModifier}, per-tag base scales {@link ScaledEntityData}, td-ized
 * data-pack rules {@link ScaleRuleDocument}) into the boot lifecycle as a gated deterministic
 * verification. {@link #bootstrap()} (called from the {@code Subterra.java} constructor) registers
 * the {@code ServerStartedEvent} gate; gated by {@code subterra.probe.scale} (forwarded gradle -P →
 * runServer system property, same pattern as the other probe shells), runs only when non-null — a
 * pure no-op shell by default, zero impact on the boot lifecycle.
 * <p>
 * Deterministic sample consumption (fixed order, no timing, no sleeps): builds a fixed
 * {@link ScaleData} (three typed values) → loads the rules td document via
 * {@link ScaleRuleDocument#fromTd} (dropping duplicate {@code type+tag} first-occurrence-wins) →
 * applies the zombie-tag-matching rules via {@link ScaleRuleDocument#applyRules} (override
 * semantics) → folds two fixed {@link ScaleModifier}s in registration order via
 * {@link ScaleModifier#applyAll} → digests the final {@link ScaleData} as {@code type=value} in
 * the fixed {@link ScaleType#values()} order; every digest is re-verified against an identical
 * from-scratch rebuild (the determinism proof). On full success it prints
 * {@code [Subterra scale] ok (types=N, data=<digest>, rules=M, verify=ok)}
 * (types = the {@link ScaleType#values()} dimension count, fixed 9; data = the final scale
 * {@code type=value} digest in fixed dimension order; rules = the loaded rule count after
 * first-occurrence-wins duplicate dropping; verify=ok); a load violation / program error (the
 * samples are legal, so normally unreachable) prints an
 * {@code [Subterra scale] mismatch (error=...)} marker instead — a false ok is never emitted.
 * <p>
 * <b>MC entity-scale lifecycle wiring surfaces (post-p.2.25 binding points; this sub-item only does
 * the gated deterministic verification, no MC entity injection)</b>: engine.scale is the pure-JDK
 * deterministic scaling data plane, and the real MC-entity scale load/drive/render takeover lands
 * with a later milestone (scope containment). The three wiring surfaces are documented here
 * (following the p.2.27.2 RenderHooks/RenderHooksClient "server-side read-only check + client-event
 * injection" pattern, each building on this shell's deterministic check):
 * <ol>
 *   <li><b>Data-load surface</b>: loads the datapack {@code scale_rules} td document into a
 *       {@link ScaleRuleDocument} ({@code fromTd}, duplicate type+tag first-occurrence-wins) and
 *       resolves an MC entity's type tags (vanilla {@code EntityType} tags such as
 *       {@code minecraft:zombie}) into the {@link Set} that {@link ScaleRuleDocument#applyRules}
 *       consumes — the future wiring point is datapack-load event → rule load → entity tag → rule
 *       apply; this sub-item substitutes a fixed td document + fixed zombie tag;</li>
 *   <li><b>Drive-update surface</b>: per entity tick, folds the loaded base {@link ScaleData} with
 *       the modifier queue (attack / potion / block-state multiplicative modifiers) in registration
 *       order via {@link ScaleModifier#applyAll}, and merges per-tag base scales via
 *       {@link ScaledEntityData#merge} — the future wiring point is entity tick → modifier
 *       incremental registration → dimension fold; this sub-item substitutes two fixed modifiers
 *       applied in registration order;</li>
 *   <li><b>Render-consume surface</b>: the client maps the final dimension values onto the MC
 *       entity render pipeline (vanilla {@code EntityRenderDispatcher} scale fields and the model
 *       matrix / collision box), consuming each dimension via {@link ScaleData#get} by
 *       {@link ScaleType} — the future wiring point is entity render transform → {@code scaleValues}
 *       injection; this sub-item consumes only the {@link ScaleData} data plane, without render
 *       injection.</li>
 * </ol>
 * Same gate {@code subterra.probe.scale} (default no-op): the server-side gate prints
 * {@code ok (types=.., data=.., rules=.., verify=ok)} (E2E-asserted). The wiring table also lives
 * in the repository runtime wiring docs; this sub-item delivers the gated marker and the
 * determinism proof of the engine.scale data-load / drive-update / render-consume data plane on a
 * real boot lifecycle.
 */
public final class ScaleRuntime {

    /** 探针 marker 前缀 / probe marker prefix. */
    public static final String MARKER = "[Subterra scale]";

    public static final Logger LOGGER = LogUtils.getLogger();

    /** 固定标称维度数（{@link ScaleType#values()} 长度）。The fixed nominal dimension count
     *  ({@code ScaleType.values().length}). */
    private static final int NOMINAL_TYPES = ScaleType.values().length;

    /** 固定标称装载后规则条数（重复 type+tag 首现胜出丢弃后）。The fixed nominal loaded rule count
     *  (after first-occurrence-wins duplicate dropping). */
    private static final int NOMINAL_RULES = 2;

    /** 固定标称最终缩放摘要值（{@link #verify} 用以逐字符核对）。The fixed nominal final-scale digest
     *  used to cross-check {@link #verify} character-for-character. */
    private static final String NOMINAL_DATA_DIGEST =
            "base=2.0,width=8.0,height=0.25,depth=1.0,eye_height=1.0,"
                    + "hitbox_width=1.0,hitbox_height=1.0,model_width=1.0,model_height=1.0";

    private ScaleRuntime() {
    }

    /** 注册 NeoForge 生命周期监听（mod 构造调用）。Registers the NeoForge lifecycle listeners (call
     * from the mod constructor). */
    public static void bootstrap() {
        NeoForge.EVENT_BUS.register(ScaleRuntime.class);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onServerStarted(ServerStartedEvent event) {
        // p.2.25.2 deterministic E2E hook: consume the engine.scale scaling data plane at startup
        // when a probe flag is forwarded (subterra.probe.scale) — mirrors the other probe-shell
        // gates, so no console command round-trips through the gradle-forked server JVM stdin.
        // Default no-op.
        String probe = System.getProperty("subterra.probe.scale");
        if (probe == null || probe.isBlank()) {
            return;
        }
        try {
            // Deterministic fixed-order consumption (no timing, no randomness); every digest is
            // re-verified against an identical from-scratch rebuild.
            DriveResult drive = runSample();
            int types = drive.types;
            String data = drive.data;
            int rules = drive.rules;
            if (types != NOMINAL_TYPES || !verify()) {
                throw new IllegalStateException("scale data not deterministic");
            }
            LOGGER.info("{} ok (types={}, data={}, rules={}, verify=ok)",
                    MARKER, types, data, rules);
        } catch (RuntimeException e) {
            // the samples are legal, so a mismatch is a program error — never emit a false ok.
            LOGGER.warn("{} scale mismatch (error={})", MARKER, e.getMessage());
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        // no held state -> pure no-op; symmetric empty hook kept (as the other shells).
    }

    /**
     * 固定样例确定性缩放驱动：构造固定 {@link ScaleData}（三类型值 BASE=2.0 / WIDTH=4.0 /
     * HEIGHT=0.5）→ 装载固定 {@code scale_rules} td 文档（width/height 两条命中规则 + 重复
     * {@code width+minecraft:zombie} 首现胜出丢弃）→ 对 {@code minecraft:zombie} 标签应用规则
     * （覆盖语义：WIDTH→8.0、HEIGHT→0.25，规则 {code rules} 数=2）→ 按注册序折叠两个固定
     * {@link ScaleModifier}（nether 0.5 → potions 2.0）。最终摘要 = 固定维度序
     * {@code type=value} 逗号连接；{@code types} = {@link ScaleType#values()} 长度，与摘要——逐字
     * 符一致——在 {@link #verify()} 中核对。
     * <p>
     * The fixed-sample deterministic scaling drive: builds a fixed {@link ScaleData} (three typed
     * values BASE=2.0 / WIDTH=4.0 / HEIGHT=0.5) → loads the fixed {@code scale_rules} td document
     * (two matching rules width/height + a duplicate {@code width+minecraft:zombie}
     * first-occurrence-wins drop) → applies the rules to the {@code minecraft:zombie} tag (override
     * semantics: WIDTH→8.0, HEIGHT→0.25, {@code rules} count=2) → folds two fixed
     * {@link ScaleModifier}s in registration order (nether 0.5 → potions 2.0). The digest = the
     * fixed-dimension-order {@code type=value} join; {@code types} = {@link ScaleType#values()}
     * length, cross-checked against the digest — and the character-for-character value — in
     * {@link #verify()}.
     */
    private static DriveResult runSample() {
        ScaleData base = ScaleData.of("minecraft:zombie")
                .with(ScaleType.BASE, 2.0)
                .with(ScaleType.WIDTH, 4.0)
                .with(ScaleType.HEIGHT, 0.5);
        ScaleRuleDocument rules = ScaleRuleDocument.fromTd(ruleDoc());
        int rulesCount = rules.entries().size();
        ScaleData afterRules = rules.applyRules(base, Set.of("minecraft:zombie"));
        List<ScaleModifier> modifiers = List.of(
                new ScaleModifier("subterra:nether", 0.5),
                new ScaleModifier("subterra:potions", 2.0));
        ScaleData finalData = ScaleModifier.applyAll(modifiers, afterRules);
        return new DriveResult(ScaleType.values().length, digest(finalData), rulesCount);
    }

    /**
     * 固定 {@code scale_rules} td 文档：version=1 + 三条规则（{\code width/zombie/8.0}、
     * {@code height/zombie/0.25}、重复 {@code width/zombie/5.0} 首现胜出丢弃）。
     * The fixed {@code scale_rules} td document: version=1 + three rules ({@code width/zombie/8.0},
     * {@code height/zombie/0.25}, duplicate {@code width/zombie/5.0} dropped first-occurrence-wins). */
    private static TdTable ruleDoc() {
        return TdTable.builder()
                .put("version", TdValue.of(1L))
                .put("entries", TdTable.builder()
                        .element(ruleEntry("width", "minecraft:zombie", 8.0))
                        .element(ruleEntry("height", "minecraft:zombie", 0.25))
                        .element(ruleEntry("width", "minecraft:zombie", 5.0))
                        .build())
                .build();
    }

    /** 单条固定缩放规则（固定字段序：type / tag / scale）。A single fixed scale rule (fixed field
     *  order: type / tag / scale). */
    private static TdTable ruleEntry(String type, String tag, double scale) {
        return TdTable.builder()
                .put("type", TdValue.str(type))
                .put("tag", TdValue.str(tag))
                .put("scale", TdValue.of(scale))
                .build();
    }

    /**
     * 确定性摘要复验：从零重建同驱动，核对维度数={@link #NOMINAL_TYPES}、规则数={@link #NOMINAL_RULES}，
     * 且最终缩放摘要与标称值逐字符一致（确定性证明）。{@link ScaleType#values()} 固定序保证同驱动同摘要。
     * <p>
     * Determinism re-verification: rebuilds the same drive from scratch and checks the dimension
     * count = {@link #NOMINAL_TYPES}, the rule count = {@link #NOMINAL_RULES}, and that the final
     * scale digest matches the nominal value character-for-character (the determinism proof).
     * {@link ScaleType#values()}'s fixed order guarantees the same drive yields the same digest.
     */
    private static boolean verify() {
        DriveResult drive = runSample();
        return drive.types == NOMINAL_TYPES && drive.rules == NOMINAL_RULES
                && drive.data.equals(NOMINAL_DATA_DIGEST);
    }

    /** 缩放摘要：固定 {@link ScaleType#values()} 维度序 {@code type=value} 逗号连接。 Scale digest:
     *  the fixed {@link ScaleType#values()} dimension-order {@code type=value} join. */
    private static String digest(ScaleData data) {
        StringBuilder sb = new StringBuilder();
        for (ScaleType t : ScaleType.values()) {
            if (sb.length() > 0) {
                sb.append(',');
            }
            sb.append(t.form()).append('=').append(data.get(t));
        }
        return sb.toString();
    }

    /** 样例驱动结果（维度数 / 最终缩放摘要 / 规则数）。The sample-drive result (dimension count /
     *  final-scale digest / rule count). */
    private record DriveResult(int types, String data, int rules) {
    }
}