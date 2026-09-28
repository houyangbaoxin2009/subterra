package io.toterra.subterra.api.worldgen;

import java.util.Objects;

/**
 * p.2.36.2 三场四元组（纯 JDK 不可变值）：扩散模型（Trimand，p.2.35）输出的三个宏观定调场
 * ——{@code coarse_elev} / {@code coarse_mountain} / {@code coarse_climate}——各自的调制参数。
 * <ul>
 *   <li>{@code gain}——增益；<b>{@code 0.0} 即旁路</b>（缺省值），此时该场完全不参与，
 *       密度输出与 p.1.8.34 逐位一致（设计案 §3.1 设计不变式）。</li>
 *   <li>{@code bias}——偏置（叠加在场上）。</li>
 *   <li>{@code band}——带位（{@code [0,1]}，该场生效的取值带）。</li>
 *   <li>{@code blend}——混合函数（{@link Blend}，缺省 {@link Blend#MIX}）。</li>
 * </ul>
 * 性能纪律（设计案 §3.4）：模型只定调这三个最需空间连续性的宏观低频场，高频细节与其余六维
 * 全部由噪声等传统算法生成——本 record 只描述"调多少"，不含任何逐块生成语义。
 * <p>
 * p.2.36.2 the three-field quad (pure-JDK immutable): the modulation parameters of each macro tone field
 * emitted by the diffusion model (Trimand, p.2.35) — {@code coarse_elev} / {@code coarse_mountain} /
 * {@code coarse_climate}. {@code gain} is the gain (a {@code 0.0} gain <b>bypasses</b> the field entirely;
 * that is the default, so the density output stays bit-identical to p.1.8.34 per design §3.1); {@code bias}
 * is added to the field; {@code band} is the active value band ({@code [0,1]}); {@code blend} is the blend
 * function (default {@link Blend#MIX}). Performance discipline (design §3.4): the model tones only these
 * three spatial-continuity fields; high-frequency detail and the other six dimensions come from classic
 * noise algorithms — this record describes "how much tone", never per-block generation.
 *
 * @param gain  the gain ({@code 0.0} bypasses the field).
 * @param bias  the bias added to the field.
 * @param band  the active band position ({@code [0,1]}).
 * @param blend the blend function (non-null).
 */
public record FieldQuad(double gain, double bias, double band, Blend blend) {

    /** The bypass gain ({@code 0.0}): the field takes no part. / 旁路增益（{@code 0.0}）：该场不参与。 */
    public static final double BYPASS_GAIN = 0.0D;

    /** The default bias ({@code 0.0}). / 缺省偏置（{@code 0.0}）。 */
    public static final double DEFAULT_BIAS = 0.0D;

    /** The default band ({@code 0.5}). / 缺省带位（{@code 0.5}）。 */
    public static final double DEFAULT_BAND = 0.5D;

    public FieldQuad {
        Objects.requireNonNull(blend, "blend must not be null");
        if (!Double.isFinite(gain)) {
            throw new IllegalArgumentException("gain must be finite: " + gain);
        }
        if (!Double.isFinite(bias)) {
            throw new IllegalArgumentException("bias must be finite: " + bias);
        }
        if (!Double.isFinite(band) || band < 0.0D || band > 1.0D) {
            throw new IllegalArgumentException("band must be finite within [0,1]: " + band);
        }
    }

    /** The bypass quad (gain {@code 0.0}): identity with respect to p.1.8.34. /
     *  旁路四元组（增益 {@code 0.0}）：相对 p.1.8.34 恒等。 */
    public static FieldQuad bypass() {
        return new FieldQuad(BYPASS_GAIN, DEFAULT_BIAS, DEFAULT_BAND, Blend.MIX);
    }

    /** Whether this field is bypassed (gain {@code 0.0}). / 该场是否被旁路（增益 {@code 0.0}）。 */
    public boolean bypassed() {
        return gain == BYPASS_GAIN;
    }

    /** The td value ({@code <gain>:<bias>:<band>:<blend>}). /
     *  td 值（{@code <增益>:<偏置>:<带位>:<混合>}）。 */
    public String tdValue() {
        return Double.toString(gain) + ":" + Double.toString(bias) + ":"
                + Double.toString(band) + ":" + blend.id();
    }

    /**
     * Parses a {@code <gain>:<bias>:<band>:<blend>} value. /
     * 解析 {@code <增益>:<偏置>:<带位>:<混合>} 值。
     *
     * @param value the td value (non-null).
     * @throws NullPointerException     if {@code value} is null.
     * @throws IllegalArgumentException on a wrong slot count or an out-of-contract value.
     */
    public static FieldQuad parse(String value) {
        if (value == null) {
            throw new NullPointerException("field quad value must not be null");
        }
        String[] slots = value.trim().split(":");
        if (slots.length != 4) {
            throw new IllegalArgumentException(
                    "field quad must carry 4 slots '<gain>:<bias>:<band>:<blend>': " + value);
        }
        return new FieldQuad(num(slots[0], "gain"), num(slots[1], "bias"),
                num(slots[2], "band"), Blend.parse(slots[3]));
    }

    private static double num(String raw, String slot) {
        try {
            return Double.parseDouble(raw.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("malformed " + slot + " value '" + raw + "'");
        }
    }
}
