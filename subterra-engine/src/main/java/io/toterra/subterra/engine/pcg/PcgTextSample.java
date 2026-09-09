package io.toterra.subterra.engine.pcg;

import java.util.List;
import java.util.Map;

/**
 * p.2.13.2 名称/文本生成器（final 工具类，静态入口）——由「模板 + 各槽候选池 + 种子」确定性生成一段文本
 * （名称/描述均可）。模板使用固定占位符契约：{@code {slot}} 形式（如 {@code "The {adj} {noun} of {place}"}）；
 * 候选池 {@code pools} 的 key → 槽名，value → 该槽的候选词表（按 {@link List} 固定序参与选取）。
 * <p>
 * 确定性契约（也是确定性判据）：
 * <ul>
 *   <li>同 {@code (seedSalt, seed, template, pools)} 两次独立调用 → 两次输出逐字符一致
 *       （同 seed → 同 text）；</li>
 *   <li>内部统一由 {@code new PcgSource(seed).fork(seedSalt)} 派生基线源，每个槽在基线源上独立再
 *       fork {@code "slot:"+槽名} 得槽源，再经 {@link PcgPick#pick} 从对应池选词——槽间相互隔离、
 *       父源不受影响，选取只由种子与槽序决定；</li>
 *   <li>槽序遍历固定：按模板左到右首次出现序；对每个占位在模板中出现的位置按同一顺序替换；</li>
 *   <li>候选池仅按 key 查找（{@code pools.get(slot)}），从不遍历 {@code pools}——Map 的迭代序不参与、
 *       不影响输出，输出与 {@code pools} 的具体映射实现无关，只与每个 key 的 {@link List} 固定序有关。</li>
 * </ul>
 * 纯 JDK、无 O(n&#xb2;)（单趟扫描模板，每槽一次 fork+一次 pick）、无全局状态、无时间戳/随机/时序。
 * <p>
 * 输入契约：模板无占位符（不含花括号）→ 原样返回；模板含占位但对应槽在 {@code pools} 中缺失
 * → 受检 {@link PcgException#UNKNOWN_SLOT}；对应池为空 → 既有 {@link PcgException#EMPTY_POOL}。
 * <p>
 * p.2.13.2 name / text generator (final utility class, static entry) — deterministically renders a
 * piece of text (a name or a description) from a template, per-slot candidate pools and a seed.
 * The template uses a fixed placeholder contract: the {@code {slot}} form (e.g.
 * {@code "The {adj} {noun} of {place}"}); {@code pools} maps a slot key to its candidate word list
 * (taken in the {@link List}'s fixed order for the pick).
 * <p>
 * Determinism contract (also the determinism criteria):
 * <ul>
 *   <li>two independent calls on the same {@code (seedSalt, seed, template, pools)} -> char-identical
 *       output (same seed -> same text);</li>
 *   <li>internally a baseline source is always derived via {@code new PcgSource(seed).fork(seedSalt)},
 *       and each slot forks an independent source on the baseline with {@code "slot:"+slotName}, then
 *       {@link PcgPick#pick} draws a word from the pool — slots are mutually isolated, the parent is
 *       unaffected, and each pick is decided only by the seed and the fixed slot order;</li>
 *   <li>slot traversal is fixed: the template's left-to-right first-occurrence order, replaced at each
 *       placeholder position in the same order;</li>
 *   <li>pools are only looked up by key ({@code pools.get(slot)}) and never iterated — the Map's
 *       iteration order takes no part in and cannot affect the output; the output depends only on each
 *       key's {@link List} fixed order, never on the concrete Map implementation.</li>
 * </ul>
 * Pure JDK, no O(n^2) (one single-pass sweep over the template, one fork + one pick per slot), no global
 * state, no timestamp / random / timing.
 * <p>
 * Input contract: a template without placeholders (no braces) -> returned as-is; a template placeholder
 * whose slot key is missing from {@code pools} -> checked {@link PcgException#UNKNOWN_SLOT}; a matching
 * pool that is empty -> the existing {@link PcgException#EMPTY_POOL}.
 */
public final class PcgTextSample {

    private PcgTextSample() {
        // utility class; no instantiation / 工具类，禁止实例化
    }

    /**
     * 由「模板 + 各槽候选池 + 种子」确定性生成一段文本。见类级 Javadoc 的模板/槽契约与确定性判据。
     * <p>
     * Deterministically renders a text from a template + per-slot candidate pools + a seed. See the
     * class-level Javadoc for the template / slot contract and the determinism criteria.
     *
     * @param seedSalt 派生盐，参与基线源派生（不同盐 -> 不同文本）/ derivation salt, folded into the
     *                 baseline-source derivation (distinct salt -> different text).
     * @param seed     主种子，所有取值与全部槽源的确定性基底 / the master seed, the deterministic
     *                 substrate of every draw and every slot source.
     * @param template 固定占位符形态的模板（{@code {slot}}），如 {@code "The {adj} {noun} of {place}"}
     *                 / the template with fixed placeholders ({@code {slot}}), e.g.
     *                 {@code "The {adj} {noun} of {place}"}.
     * @param pools    槽名 -> 候选词表（按 {@link List} 固定序选取）/ slot key -> candidate word list
     *                 (taken in the {@link List}'s fixed order).
     * @return 确定性生成的文本 / the deterministically generated text.
     * @throws PcgException 未知槽 {@link PcgException#UNKNOWN_SLOT} 或空池 {@link PcgException#EMPTY_POOL}
     *                      / on an unknown slot ({@link PcgException#UNKNOWN_SLOT}) or an empty pool
     *                      ({@link PcgException#EMPTY_POOL}).
     */
    public static String generate(String seedSalt, long seed, String template, Map<String, List<String>> pools)
            throws PcgException {
        if (template == null || (template.indexOf('{') < 0 && template.indexOf('}') < 0)) {
            return template;
        }
        if (pools == null) {
            pools = java.util.Collections.emptyMap();
        }
        PcgSource base = new PcgSource(seed).fork(seedSalt);
        StringBuilder out = new StringBuilder(template.length());
        int from = 0;
        while (from < template.length()) {
            int open = template.indexOf('{', from);
            if (open < 0) {
                out.append(template, from, template.length());
                break;
            }
            int close = template.indexOf('}', open + 1);
            if (close < 0) {
                // Unclosed brace past this position: copy the remainder literally.
                out.append(template, from, template.length());
                break;
            }
            out.append(template, from, open);
            String slot = template.substring(open + 1, close);
            List<String> pool = pools.get(slot);
            if (pool == null) {
                throw new PcgException(PcgException.UNKNOWN_SLOT, "no pool supplied for slot {" + slot + "}");
            }
            PcgSource slotSrc = base.fork("slot:" + slot);
            out.append(PcgPick.pick(slotSrc, pool));
            from = close + 1;
        }
        return out.toString();
    }
}