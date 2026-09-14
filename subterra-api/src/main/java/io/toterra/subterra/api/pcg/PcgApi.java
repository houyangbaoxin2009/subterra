package io.toterra.subterra.api.pcg;

import java.util.ArrayList;
import java.util.List;

/**
 * p.2.33.8 对外 PCG 域契约门面（final class、静态纯函数、纯 JDK）：确定性接口面，供上层/域模组按
 * 「种子扩散/派生 + fork 隔离 + 同种子同序列 pick + 文本采样模板-槽固定序」使用 engine.pcg 域的确定性
 * 语义。语义与 {@code engine.pcg} 的 {@code Pcg}/{@code PcgSource}/{@code PcgPick}/{@code PcgTextSample}/
 * {@code PcgException}（p.2.13.1/.2）一致——本处为契约与数据面注入，engine 为实现镜像
 * （{@code engine.pcg.PcgApiMirror}），api 不依赖 engine。所有常量/语义均从 engine 实际行为逐字对照落于此。
 * <p>确定性：{@link #contractStatement()} 返回固定契约陈述；{@link #reasons()} 返回固定序异常原因分类；
 * {@link #slotForkSalt} 复现 {@code PcgTextSample} 按槽 fork 的 {@code "slot:"+槽名} 派生盐方案（slot 间相互
 * 隔离、父源不受影响）；{@link #slotsInOrder} 复现模板左到右首次出现序的槽序遍历（同
 * {@code PcgTextSample.generate}）；{@link #uniformIndex} / {@link #weightedIndex} 以「draw 输入」复现
 * {@code PcgPick.pick}/{@code pickWeighted} 的下标解析语义（位置即选择、前缀区间二分），把「同种子同序列」
 * 的随机抽取折叠成确定性的下标映射。全部为纯函数：无随机、无墙钟、无迭代序依赖；无 O(n²)（加权为单趟前缀和
 * + 二分）。无状态、无副作用。<b>诚实界限</b>：Xoroshiro128++ 确定性 PRNG 流由 engine 的
 * {@code XoroRandom} 持有（api 不复制 PRNG）；本契约面锁定的是一份 {@code long} draw 到「池位置 / 权重前缀
 * 区间」的确定性语义与 fork 派生盐方案，engine 镜像逐字导出同一份方案。
 * <p>
 * p.2.33.8 the external PCG-domain contract facade (final class, static pure functions, pure JDK): a
 * deterministic interface surface for upper layers / domain mods to use the deterministic semantics of
 * {@code engine.pcg}'s seed derivation / fork isolation / same-seed-same-sequence pick / template-slot order.
 * Semantics match {@code Pcg}/{@code PcgSource}/{@code PcgPick}/{@code PcgTextSample}/{@code PcgException}
 * (p.2.13.1/.2) — this is the contract and data-injection surface for the engine to mirror as its implementation
 * ({@code engine.pcg.PcgApiMirror}), and the api does not depend on the engine. Every constant/semantic here is
 * pinned verbatim from the engine's actual behaviour.
 * <p>Deterministic: {@link #contractStatement()} returns the fixed contract statement; {@link #reasons()} returns
 * the fixed-order exception-reason classification; {@link #slotForkSalt} reproduces {@code PcgTextSample}'s
 * per-slot fork salt scheme {@code "slot:"+slotName} (slots mutually isolated, parent unaffected);
 * {@link #slotsInOrder} reproduces the template's left-to-right first-occurrence slot traversal (as in
 * {@code PcgTextSample.generate}); {@link #uniformIndex} / {@link #weightedIndex} reproduce the index-resolution
 * semantics of {@code PcgPick.pick} / {@code pickWeighted} on a provided {@code draw} (position decides the pick;
 * prefix-interval binary search), folding the "same-seed-same-sequence" random draw into a deterministic index map.
 * All pure functions: no randomness, no wall-clock, no iteration-order dependence; no O(n²) (weighted is a
 * single-pass prefix sum + binary search). Stateless, side-effect free. <b>Honest boundary</b>: the Xoroshiro128++
 * deterministic PRNG stream is owned by {@code XoroRandom} in the engine (the api does not duplicate the PRNG);
 * this contract surface pins the deterministic semantics from one {@code long} draw to a "pool position /
 * weight-prefix interval" plus the per-slot fork-salt scheme, and the engine mirror exports the same scheme.
 */
public final class PcgApi {

    private PcgApi() {
    }

    /** Core determinism contract statement, mirroring {@code engine.pcg.Pcg#CONTRACT_SAME_SEED_SAME_OUTPUT}.
     *  核心确定性契约陈述，镜像 {@code engine.pcg.Pcg#CONTRACT_SAME_SEED_SAME_OUTPUT}。 */
    public static String contractStatement() {
        return "same (seed, call sequence) -> same (choice sequence)";
    }

    /** The fixed-order exception-reason classification, mirroring
     *  {@code engine.pcg.PcgException} reason constants. /
     *  固定序异常原因分类，镜像 {@code engine.pcg.PcgException} 的原因常量。 */
    public static List<String> reasons() {
        return List.of("EMPTY_POOL", "NEGATIVE_WEIGHT", "BOUND", "UNKNOWN_SLOT", "SCHEMA_ROOT");
    }

    /**
     * The per-slot fork salt scheme used by {@code PcgTextSample} to isolate each slot's derivation source
     * ({@code "slot:" + slotName}): derived sources are mutually independent and the parent is unaffected.
     * Pure and deterministic.
     * / {@code PcgTextSample} 用于隔离每个槽派生源的按槽 fork 盐方案（{@code "slot:" + 槽名}）：派生源相互独立、
     * 父源不受影响。纯函数且确定性。
     *
     * @param slotName the slot key.
     * @return the fork salt {@code "slot:" + slotName}.
     */
    public static String slotForkSalt(String slotName) {
        return "slot:" + slotName;
    }

    /**
     * The template's slot order: the slot keys of every {@code {slot}} placeholder in left-to-right occurrence
     * order (a repeated slot appears once per occurrence, exactly as {@code PcgTextSample.generate} replaces each
     * placeholder position). A template without placeholders yields an empty list. Deterministic.
     * / 模板的槽序遍历：按左到右出现序收集每个 {@code {slot}} 占位的槽键（重复槽按其每次出现列出，与
     * {@code PcgTextSample.generate} 逐个替换占位格的行为一致）。无占位符的模板返回空表。确定性。
     *
     * @param template the template with {@code {slot}} placeholders.
     * @return the slot keys in left-to-right occurrence order.
     */
    public static List<String> slotsInOrder(String template) {
        if (template == null) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        int from = 0;
        while (from < template.length()) {
            int open = template.indexOf('{', from);
            if (open < 0) {
                break;
            }
            int close = template.indexOf('}', open + 1);
            if (close < 0) {
                break;
            }
            out.add(template.substring(open + 1, close));
            from = close + 1;
        }
        return List.copyOf(out);
    }

    /**
     * Resolves a uniform draw into a pool index, mirroring {@code PcgPick#pick}'s positional semantics
     * ({@code index = source.nextInt(pool.size())} with {@code draw} already in {@code [0, poolSize)}). Deterministic.
     * / 将均匀 draw 解析为池下标，镜像 {@code PcgPick#pick} 的位置语义（{@code index = source.nextInt(pool.size())}，
     * {@code draw} 已在 {@code [0, poolSize)}）。确定性。
     *
     * @param poolSize the pool size ({@code > 0}).
     * @param draw     the deterministic draw in {@code [0, poolSize)}.
     * @return the pool index {@code draw}.
     * @throws IllegalArgumentException if {@code poolSize < 1}, or {@code draw} is outside {@code [0, poolSize)}.
     */
    public static int uniformIndex(int poolSize, int draw) {
        if (poolSize < 1) {
            throw new IllegalArgumentException("pool is empty");
        }
        if (draw < 0 || draw >= poolSize) {
            throw new IllegalArgumentException("draw " + draw + " outside [0, " + poolSize + ")");
        }
        return draw;
    }

    /**
     * Resolves a weighted draw into the pool index whose cumulative-weight prefix interval contains it, mirroring
     * {@code PcgPick#pickWeighted}'s index resolution (single-pass prefix sum + binary search). Deterministic.
     * / 将加权 draw 解析为其所在累计权重前缀区间对应的池下标，镜像 {@code PcgPick#pickWeighted} 的下标解析
     * （单趟前缀和 + 二分）。确定性。
     *
     * @param weights the parallel non-negative weights ({@code > 0} each, non-empty).
     * @param draw    the deterministic draw in {@code [0, totalWeight)}.
     * @return the index of the prefix interval containing {@code draw}.
     * @throws IllegalArgumentException if {@code weights} is empty, any weight is {@code <= 0}, or {@code draw} is
     *     outside {@code [0, totalWeight)}.
     */
    public static int weightedIndex(List<Long> weights, long draw) {
        if (weights == null || weights.isEmpty()) {
            throw new IllegalArgumentException("items is empty");
        }
        long total = 0L;
        int n = weights.size();
        long[] prefix = new long[n];
        for (int i = 0; i < n; i++) {
            long w = weights.get(i);
            if (w <= 0L) {
                throw new IllegalArgumentException("weight[" + i + "] = " + w);
            }
            total += w;
            prefix[i] = total;
        }
        if (draw < 0 || draw >= total) {
            throw new IllegalArgumentException("draw " + draw + " outside [0, " + total + ")");
        }
        int lo = 0;
        int hi = n - 1;
        while (lo < hi) {
            int mid = (lo + hi) >>> 1;
            if (prefix[mid] <= draw) {
                lo = mid + 1;
            } else {
                hi = mid;
            }
        }
        return lo;
    }
}