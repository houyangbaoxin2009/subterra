package io.toterra.subterra.api.worldgen;

import java.util.Locale;
import java.util.Objects;

/**
 * p.2.36.2 九维关系图的一条边（纯 JDK 不可变值）：{@code from → to} 表示 {@code to} 维消费
 * {@code from} 维，权重 {@code weight} 与混合函数 {@link Blend}。
 * <p>
 * 用户定案（设计案 §3.2）：关系图<b>不是固定 DAG</b>——边可增删改、边权可改写，引擎只提供一条
 * <b>缺省关系链</b>作为起点；引擎侧在图加载后做确定性拓扑排序（环检测确定性报错），
 * 求值序由排序结果决定（见 {@link WorldgenProfile#evaluationOrder()}）。
 * <p>
 * 契约：{@code from} / {@code to} 必须是 {@link EcoDim#ALL} 的内建维（小写归一），
 * {@code from != to}（自环确定性拒绝），{@code weight} 有限且 {@code >= 0}，
 * {@code blend} 非空。
 * <p>
 * p.2.36.2 one edge of the nine-dimension relation graph (pure-JDK immutable): {@code from → to} means
 * dimension {@code to} consumes dimension {@code from}, with a {@code weight} and a {@link Blend}. Per the
 * user decision (design §3.2) the graph is <b>not a fixed DAG</b> — edges can be added, removed and
 * re-weighted, and the engine only ships a <b>default relation chain</b> as a starting point; the engine
 * performs a deterministic topological sort after loading (cycles are a deterministic error) and the
 * evaluation order follows that sort ({@link WorldgenProfile#evaluationOrder()}). Contract: both ends are
 * built-in {@link EcoDim#ALL} ids (lower-cased), {@code from != to} (self-edges rejected), {@code weight}
 * finite and {@code >= 0}, {@code blend} non-null.
 *
 * @param from   the source dimension id.
 * @param to     the consuming dimension id.
 * @param weight the edge weight ({@code >= 0}, finite).
 * @param blend  the blend function (non-null).
 */
public record RelationEdge(String from, String to, double weight, Blend blend) {

    /** The default edge weight ({@code 1.0}). / 缺省边权（{@code 1.0}）。 */
    public static final double DEFAULT_WEIGHT = 1.0D;

    public RelationEdge {
        Objects.requireNonNull(from, "from must not be null");
        Objects.requireNonNull(to, "to must not be null");
        Objects.requireNonNull(blend, "blend must not be null");
        from = from.trim().toLowerCase(Locale.ROOT);
        to = to.trim().toLowerCase(Locale.ROOT);
        if (EcoDim.of(from) == null) {
            throw new IllegalArgumentException("unknown dimension '" + from + "'");
        }
        if (EcoDim.of(to) == null) {
            throw new IllegalArgumentException("unknown dimension '" + to + "'");
        }
        if (from.equals(to)) {
            throw new IllegalArgumentException("self-edge is not allowed: " + from);
        }
        if (!Double.isFinite(weight) || weight < 0.0D) {
            throw new IllegalArgumentException("weight must be finite and >= 0: " + weight);
        }
    }

    /** The edge with the default weight and blend. / 缺省权重与混合函数的边。 */
    public static RelationEdge of(String from, String to) {
        return new RelationEdge(from, to, DEFAULT_WEIGHT, Blend.MIX);
    }

    /** The td key of this edge ({@code edge.<from>.<to>}). / 该边的 td 键（{@code edge.<from>.<to>}）。 */
    public String tdKey() {
        return "edge." + from + "." + to;
    }

    /** The td value of this edge ({@code <weight>:<blend>}). / 该边的 td 值（{@code <权重>:<混合>}）。 */
    public String tdValue() {
        return Double.toString(weight) + ":" + blend.id();
    }

    /**
     * Parses the {@code <weight>:<blend>} value for an already-known edge. /
     * 解析已知边的 {@code <权重>:<混合>} 值。
     *
     * @param from  the source dimension id.
     * @param to    the consuming dimension id.
     * @param value the td value (non-null).
     * @throws IllegalArgumentException on a wrong slot count or an out-of-contract value.
     */
    public static RelationEdge parse(String from, String to, String value) {
        if (value == null) {
            throw new NullPointerException("edge value must not be null");
        }
        String[] slots = value.trim().split(":");
        if (slots.length != 2) {
            throw new IllegalArgumentException("edge must carry 2 slots '<weight>:<blend>': " + value);
        }
        double weight;
        try {
            weight = Double.parseDouble(slots[0].trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("malformed edge weight '" + slots[0] + "'");
        }
        return new RelationEdge(from, to, weight, Blend.parse(slots[1]));
    }
}
