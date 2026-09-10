package io.toterra.sample.overturn;

import io.toterra.subterra.api.config.Rule;
import io.toterra.subterra.api.config.RuleSet;
import io.toterra.subterra.engine.config.rules.RuleKey;
import io.toterra.subterra.engine.config.rules.RuleSpec;
import io.toterra.subterra.engine.config.rules.RuleStore;
import io.toterra.subterra.engine.config.rules.RuleType;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * p.2.20.3 官方示例模组最小入口（骨架占位，纯 JDK——不触 MC 类路径）。
 * <p>
 * 示例意图：最小「颠覆性模组」消费方——用框架纯 JDK 能力（api 契约 {@code api.config.RuleSet}
 * + 引擎核心 {@code engine.config.rules.RuleStore}）把「颠覆原版一小面」写成一段 td 规则逻辑。
 * 骨架只立结构 + 清单式最小形态：声明两条类型化规则规格、从类路径装载 td 规则包
 * {@code /data/overturn_minimal/pack.td}、双层解析 + 校验 + 冻结规则集 + 规范渲染，全部确定性
 * （固定注册序、禁时序、禁随机、同输入同输出）。颠覆语义（把规则接入实际世界生成路径，如密度
 * 偏移）随 p.2.20.4 兑现。
 * <p>
 * p.2.20.3 official sample minimal entry (skeleton placeholder, pure JDK — never touches the MC
 * classpath).
 * <p>
 * Intent: the minimal "overturn" mod consumer — a piece of "overturn" logic written as td rules
 * against the framework's pure-JDK surface (api contract {@code api.config.RuleSet} + engine core
 * {@code engine.config.rules.RuleStore}). The skeleton only establishes the structure and a
 * minimal checklist shape: declare two typed rule specs, load the td rules pack from the
 * classpath resource {@code /data/overturn_minimal/pack.td}, then two-tier resolve + validate +
 * freeze a rule set + canonical render — all deterministic (fixed registration order, no timing,
 * no randomness, same input → same output). The overturn semantics (wiring the rules into the
 * real worldgen path, e.g. the density offset) land with p.2.20.4.
 */
public final class OverturnMinimal {

    /** 规则文档类路径资源。The rule document classpath resource. */
    public static final String RULES_RESOURCE = "/data/overturn_minimal/pack.td";

    private OverturnMinimal() {
    }

    /**
     * 确定性自检入口：规格注册 → 装载规则包 → 解析/校验 → 冻结规则集 → 打印规范渲染。
     * 同一输入永远产出同一输出。
     * <p>
     * Deterministic self-check entry: register specs → load the rules pack → resolve/validate →
     * freeze the rule set → print the canonical render. The same input always yields the same
     * output.
     *
     * @param args 未使用（确定性：无任何输入依赖）/ unused (deterministic: no input dependency).
     */
    public static void main(String[] args) throws IOException {
        // 1) 声明两条类型化规则规格（固定注册序）——颠覆目标的两根旋钮。
        //    Declare two typed rule specs (fixed registration order) — the two knobs of the
        //    overturn target.
        List<RuleSpec> specs = List.of(
                new RuleSpec(new RuleKey("overturn.worldgen.density_offset"), RuleType.FLOAT, "0.0",
                        "global terrain density shift in blocks (semantics land with p.2.20.4)"),
                new RuleSpec(new RuleKey("overturn.worldgen.structures"), RuleType.BOOLEAN, "true",
                        "whether vanilla structures keep generating"));
        RuleStore store = new RuleStore(specs);

        // 2) 装载类路径中的 td 规则包（清单式最小形态；语义面随 p.2.20.4 兑现）。
        //    Load the td rules pack from the classpath resource (minimal checklist shape;
        //    the semantics land with p.2.20.4).
        try (InputStream in = OverturnMinimal.class.getResourceAsStream(RULES_RESOURCE)) {
            if (in == null) {
                throw new IOException("missing classpath resource: " + RULES_RESOURCE);
            }
            store.loadGlobalText(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        }

        // 3) 确定性自检：双层解析 + 校验 + 冻结规则集 + 规范渲染。
        //    Deterministic self-check: two-tier resolve + validate + frozen rule set + canonical
        //    render.
        Map<String, String> effective = store.resolve();
        RuleSet frozen = RuleSet.of(effective.entrySet().stream()
                .map(e -> new Rule(e.getKey(), e.getValue()))
                .toList());
        System.out.println("overturn_minimal ok: " + frozen.rules().size() + " rule(s), report="
                + store.validate().ok() + ", render=" + store.render());
    }
}
