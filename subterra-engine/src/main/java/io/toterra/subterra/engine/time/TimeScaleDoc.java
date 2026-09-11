package io.toterra.subterra.engine.time;

import io.toterra.subterra.engine.config.TdTable;
import io.toterra.subterra.engine.config.TdValue;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * td-ized time-scale configuration (p.2.26.1, clean-room self-developed): a two-tier
 * document that mirrors the two-layer resolve semantics of {@link TimeScale} and carries
 * them into the framework's {@code td} data language. The document shape is
 * <pre>
 *   time_scale = [ global = 1.0, entity = 0.5, zone = 2.0, player = 1.0 ]
 * </pre>
 * where {@code global} is the mandatory baseline tier and {@code entity} / {@code zone} /
 * {@code player} are optional per-domain override tiers. Resolving an absent override to
 * the global value is deliberately deferred to runtime (see {@link TimeScale#effective(TimeDomain)});
 * the document itself only ever records explicitly set tiers, so the unset state survives
 * round-tripping.
 * <p>
 * Deterministic: {@link #fromTd(TdTable)} reads {@code global} (defaulting to {@code 1.0})
 * and each present override; {@link #toTd(TimeScale)} writes {@code global} then the
 * explicitly set overrides in fixed {@link TimeDomain} enumeration order, skipping the
 * unset tiers, with no timestamps. A document without an absent state difference
 * round-trips to a stable, byte-identical normalized form: parsing a written document and
 * re-writing yields the identical td text on every run.
 *
 * <p>td 化时间缩放配置（p.2.26.1，clean-room 自研）：一种双层文档，镜像 {@link TimeScale} 的双层解析
 * 语义并将其带入框架的 {@code td} 数据语言。文档形态为
 * <pre>
 *   time_scale = [ global = 1.0, entity = 0.5, zone = 2.0, player = 1.0 ]
 * </pre>
 * 其中 {@code global} 为强制基线档，{@code entity} / {@code zone} / {@code player} 为可选按域覆盖档。
 * 把缺失覆盖档解析回全局值的动作刻意留到 runtime（见
 * {@link TimeScale#effective(TimeDomain)}）；文档本身只记录被显式设置的档位，故「未设置」状态在往返中
 * 得以保留。
 * <p>确定性：{@link #fromTd(TdTable)} 读取 {@code global}（缺省 {@code 1.0}）与每个在场的覆盖档；
 * {@link #toTd(TimeScale)} 先写 {@code global}，再按固定 {@link TimeDomain} 枚举序写被显式设置的覆盖
 * 档、跳过未设置档，无时间戳。不含「未设置状态差异」的文档可往返为稳定、逐字节一致的规范化形态：解析已
 * 写出的文档再写回，每次运行都得到相同的 td 文本。
 */
public final class TimeScaleDoc {

    private TimeScaleDoc() {
    }

    /**
     * Parses a {@code time_scale} table into a {@link TimeScale}. Accepts any key among
     * {@code global}, {@code entity}, {@code zone}, {@code player}; {@code global} is
     * required ({@code 1.0} when absent) and the domains become explicitly set; an absent
     * override is left unset (falling back to global at runtime). Unknown keys are ignored
     * deterministically. Each value must be finite and {@code > 0}.
     * / 将 {@code time_scale} 表解析为 {@link TimeScale}。接受 {@code global}、{@code entity}、
     * {@code zone}、{@code player} 中的任意键；{@code global} 必填（缺省 {@code 1.0}）且该域记为显式
     * 设置；缺失的覆盖档保持未设置（运行时回退全局）。未知键被确定性忽略。每项取值必须有限且 {@code > 0}。
     *
     * @param root the {@code time_scale} table (non-null).
     * @return a new {@link TimeScale} with the parsed tiers.
     * @throws NullPointerException     if {@code root} is null.
     * @throws IllegalArgumentException if any present value is not finite and {@code > 0}.
     */
    public static TimeScale fromTd(TdTable root) {
        Objects.requireNonNull(root, "root must not be null");
        Map<TimeDomain, Double> explicit = new LinkedHashMap<>();
        for (TimeDomain d : TimeDomain.values()) {
            TdValue v = root.get(d.form());
            if (v != null) {
                double rate = v.asFloat();
                if (!Double.isFinite(rate) || rate <= 0.0D) {
                    throw new IllegalArgumentException(d.form() + " flow rate must be finite and > 0: " + rate);
                }
                explicit.put(d, rate);
            }
        }
        if (!explicit.containsKey(TimeDomain.GLOBAL)) {
            explicit.put(TimeDomain.GLOBAL, 1.0D);
        }
        return TimeScale.of(explicit);
    }

    /**
     * Writes a {@link TimeScale} back to a {@link TdTable}: {@code global} first (its
     * effective value, {@code 1.0} when unset), then the explicitly set override tiers in
     * fixed {@link TimeDomain} enumeration order ({@code entity, zone, player}), skipping
     * unset tiers — exactly preserving the unset state so {@link #fromTd(TdTable)} is a
     * stable, byte-identical round-trip. Deterministic and timestamp-free.
     * / 将 {@link TimeScale} 写回 {@link TdTable}：先 {@code global}（其有效值，未设置时 {@code 1.0}），
     * 再按固定 {@link TimeDomain} 枚举序（{@code entity, zone, player}）写被显式设置的覆盖档，跳过未设置
     * 档——精确保留「未设置」状态，使 {@link #fromTd(TdTable)} 成为稳定、逐字节一致的往返。确定性、无
     * 时间戳。
     *
     * @param scale the {@link TimeScale} to serialize (non-null).
     * @return a {@link TdTable} representing the two-tier document.
     * @throws NullPointerException if {@code scale} is null.
     */
    public static TdTable toTd(TimeScale scale) {
        Objects.requireNonNull(scale, "scale must not be null");
        TdTable.Builder b = TdTable.builder();
        b.put("global", TdValue.of(scale.effective(TimeDomain.GLOBAL)));
        for (TimeDomain d : TimeDomain.values()) {
            if (d == TimeDomain.GLOBAL) {
                continue;
            }
            if (scale.isSet(d)) {
                b.put(d.form(), TdValue.of(scale.get(d)));
            }
        }
        return b.build();
    }
}