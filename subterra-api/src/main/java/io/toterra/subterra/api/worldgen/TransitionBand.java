package io.toterra.subterra.api.worldgen;

import java.util.Locale;
import java.util.Objects;

/**
 * p.2.36.2 过渡级（transition level）：维与维之间的过渡带（纯 JDK 不可变值）。
 * 过渡（海陆过渡、山地缓入 {@code mask^2}、群系权接等）抽为可配置过渡函数三元组：
 * {@code width}（过渡带宽度，{@code > 0}）+ {@link Curve}（曲线族）+ {@code order}（阶，{@code >= 1}，
 * 用于曲线幂次/缓动阶数）。
 * <p>
 * 曲线族<b>只给两支</b>（够用，不预设扩展）：{@link Curve#CLAMP_LINEAR}（截断线性）与
 * {@link Curve#SMOOTHSTEP}（平滑阶跃）。
 * <p>
 * p.2.36.2 the transition level: an inter-dimension transition band (pure-JDK immutable). Transitions
 * (land/sea, the mountain {@code mask^2} ease-in, biome weight blending …) are factored into a
 * configurable triple: {@code width} ({@code > 0}), a {@link Curve} and an {@code order} ({@code >= 1},
 * the curve's power / easing order). The curve family ships <b>two members only</b>:
 * {@link Curve#CLAMP_LINEAR} and {@link Curve#SMOOTHSTEP}.
 *
 * @param width the transition width ({@code > 0}, finite).
 * @param curve the curve family member (non-null).
 * @param order the curve order ({@code >= 1}).
 */
public record TransitionBand(double width, Curve curve, int order) {

    /** The default width ({@code 0.08}). / 缺省宽度（{@code 0.08}）。 */
    public static final double DEFAULT_WIDTH = 0.08D;

    /** The default order ({@code 1}). / 缺省阶（{@code 1}）。 */
    public static final int DEFAULT_ORDER = 1;

    public TransitionBand {
        Objects.requireNonNull(curve, "curve must not be null");
        if (!Double.isFinite(width) || width <= 0.0D) {
            throw new IllegalArgumentException("width must be finite and > 0: " + width);
        }
        if (order < 1) {
            throw new IllegalArgumentException("order must be >= 1: " + order);
        }
    }

    /** The default band (width {@code 0.08}, {@link Curve#SMOOTHSTEP}, order {@code 1}). /
     *  缺省过渡带（宽度 {@code 0.08}、{@link Curve#SMOOTHSTEP}、阶 {@code 1}）。 */
    public static TransitionBand defaults() {
        return new TransitionBand(DEFAULT_WIDTH, Curve.SMOOTHSTEP, DEFAULT_ORDER);
    }

    /** The td value ({@code <width>:<curve>:<order>}). / td 值（{@code <宽度>:<曲线>:<阶>}）。 */
    public String tdValue() {
        return Double.toString(width) + ":" + curve.id() + ":" + order;
    }

    /**
     * Parses a {@code <width>:<curve>:<order>} value. / 解析 {@code <宽度>:<曲线>:<阶>} 值。
     *
     * @param value the td value (non-null).
     * @throws NullPointerException     if {@code value} is null.
     * @throws IllegalArgumentException on a wrong slot count or an out-of-contract value.
     */
    public static TransitionBand parse(String value) {
        if (value == null) {
            throw new NullPointerException("transition value must not be null");
        }
        String[] slots = value.trim().split(":");
        if (slots.length != 3) {
            throw new IllegalArgumentException(
                    "transition must carry 3 slots '<width>:<curve>:<order>': " + value);
        }
        double width;
        int order;
        try {
            width = Double.parseDouble(slots[0].trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("malformed transition width '" + slots[0] + "'");
        }
        try {
            order = Integer.parseInt(slots[2].trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("malformed transition order '" + slots[2] + "'");
        }
        return new TransitionBand(width, Curve.parse(slots[1]), order);
    }

    /** The transition curve family (two members only). / 过渡曲线族（只有两支）。 */
    public enum Curve {

        /** Clamped linear transition. / 截断线性过渡。 */
        CLAMP_LINEAR,

        /** Smoothstep transition. / 平滑阶跃过渡。 */

        SMOOTHSTEP;

        /** The canonical id used by td rendering ({@code clamp-linear} / {@code smoothstep}). /
         *  td 渲染用的规范名（{@code clamp-linear} / {@code smoothstep}）。 */
        public String id() {
            return name().toLowerCase(Locale.ROOT).replace('_', '-');
        }

        /**
         * Parses a curve id, tolerating case, whitespace and {@code _} for {@code -}. /
         * 解析曲线名，容忍大小写、空白与 {@code _} 代 {@code -}。
         *
         * @param value the td value (non-null).
         * @throws NullPointerException     if {@code value} is null.
         * @throws IllegalArgumentException if the value names no known curve.
         */
        public static Curve parse(String value) {
            Objects.requireNonNull(value, "curve must not be null");
            String normalized = value.trim().toLowerCase(Locale.ROOT).replace('_', '-').replace(" ", "");
            return switch (normalized) {
                case "clamp-linear", "clamplinear", "linear" -> CLAMP_LINEAR;
                case "smoothstep", "smooth" -> SMOOTHSTEP;
                default -> throw new IllegalArgumentException("unknown transition curve '" + value + "'");
            };
        }
    }
}
