package io.toterra.subterra.engine.pcg;

import java.util.ArrayList;
import java.util.List;

/**
 * p.2.33.8 引擎侧镜像实现（api 定义形状 / engine 镜像实现范式，对应 {@code api.pcg.PcgApi}）：以
 * {@code engine.pcg} 的真实实现（{@link Pcg} / {@link PcgException} / {@link PcgPick} / {@code PcgTextSample}
 * 的逐槽 fork 盐方案与模板槽遍历）为<b>唯一来源</b>，暴露与 {@code api.pcg.PcgApi} 同语义的只读契约面——
 * 契约陈述、异常原因固定序、槽 fork 盐方案、模板槽序、以及 uniform/weighted 下标解析均同输入同输出
 * （供 p.2.33.8 探针对照断言）。本镜像<em>不 import</em> api 包，仅消费 engine 自身类型并把 api 契约值
 * 逐字导出，从而在两侧分别实例化后完全一致。
 * <p>确定性：全部方法为纯函数；{@link #weightedIndex} 复用 {@code PcgPick#pickWeighted} 相同的单趟前缀和 +
 * 二分下标解析；{@link #slotsInOrder} 复用 {@code PcgTextSample.generate} 相同的左到右首次出现槽遍历。
 * 无随机、无时序。
 * <p>
 * p.2.33.8 engine-side mirror implementation (api-shapes / engine-mirrors pattern, counterpart of
 * {@code api.pcg.PcgApi}): using the <b>actual</b> {@code engine.pcg} implementations ({@link Pcg} /
 * {@link PcgException} / {@link PcgPick} / {@code PcgTextSample}'s per-slot fork salt scheme and template slot
 * traversal) as the single source of truth, it exposes a read-only surface with the same semantics as
 * {@code api.pcg.PcgApi} — the contract statement, fixed-order exception reasons, slot fork salt scheme, template
 * slot order and the uniform/weighted index resolution are all same-input-same-output (the p.2.33.8 probe asserts
 * both sides). This mirror does <em>not</em> import the api package; it consumes only engine types and exports the
 * api contract values verbatim, so instantiating both sides yields identical results.
 * <p>Deterministic: every method is a pure function; {@link #weightedIndex} reuses the same single-pass prefix-sum
 * + binary-search index resolution as {@code PcgPick#pickWeighted}; {@link #slotsInOrder} reuses the same
 * left-to-right first-occurrence slot traversal as {@code PcgTextSample.generate}. No randomness, no timing.
 */
public final class PcgApiMirror {

    private PcgApiMirror() {
    }

    /** The core determinism contract statement, sourced verbatim from {@link Pcg#CONTRACT_SAME_SEED_SAME_OUTPUT}. */
    public static String contractStatement() {
        return Pcg.CONTRACT_SAME_SEED_SAME_OUTPUT;
    }

    /** The fixed-order exception-reason classification, sourced verbatim from {@link PcgException} constants. */
    public static List<String> reasons() {
        return List.of(PcgException.EMPTY_POOL, PcgException.NEGATIVE_WEIGHT, PcgException.BOUND,
                PcgException.UNKNOWN_SLOT, PcgException.SCHEMA_ROOT);
    }

    /** The {@code "slot:" + slotName} fork salt scheme used by {@code PcgTextSample.generate}. */
    public static String slotForkSalt(String slotName) {
        return "slot:" + slotName;
    }

    /** The template's left-to-right first-occurrence slot order, as traversed by {@code PcgTextSample.generate}. */
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

    /** The positional uniform index resolution ({@code index = draw}), mirroring the semantic of
     *  {@code PcgPick#pick} on a provided draw. */
    public static int uniformIndex(int poolSize, int draw) {
        if (poolSize < 1) {
            throw new IllegalArgumentException("pool is empty");
        }
        if (draw < 0 || draw >= poolSize) {
            throw new IllegalArgumentException("draw " + draw + " outside [0, " + poolSize + ")");
        }
        return draw;
    }

    /** The weighted index resolution (single-pass prefix sum + binary search), mirroring
     *  {@code PcgPick#pickWeighted}'s index semantics on a provided draw. */
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