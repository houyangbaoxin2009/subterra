package io.toterra.subterra.api.worldgen;

import java.util.Locale;

/**
 * p.2.36.2 变量维级（variable-dimension level）：单个九维的<b>参数向量</b>（纯 JDK 不可变值）。
 * 与 {@link DimCapability}（能力位：该维由谁生成）互补——本 record 描述该维<b>怎么生成</b>：
 * <ul>
 *   <li>{@code weight}——该维在下游消费中的权重（{@code >= 0}，缺省 {@value #DEFAULT_WEIGHT}）。</li>
 *   <li>{@code wavelength}——该维场的空间波长（{@code > 0}，缺省 {@value #DEFAULT_WAVELENGTH}）。</li>
 *   <li>{@code amplitude}——该维场的幅度（{@code >= 0}，缺省 {@value #DEFAULT_AMPLITUDE}）。</li>
 *   <li>{@code band}——该维被消费的取值带位（{@code [0,1]}，缺省 {@value #DEFAULT_BAND}）；每维输出
 *       一个 {@code 0..1} 标量场（设计案 §3.2），带位决定该场落在带内还是带外。</li>
 * </ul>
 * 缺省四值是<b>中性起点</b>（不钉任何具体地貌），全部可覆盖；非法值（非有限、负权重、零/负波长、
 * 越界带位）确定性拒绝（{@link IllegalArgumentException}）。
 * <p>
 * p.2.36.2 the variable-dimension level: the <b>parameter vector</b> of one dimension (pure-JDK
 * immutable). It complements {@link DimCapability} (who generates the dimension) by describing how it is
 * generated: downstream {@code weight}, spatial {@code wavelength}, {@code amplitude} and the consumed
 * {@code band} ({@code [0,1]}; every dimension emits a {@code 0..1} scalar field per design §3.2). The
 * four defaults are a neutral starting point (they pin no particular landscape) and every value is
 * overridable; non-finite values, negative weights, non-positive wavelengths and out-of-range bands are
 * rejected deterministically.
 *
 * @param weight     the downstream weight ({@code >= 0}, finite).
 * @param wavelength the spatial wavelength ({@code > 0}, finite).
 * @param amplitude  the field amplitude ({@code >= 0}, finite).
 * @param band       the consumed band position ({@code [0,1]}).
 */
public record DimParams(double weight, double wavelength, double amplitude, double band) {

    /** The default weight ({@code 1.0}). / 缺省权重（{@code 1.0}）。 */
    public static final double DEFAULT_WEIGHT = 1.0D;

    /** The default wavelength ({@code 256.0}). / 缺省波长（{@code 256.0}）。 */
    public static final double DEFAULT_WAVELENGTH = 256.0D;

    /** The default amplitude ({@code 1.0}). / 缺省幅度（{@code 1.0}）。 */
    public static final double DEFAULT_AMPLITUDE = 1.0D;

    /** The default band position ({@code 0.5}). / 缺省带位（{@code 0.5}）。 */
    public static final double DEFAULT_BAND = 0.5D;

    public DimParams {
        if (!Double.isFinite(weight) || weight < 0.0D) {
            throw new IllegalArgumentException("weight must be finite and >= 0: " + weight);
        }
        if (!Double.isFinite(wavelength) || wavelength <= 0.0D) {
            throw new IllegalArgumentException("wavelength must be finite and > 0: " + wavelength);
        }
        if (!Double.isFinite(amplitude) || amplitude < 0.0D) {
            throw new IllegalArgumentException("amplitude must be finite and >= 0: " + amplitude);
        }
        if (!Double.isFinite(band) || band < 0.0D || band > 1.0D) {
            throw new IllegalArgumentException("band must be finite within [0,1]: " + band);
        }
    }

    /** The neutral default vector. / 中性缺省向量。 */
    public static DimParams defaults() {
        return new DimParams(DEFAULT_WEIGHT, DEFAULT_WAVELENGTH, DEFAULT_AMPLITUDE, DEFAULT_BAND);
    }

    /**
     * Renders the vector as its td value ({@code weight:wavelength:amplitude:band}, canonical
     * {@link Double#toString} per slot). / 渲染为 td 值（{@code weight:wavelength:amplitude:band}，
     * 每槽用 {@link Double#toString}）。
     */
    public String tdValue() {
        return Double.toString(weight) + ":" + Double.toString(wavelength) + ":"
                + Double.toString(amplitude) + ":" + Double.toString(band);
    }

    /**
     * Parses a {@code weight:wavelength:amplitude:band} value; a wrong slot count or any illegal
     * value is rejected deterministically. / 解析 {@code weight:wavelength:amplitude:band} 值；
     * 槽数不符或任一值非法确定性拒绝。
     *
     * @param value the td value (non-null).
     * @throws NullPointerException     if {@code value} is null.
     * @throws IllegalArgumentException on a wrong slot count or an out-of-contract value.
     */
    public static DimParams parse(String value) {
        if (value == null) {
            throw new NullPointerException("dim params value must not be null");
        }
        String[] slots = value.trim().split(":");
        if (slots.length != 4) {
            throw new IllegalArgumentException("dim params must carry 4 slots 'w:wl:amp:band': " + value);
        }
        return new DimParams(num(slots[0], "weight"), num(slots[1], "wavelength"),
                num(slots[2], "amplitude"), num(slots[3], "band"));
    }

    private static double num(String raw, String slot) {
        try {
            return Double.parseDouble(raw.trim().toLowerCase(Locale.ROOT));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("malformed " + slot + " value '" + raw + "'");
        }
    }
}
