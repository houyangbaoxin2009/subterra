package io.toterra.subterra.api.worldgen;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * p.2.36.2 世界生成配置契约（纯 JDK 不可变值、确定性）。三层混合粒度（设计案 §2）全部落在这份
 * 契约上，是 p.2.36 全线（.3 三场定调 / .4 九维关系图 / .5 结构消费 / .6 洞穴群系）的<b>配置地基</b>：
 * <ol>
 *   <li><b>能力级</b>——{@code dims}：九维各一个 {@link DimCapability}（enable / disable / redirect）。</li>
 *   <li><b>变量维级</b>——{@code params}：九维各一条 {@link DimParams}（权重/波长/幅度/带位），
 *       与 {@code dims} 平行同序。</li>
 *   <li><b>过渡级</b>——{@code transitions}：三个具名 {@link TransitionBand}
 *       （{@value #TRANSITION_LAND_SEA} / {@value #TRANSITION_MOUNTAIN} / {@value #TRANSITION_BIOME}）。</li>
 * </ol>
 * 外加两段关系面：{@code edges}——九维关系图（{@link RelationEdge}，<b>不是固定 DAG</b>，边可增删改、
 * 边权可改写，引擎只提供缺省关系链作起点，加载后确定性拓扑排序、环确定性拒绝，
 * 求值序见 {@link #evaluationOrder()}）；{@code fields}——三场四元组（{@link FieldQuad}，
 * {@code coarse_elev} / {@code coarse_mountain} / {@code coarse_climate}，增益 0 即旁路，
 * 缺省相对 p.1.8.34 逐位恒等）。
 * <p>
 * 钉死的事实与纪律：
 * <ul>
 *   <li><b>窗口对齐</b>：{@code min_y} / {@code height} 为 {@value #WINDOW_ALIGN} 的倍数，
 *       {@code height > 0}，{@code min_y + height <= 2032}。</li>
 *   <li><b>海平面必须在窗口内</b>：{@code min_y < sea_level < min_y + height}——p.1.8.33 淹没回归
 *       的直接守门，越界确定性拒绝。</li>
 *   <li><b>九维固定序</b>：{@code dims} 与 {@code params} 必须恰为 {@link EcoDim#ALL} 九维、按该序排列。</li>
 *   <li><b>边规范序</b>：{@code edges} 必须按（from 序，to 序）严格升序——同时天然禁重复边。</li>
 *   <li><b>四方一致</b>：窗口改动必须「JSON ↔ Kind ↔ dimension_type ↔ 探针」同步（p.1.8.33 教训）。</li>
 * </ul>
 * <b>双接口</b>：{@link #td()} 渲染 td 表字面量、{@link #fromTd(String)} 解析回来（逐字节往返）。
 * 键为扁平点号形式；{@code p.*} / {@code dim.*} 恒渲染，{@code edge.*} 整段渲染（声明即整表替换），
 * {@code field.*} / {@code transition.*} 按条目仅渲染非缺省项（渲染 ∷ 解析仍逐字节稳定）。td 中声明任何
 * {@code edge.*} 即<b>整表替换</b>缺省关系链；{@code field.*} / {@code transition.*} 按具名槽合并。
 * 缺省值只是起点——每键均可覆盖。
 * <p>
 * p.2.36.2 the worldgen configuration contract (pure-JDK immutable, deterministic). All three hybrid
 * granularity levels (design §2) land on this contract, the <b>configuration foundation</b> of the whole
 * p.2.36 line: the capability level ({@code dims}, one {@link DimCapability} per dimension), the
 * variable-dimension level ({@code params}, one {@link DimParams} per dimension, parallel to {@code dims})
 * and the transition level ({@code transitions}, three named {@link TransitionBand}s) — plus two relation
 * sections: {@code edges}, the nine-dimension relation graph (not a fixed DAG; edges add/remove/re-weight
 * freely, the engine ships only a default chain, loads then topologically sorts deterministically with
 * cycles rejected, see {@link #evaluationOrder()}) and {@code fields}, the three tone-field quads
 * (gain 0 bypasses; the default is bit-identical to p.1.8.34). Pinned discipline: window alignment; the
 * sea level strictly inside the window (the p.1.8.33 drowning-regression guard); the nine-dimension fixed
 * order for both {@code dims} and {@code params}; edges in strict canonical (from, to) order — which also
 * bans duplicates; any window change keeps JSON ↔ Kind ↔ dimension_type ↔ probe in four-way agreement.
 * Dual interface: {@link #td()} renders and {@link #fromTd(String)} parses back byte-exactly. The
 * {@code p.*} / {@code dim.*} sections always render; {@code edge.*} / {@code field.*} /
 * {@code transition.*} render only when they differ from the defaults (render ∷ parse stays byte-stable).
 * Declaring any {@code edge.*} in td <b>replaces the whole default chain</b>; {@code field.*} /
 * {@code transition.*} merge by named slot. Defaults are only a starting point.
 *
 * @param minY        the window bottom Y (multiple of {@value #WINDOW_ALIGN}).
 * @param height      the window height (positive multiple of {@value #WINDOW_ALIGN}).
 * @param seaLevel    the sea level, strictly inside the window.
 * @param dims        the nine capability bits, exactly {@link EcoDim#ALL} in canonical order.
 * @param params      the nine parameter vectors, parallel to {@code dims}.
 * @param edges       the relation-graph edges in strict canonical (from, to) order, acyclic.
 * @param fields      the three tone-field quads in {@link #FIELD_IDS} order.
 * @param transitions the three transition bands in {@link #TRANSITION_IDS} order.
 */
public record WorldgenProfile(int minY, int height, double seaLevel, List<DimCapability> dims,
                              List<DimParams> params, List<RelationEdge> edges,
                              List<FieldQuad> fields, List<TransitionBand> transitions) {

    /** The default window bottom Y ({@code -128}), the p.2.36.1 landed default. /
     *  缺省窗口下沿 Y（{@code -128}），p.2.36.1 落地的默认值。 */
    public static final int DEFAULT_MIN_Y = -128;

    /** The default window height ({@code 720}), i.e. the window {@code [-128, 592)}. /
     *  缺省窗口高度（{@code 720}），即窗口 {@code [-128, 592)}。 */
    public static final int DEFAULT_HEIGHT = 720;

    /** The default sea level ({@code 127.0}), the p.1.8.33/34 design value. /
     *  缺省海平面（{@code 127.0}），p.1.8.33/34 的设计值。 */
    public static final double DEFAULT_SEA_LEVEL = 127.0D;

    /** The window alignment every MC dimension type obeys (section alignment, {@code 16}). /
     *  MC 维度类型遵循的窗口对齐（区段对齐，{@code 16}）。 */
    public static final int WINDOW_ALIGN = 16;

    /** The upper bound of {@code min_y + height} allowed by MC dimension types ({@code 2032}). /
     *  MC 维度类型允许的 {@code min_y + height} 上界（{@code 2032}）。 */
    public static final int MAX_WINDOW_TOP = 2032;

    /** The lower bound of {@code min_y} allowed by MC dimension types ({@code -2032}). /
     *  MC 维度类型允许的 {@code min_y} 下界（{@code -2032}）。 */
    public static final int MIN_WINDOW_BOTTOM = -2032;

    /** The td key prefix for dimension capability bits ({@code "dim."}). /
     *  九维能力位的 td 键前缀（{@code "dim."}）。 */
    public static final String DIM_KEY_PREFIX = "dim.";

    /** The td key prefix for dimension parameter vectors ({@code "p."}). /
     *  九维参数向量的 td 键前缀（{@code "p."}）。 */
    public static final String PARAM_KEY_PREFIX = "p.";

    /** The td key prefix for relation edges ({@code "edge."}). /
     *  关系图边的 td 键前缀（{@code "edge."}）。 */
    public static final String EDGE_KEY_PREFIX = "edge.";

    /** The td key prefix for tone-field quads ({@code "field."}). /
     *  三场四元组的 td 键前缀（{@code "field."}）。 */
    public static final String FIELD_KEY_PREFIX = "field.";

    /** The td key prefix for transition bands ({@code "transition."}). /
     *  过渡带的 td 键前缀（{@code "transition."}）。 */
    public static final String TRANSITION_KEY_PREFIX = "transition.";

    /** The three tone-field ids in fixed order (the Trimand exports, p.2.35). /
     *  三个定调场 id，固定序（Trimand 导出面，p.2.35）。 */
    public static final List<String> FIELD_IDS = List.of(
            "coarse_elev", "coarse_mountain", "coarse_climate");

    /** The three named transition bands in fixed order. / 三个具名过渡带，固定序。 */
    public static final List<String> TRANSITION_IDS = List.of(
            "land_sea", "mountain", "biome");

    /** The land/sea transition id. / 海陆过渡 id。 */
    public static final String TRANSITION_LAND_SEA = "land_sea";

    /** The mountain-ease transition id. / 山地缓入过渡 id。 */
    public static final String TRANSITION_MOUNTAIN = "mountain";

    /** The biome-weight transition id. / 群系权接过渡 id。 */
    public static final String TRANSITION_BIOME = "biome";

    public WorldgenProfile {
        if (minY % WINDOW_ALIGN != 0) {
            throw new IllegalArgumentException("min_y must be a multiple of " + WINDOW_ALIGN + ": " + minY);
        }
        if (minY < MIN_WINDOW_BOTTOM) {
            throw new IllegalArgumentException("min_y must be >= " + MIN_WINDOW_BOTTOM + ": " + minY);
        }
        if (height <= 0 || height % WINDOW_ALIGN != 0) {
            throw new IllegalArgumentException(
                    "height must be a positive multiple of " + WINDOW_ALIGN + ": " + height);
        }
        if (minY + height > MAX_WINDOW_TOP) {
            throw new IllegalArgumentException("min_y + height must be <= " + MAX_WINDOW_TOP
                    + ": " + (minY + height));
        }
        if (!Double.isFinite(seaLevel)) {
            throw new IllegalArgumentException("sea_level must be finite: " + seaLevel);
        }
        if (seaLevel <= minY || seaLevel >= minY + height) {
            throw new IllegalArgumentException("sea_level must sit strictly inside the window ["
                    + minY + ", " + (minY + height) + "): " + seaLevel);
        }
        Objects.requireNonNull(dims, "dims must not be null");
        List<DimCapability> dimsCopy = List.copyOf(dims);
        if (dimsCopy.size() != EcoDim.ALL.size()) {
            throw new IllegalArgumentException("dims must carry exactly " + EcoDim.ALL.size()
                    + " capability bits: " + dimsCopy.size());
        }
        for (int i = 0; i < dimsCopy.size(); i++) {
            String expected = EcoDim.ALL.get(i).id();
            String actual = dimsCopy.get(i).dim();
            if (!expected.equals(actual)) {
                throw new IllegalArgumentException("dims must follow the canonical order: expected '"
                        + expected + "' at index " + i + " but found '" + actual + "'");
            }
        }
        dims = dimsCopy;

        Objects.requireNonNull(params, "params must not be null");
        List<DimParams> paramsCopy = List.copyOf(params);
        if (paramsCopy.size() != EcoDim.ALL.size()) {
            throw new IllegalArgumentException("params must carry exactly " + EcoDim.ALL.size()
                    + " vectors: " + paramsCopy.size());
        }
        params = paramsCopy;

        Objects.requireNonNull(edges, "edges must not be null");
        List<RelationEdge> edgesCopy = List.copyOf(edges);
        int lastFrom = -1;
        int lastTo = -1;
        for (RelationEdge edge : edgesCopy) {
            int from = indexOf(edge.from());
            int to = indexOf(edge.to());
            if (from < 0 || to < 0) {
                throw new IllegalArgumentException("edge endpoints must be built-in dimensions: " + edge);
            }
            if (from < lastFrom || (from == lastFrom && to <= lastTo)) {
                throw new IllegalArgumentException("edges must follow the strict canonical (from, to) "
                        + "order — this also bans duplicate edges: " + edge.tdKey());
            }
            lastFrom = from;
            lastTo = to;
        }
        edges = edgesCopy;

        Objects.requireNonNull(fields, "fields must not be null");
        List<FieldQuad> fieldsCopy = List.copyOf(fields);
        if (fieldsCopy.size() != FIELD_IDS.size()) {
            throw new IllegalArgumentException("fields must carry exactly " + FIELD_IDS.size()
                    + " quads: " + fieldsCopy.size());
        }
        fields = fieldsCopy;

        Objects.requireNonNull(transitions, "transitions must not be null");
        List<TransitionBand> transitionsCopy = List.copyOf(transitions);
        if (transitionsCopy.size() != TRANSITION_IDS.size()) {
            throw new IllegalArgumentException("transitions must carry exactly " + TRANSITION_IDS.size()
                    + " bands: " + transitionsCopy.size());
        }
        transitions = transitionsCopy;

        evaluationOrder(dims, edges); // cycle check at construction time (deterministic rejection)
    }

    /** The canonical index of a dimension id, or {@code -1} when unknown. /
     *  维度 id 的规范序下标，未知返回 {@code -1}。 */
    private static int indexOf(String dim) {
        for (int i = 0; i < EcoDim.ALL.size(); i++) {
            if (EcoDim.ALL.get(i).id().equals(dim)) {
                return i;
            }
        }
        return -1;
    }

    /** The default profile: window {@code [-128, 592)} with sea level {@code 127.0}, all nine dimensions
     *  enabled with neutral parameter vectors, the default relation chain, all three tone fields bypassed
     *  and the three default transition bands. / 缺省档案：窗口 {@code [-128, 592)}、海平面
     *  {@code 127.0}，九维全启用、参数向量取中性缺省，缺省关系链，三场全部旁路，过渡带取缺省。 */
    public static WorldgenProfile defaults() {
        List<DimCapability> bits = new ArrayList<>();
        List<DimParams> vectors = new ArrayList<>();
        for (EcoDim dim : EcoDim.ALL) {
            bits.add(DimCapability.enabled(dim.id()));
            vectors.add(DimParams.defaults());
        }
        return new WorldgenProfile(DEFAULT_MIN_Y, DEFAULT_HEIGHT, DEFAULT_SEA_LEVEL,
                bits, vectors, defaultEdges(), defaultFields(), defaultTransitions());
    }

    /**
     * The default relation chain (design §3.2, weights {@code 1.0}, blend {@link Blend#MIX}):
     * {@code terrain} and {@code climate} are roots (terrain is driven by the model tone fields);
     * {@code hydro ← climate, terrain}; {@code vegetation / surface ← climate, terrain, hydro};
     * {@code litho / mineral ← terrain, surface}; {@code fauna / relic ← climate, surface}. /
     * 缺省关系链（设计案 §3.2，边权 {@code 1.0}、混合 {@link Blend#MIX}）：{@code terrain} 与
     * {@code climate} 为根（terrain 由模型定调场驱动）；其余维按缺省链挂接。
     */
    public static List<RelationEdge> defaultEdges() {
        List<RelationEdge> edges = new ArrayList<>();
        edges.add(RelationEdge.of("terrain", "vegetation"));
        edges.add(RelationEdge.of("terrain", "hydro"));
        edges.add(RelationEdge.of("terrain", "surface"));
        edges.add(RelationEdge.of("terrain", "litho"));
        edges.add(RelationEdge.of("terrain", "mineral"));
        edges.add(RelationEdge.of("climate", "vegetation"));
        edges.add(RelationEdge.of("climate", "hydro"));
        edges.add(RelationEdge.of("climate", "surface"));
        edges.add(RelationEdge.of("climate", "fauna"));
        edges.add(RelationEdge.of("climate", "relic"));
        edges.add(RelationEdge.of("hydro", "vegetation"));
        edges.add(RelationEdge.of("hydro", "surface"));
        edges.add(RelationEdge.of("surface", "litho"));
        edges.add(RelationEdge.of("surface", "mineral"));
        edges.add(RelationEdge.of("surface", "fauna"));
        edges.add(RelationEdge.of("surface", "relic"));
        return List.copyOf(edges);
    }

    /** The default tone-field quads: all three bypassed (bit-identical to p.1.8.34). /
     *  缺省三场四元组：全部旁路（相对 p.1.8.34 逐位恒等）。 */
    public static List<FieldQuad> defaultFields() {
        return List.of(FieldQuad.bypass(), FieldQuad.bypass(), FieldQuad.bypass());
    }

    /** The default transition bands: {@code land_sea} (0.08, smoothstep, 1), {@code mountain}
     *  (0.25, smoothstep, 2), {@code biome} (0.12, clamp-linear, 1). /
     *  缺省过渡带：{@code land_sea}(0.08, smoothstep, 1)、{@code mountain}(0.25, smoothstep, 2)、
     *  {@code biome}(0.12, clamp-linear, 1)。 */
    public static List<TransitionBand> defaultTransitions() {
        return List.of(
                new TransitionBand(0.08D, TransitionBand.Curve.SMOOTHSTEP, 1),
                new TransitionBand(0.25D, TransitionBand.Curve.SMOOTHSTEP, 2),
                new TransitionBand(0.12D, TransitionBand.Curve.CLAMP_LINEAR, 1));
    }

    /** The window top Y (exclusive): {@code minY + height}. / 窗口上沿 Y（不含）：{@code minY + height}。 */
    public int windowTop() {
        return minY + height;
    }

    /** The capability bit of the dimension, or {@code null} for an unknown id (pure read). /
     *  该维的能力位，未知 id 返回 {@code null}（纯读）。 */
    public DimCapability capability(String dim) {
        if (dim == null) {
            return null;
        }
        String key = dim.trim().toLowerCase(Locale.ROOT);
        for (DimCapability cap : dims) {
            if (cap.dim().equals(key)) {
                return cap;
            }
        }
        return null;
    }

    /** The mode of the dimension ({@link DimMode#ENABLE} when the id is unknown, so reads never
     *  throw). / 该维的模式（未知 id 视为 {@link DimMode#ENABLE}，读永不抛）。 */
    public DimMode mode(String dim) {
        DimCapability cap = capability(dim);
        return cap == null ? DimMode.ENABLE : cap.mode();
    }

    /** The parameter vector of the dimension (the neutral default when the id is unknown). /
     *  该维的参数向量（未知 id 返回中性缺省）。 */
    public DimParams params(String dim) {
        int index = indexOf(dim == null ? "" : dim.trim().toLowerCase(Locale.ROOT));
        return index < 0 ? DimParams.defaults() : params.get(index);
    }

    /** The tone-field quad by id ({@code null} for an unknown id). /
     *  按 id 取三场四元组（未知 id 返回 {@code null}）。 */
    public FieldQuad field(String id) {
        int index = FIELD_IDS.indexOf(id);
        return index < 0 ? null : fields.get(index);
    }

    /** The transition band by id ({@code null} for an unknown id). /
     *  按 id 取过渡带（未知 id 返回 {@code null}）。 */
    public TransitionBand transition(String id) {
        int index = TRANSITION_IDS.indexOf(id);
        return index < 0 ? null : transitions.get(index);
    }

    /**
     * The deterministic evaluation order of the nine dimensions: a Kahn topological sort over the
     * relation graph, ties broken by the canonical dimension order; a cycle is a deterministic
     * {@link IllegalArgumentException}. / 九维的确定性求值序：对关系图做 Kahn 拓扑排序，并列时按
     * 维度规范序打破；环是确定性的 {@link IllegalArgumentException}。
     *
     * @return the dimension ids in evaluation order (nine entries).
     */
    public List<String> evaluationOrder() {
        return evaluationOrder(dims, edges);
    }

    private static List<String> evaluationOrder(List<DimCapability> dims, List<RelationEdge> edges) {
        int n = dims.size();
        int[] indegree = new int[n];
        List<List<Integer>> outgoing = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            outgoing.add(new ArrayList<>());
        }
        for (RelationEdge edge : edges) {
            int from = indexOf(edge.from());
            int to = indexOf(edge.to());
            outgoing.get(from).add(to);
            indegree[to]++;
        }
        List<Integer> ready = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            if (indegree[i] == 0) {
                ready.add(i);
            }
        }
        List<String> order = new ArrayList<>(n);
        while (!ready.isEmpty()) {
            int picked = ready.remove(0); // ready is kept in canonical order
            order.add(dims.get(picked).dim());
            for (int to : outgoing.get(picked)) {
                indegree[to]--;
                if (indegree[to] == 0) {
                    insertByCanonicalOrder(ready, to);
                }
            }
        }
        if (order.size() != n) {
            List<String> stuck = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                if (indegree[i] > 0) {
                    stuck.add(dims.get(i).dim());
                }
            }
            throw new IllegalArgumentException("relation graph contains a cycle: " + stuck);
        }
        return List.copyOf(order);
    }

    private static void insertByCanonicalOrder(List<Integer> ready, int index) {
        int at = 0;
        while (at < ready.size() && ready.get(at) < index) {
            at++;
        }
        ready.add(at, index);
    }

    /** A copy with the window replaced (re-validated; the receiver is unchanged). /
     *  替换窗口的副本（重新校验；原值不变）。 */
    public WorldgenProfile withWindow(int minY, int height, double seaLevel) {
        return new WorldgenProfile(minY, height, seaLevel, dims, params, edges, fields, transitions);
    }

    /** A copy with one capability bit replaced, canonical order preserved (re-validated). /
     *  替换单个能力位的副本，保持规范序（重新校验）。 */
    public WorldgenProfile withDim(String dim, DimMode mode, String target) {
        Objects.requireNonNull(mode, "mode must not be null");
        String key = dim == null ? "" : dim.trim().toLowerCase(Locale.ROOT);
        List<DimCapability> out = new ArrayList<>();
        boolean replaced = false;
        for (DimCapability cap : dims) {
            if (cap.dim().equals(key)) {
                out.add(new DimCapability(key, mode, target == null ? "" : target));
                replaced = true;
            } else {
                out.add(cap);
            }
        }
        if (!replaced) {
            throw new IllegalArgumentException("unknown dimension '" + dim + "'");
        }
        return new WorldgenProfile(minY, height, seaLevel, out, params, edges, fields, transitions);
    }

    /** A copy with one parameter vector replaced (re-validated). /
     *  替换单个参数向量的副本（重新校验）。 */
    public WorldgenProfile withParams(String dim, DimParams vector) {
        Objects.requireNonNull(vector, "vector must not be null");
        int index = indexOf(dim == null ? "" : dim.trim().toLowerCase(Locale.ROOT));
        if (index < 0) {
            throw new IllegalArgumentException("unknown dimension '" + dim + "'");
        }
        List<DimParams> out = new ArrayList<>(params);
        out.set(index, vector);
        return new WorldgenProfile(minY, height, seaLevel, dims, out, edges, fields, transitions);
    }

    /** A copy with the edge replaced or added by its (from, to) pair, canonical order restored
     *  (re-validated). / 按 (from, to) 替换或新增边的副本，恢复规范序（重新校验）。 */
    public WorldgenProfile withEdge(RelationEdge edge) {
        Objects.requireNonNull(edge, "edge must not be null");
        List<RelationEdge> out = new ArrayList<>();
        boolean replaced = false;
        for (RelationEdge existing : edges) {
            if (existing.from().equals(edge.from()) && existing.to().equals(edge.to())) {
                out.add(edge);
                replaced = true;
            } else {
                out.add(existing);
            }
        }
        if (!replaced) {
            out.add(edge);
        }
        out.sort((a, b) -> {
            int byFrom = Integer.compare(indexOf(a.from()), indexOf(b.from()));
            return byFrom != 0 ? byFrom : Integer.compare(indexOf(a.to()), indexOf(b.to()));
        });
        return new WorldgenProfile(minY, height, seaLevel, dims, params, out, fields, transitions);
    }

    /** A copy with the edge removed (absent pairs are a no-op). /
     *  移除边的副本（不存在的边是幂等空操作）。 */
    public WorldgenProfile withoutEdge(String from, String to) {
        String f = from == null ? "" : from.trim().toLowerCase(Locale.ROOT);
        String t = to == null ? "" : to.trim().toLowerCase(Locale.ROOT);
        List<RelationEdge> out = new ArrayList<>();
        for (RelationEdge edge : edges) {
            if (!edge.from().equals(f) || !edge.to().equals(t)) {
                out.add(edge);
            }
        }
        return new WorldgenProfile(minY, height, seaLevel, dims, params, out, fields, transitions);
    }

    /** A copy with one tone-field quad replaced by id (re-validated). /
     *  按 id 替换单个三场四元组的副本（重新校验）。 */
    public WorldgenProfile withField(String id, FieldQuad quad) {
        Objects.requireNonNull(quad, "quad must not be null");
        int index = FIELD_IDS.indexOf(id);
        if (index < 0) {
            throw new IllegalArgumentException("unknown field id '" + id + "'");
        }
        List<FieldQuad> out = new ArrayList<>(fields);
        out.set(index, quad);
        return new WorldgenProfile(minY, height, seaLevel, dims, params, edges, out, transitions);
    }

    /** A copy with one transition band replaced by id (re-validated). /
     *  按 id 替换单个过渡带的副本（重新校验）。 */
    public WorldgenProfile withTransition(String id, TransitionBand band) {
        Objects.requireNonNull(band, "band must not be null");
        int index = TRANSITION_IDS.indexOf(id);
        if (index < 0) {
            throw new IllegalArgumentException("unknown transition id '" + id + "'");
        }
        List<TransitionBand> out = new ArrayList<>(transitions);
        out.set(index, band);
        return new WorldgenProfile(minY, height, seaLevel, dims, params, edges, fields, out);
    }

    /**
     * Renders the profile as a td table literal (canonical key order, deterministic). The window and
     * the nine-dimension sections always render; the edge section renders wholly (a declaration replaces
     * the chain) while the field/transition sections render only their non-default entries. /
     * 渲染为 td 表字面量（规范键序、确定性）。窗口与九维两段恒渲染；边段整段渲染（声明即整表替换），
     * 场/带两段按条目仅渲染非缺省项。
     *
     * @return the td document (never null); {@link #fromTd(String)} parses it back losslessly.
     */
    public String td() {
        StringBuilder sb = new StringBuilder();
        sb.append("[\n");
        sb.append("  min_y = ").append(minY).append(",\n");
        sb.append("  height = ").append(height).append(",\n");
        sb.append("  sea_level = ").append(Double.toString(seaLevel)).append(",\n");
        for (DimCapability cap : dims) {
            sb.append("  ").append(DIM_KEY_PREFIX).append(cap.dim()).append(" = ")
                    .append(cap.tdValue()).append(",\n");
        }
        for (int i = 0; i < params.size(); i++) {
            sb.append("  ").append(PARAM_KEY_PREFIX).append(dims.get(i).dim()).append(" = ")
                    .append(params.get(i).tdValue()).append(",\n");
        }
        if (!edges.equals(defaultEdges())) {
            for (RelationEdge edge : edges) {
                sb.append("  ").append(edge.tdKey()).append(" = ").append(edge.tdValue()).append(",\n");
            }
        }
        List<FieldQuad> fieldDefaults = defaultFields();
        for (int i = 0; i < fields.size(); i++) {
            if (!fields.get(i).equals(fieldDefaults.get(i))) {
                sb.append("  ").append(FIELD_KEY_PREFIX).append(FIELD_IDS.get(i)).append(" = ")
                        .append(fields.get(i).tdValue()).append(",\n");
            }
        }
        List<TransitionBand> transitionDefaults = defaultTransitions();
        for (int i = 0; i < transitions.size(); i++) {
            if (!transitions.get(i).equals(transitionDefaults.get(i))) {
                sb.append("  ").append(TRANSITION_KEY_PREFIX).append(TRANSITION_IDS.get(i)).append(" = ")
                        .append(transitions.get(i).tdValue()).append(",\n");
            }
        }
        sb.append("]");
        return sb.toString();
    }

    /**
     * Parses a td table literal into a profile. Recognised keys: {@code min_y}, {@code height},
     * {@code sea_level}, {@code dim.<id>}, {@code p.<id>}, {@code edge.<from>.<to>},
     * {@code field.<id>} and {@code transition.<id>}. Undeclared keys fall back to {@link #defaults()};
     * declaring any {@code edge.*} replaces the whole default chain; {@code field.*} /
     * {@code transition.*} merge by named slot. A {@code type tie<data>} header, {@code //} comments and
     * stray/trailing commas are tolerated; duplicate keys, unknown keys, malformed entries, out-of-window
     * sea levels, out-of-order edges and cyclic graphs are rejected with
     * {@link IllegalArgumentException}.
     * / 解析 td 表字面量为档案。识别 {@code min_y} / {@code height} / {@code sea_level} /
     * {@code dim.<id>} / {@code p.<id>} / {@code edge.<from>.<to>} / {@code field.<id>} /
     * {@code transition.<id>} 八类键；未声明键回落 {@link #defaults()}；声明任何 {@code edge.*} 即
     * 整表替换缺省关系链，{@code field.*} / {@code transition.*} 按具名槽合并。容忍
     * {@code type tie<data>} 头、{@code //} 注释与多余逗号；重复键、未知键、畸形条目、越窗海平面、
     * 乱序边与带环图确定性拒绝。
     *
     * @param source the td source (non-null, non-blank).
     * @return the parsed profile.
     * @throws NullPointerException     if {@code source} is null.
     * @throws IllegalArgumentException on any malformed or out-of-contract input.
     */
    public static WorldgenProfile fromTd(String source) {
        Objects.requireNonNull(source, "td source must not be null");
        String text = normalize(source);
        if (text.isEmpty()) {
            throw new IllegalArgumentException("malformed td: empty document");
        }
        if (text.charAt(0) != '[' || text.charAt(text.length() - 1) != ']') {
            throw new IllegalArgumentException("malformed td: expected a '[ ... ]' table literal");
        }
        String body = text.substring(1, text.length() - 1);
        Integer minY = null;
        Integer height = null;
        Double seaLevel = null;
        Map<String, String> dimValues = new LinkedHashMap<>();
        Map<String, String> paramValues = new LinkedHashMap<>();
        Map<String, RelationEdge> edgeValues = new LinkedHashMap<>();
        Map<String, String> fieldValues = new LinkedHashMap<>();
        Map<String, String> transitionValues = new LinkedHashMap<>();
        for (String raw : body.split(",")) {
            String entry = raw.trim();
            if (entry.isEmpty()) {
                continue; // tolerate stray / trailing commas
            }
            int eq = entry.indexOf('=');
            if (eq <= 0) {
                throw new IllegalArgumentException("malformed td entry: '" + entry + "'");
            }
            String key = entry.substring(0, eq).trim();
            String value = entry.substring(eq + 1).trim();
            if (value.isEmpty()) {
                throw new IllegalArgumentException("malformed td entry: empty value for '" + key + "'");
            }
            if (key.equals("min_y")) {
                requireAbsent(minY, key);
                minY = parseInt(value, key);
            } else if (key.equals("height")) {
                requireAbsent(height, key);
                height = parseInt(value, key);
            } else if (key.equals("sea_level")) {
                requireAbsent(seaLevel, key);
                seaLevel = parseDouble(value, key);
            } else if (key.startsWith(DIM_KEY_PREFIX)) {
                String dim = requireDimKey(key, DIM_KEY_PREFIX);
                if (dimValues.put(dim, value) != null) {
                    throw new IllegalArgumentException("duplicate td key '" + key + "'");
                }
            } else if (key.startsWith(PARAM_KEY_PREFIX)) {
                String dim = requireDimKey(key, PARAM_KEY_PREFIX);
                if (paramValues.put(dim, value) != null) {
                    throw new IllegalArgumentException("duplicate td key '" + key + "'");
                }
            } else if (key.startsWith(EDGE_KEY_PREFIX)) {
                String pair = key.substring(EDGE_KEY_PREFIX.length());
                int dot = pair.indexOf('.');
                if (dot <= 0 || dot == pair.length() - 1 || pair.indexOf('.', dot + 1) >= 0) {
                    throw new IllegalArgumentException("edge key must be 'edge.<from>.<to>': " + key);
                }
                String from = pair.substring(0, dot).trim().toLowerCase(Locale.ROOT);
                String to = pair.substring(dot + 1).trim().toLowerCase(Locale.ROOT);
                RelationEdge edge = RelationEdge.parse(from, to, value);
                if (edgeValues.put(from + ">" + to, edge) != null) {
                    throw new IllegalArgumentException("duplicate td key '" + key + "'");
                }
            } else if (key.startsWith(FIELD_KEY_PREFIX)) {
                String id = key.substring(FIELD_KEY_PREFIX.length()).trim();
                if (!FIELD_IDS.contains(id)) {
                    throw new IllegalArgumentException("unknown td key '" + key + "'");
                }
                if (fieldValues.put(id, value) != null) {
                    throw new IllegalArgumentException("duplicate td key '" + key + "'");
                }
            } else if (key.startsWith(TRANSITION_KEY_PREFIX)) {
                String id = key.substring(TRANSITION_KEY_PREFIX.length()).trim();
                if (!TRANSITION_IDS.contains(id)) {
                    throw new IllegalArgumentException("unknown td key '" + key + "'");
                }
                if (transitionValues.put(id, value) != null) {
                    throw new IllegalArgumentException("duplicate td key '" + key + "'");
                }
            } else {
                throw new IllegalArgumentException("unknown td key '" + key + "'");
            }
        }
        WorldgenProfile base = defaults();
        int minYValue = minY == null ? base.minY() : minY;
        int heightValue = height == null ? base.height() : height;
        double seaValue = seaLevel == null ? base.seaLevel() : seaLevel;

        List<DimCapability> bits = new ArrayList<>();
        List<DimParams> vectors = new ArrayList<>();
        for (int i = 0; i < base.dims().size(); i++) {
            String dim = base.dims().get(i).dim();
            String capValue = dimValues.get(dim);
            bits.add(capValue == null ? base.dims().get(i) : parseCapability(dim, capValue));
            String paramValue = paramValues.get(dim);
            vectors.add(paramValue == null ? base.params().get(i) : DimParams.parse(paramValue));
        }

        List<RelationEdge> edgeList;
        if (edgeValues.isEmpty()) {
            edgeList = base.edges();
        } else {
            edgeList = new ArrayList<>(edgeValues.values());
            edgeList.sort((a, b) -> {
                int byFrom = Integer.compare(indexOf(a.from()), indexOf(b.from()));
                return byFrom != 0 ? byFrom : Integer.compare(indexOf(a.to()), indexOf(b.to()));
            });
        }

        List<FieldQuad> fieldList = new ArrayList<>(base.fields());
        for (int i = 0; i < FIELD_IDS.size(); i++) {
            String value = fieldValues.get(FIELD_IDS.get(i));
            if (value != null) {
                fieldList.set(i, FieldQuad.parse(value));
            }
        }

        List<TransitionBand> transitionList = new ArrayList<>(base.transitions());
        for (int i = 0; i < TRANSITION_IDS.size(); i++) {
            String value = transitionValues.get(TRANSITION_IDS.get(i));
            if (value != null) {
                transitionList.set(i, TransitionBand.parse(value));
            }
        }

        return new WorldgenProfile(minYValue, heightValue, seaValue, bits, vectors,
                edgeList, fieldList, transitionList);
    }

    private static DimCapability parseCapability(String dim, String value) {
        if (value.startsWith(DimCapability.REDIRECT_PREFIX)) {
            return new DimCapability(dim, DimMode.REDIRECT,
                    value.substring(DimCapability.REDIRECT_PREFIX.length()).trim());
        }
        return new DimCapability(dim, DimMode.parse(value), DimCapability.NO_TARGET);
    }

    /** Validates a {@code <prefix><dim>} key's dimension id and returns it (lower-cased). /
     *  校验 {@code <前缀><维>} 键的维度 id 并返回（小写）。 */
    private static String requireDimKey(String key, String prefix) {
        String dim = key.substring(prefix.length()).trim().toLowerCase(Locale.ROOT);
        if (EcoDim.of(dim) == null) {
            throw new IllegalArgumentException("unknown td key '" + key + "'");
        }
        return dim;
    }

    private static void requireAbsent(Object existing, String key) {
        if (existing != null) {
            throw new IllegalArgumentException("duplicate td key '" + key + "'");
        }
    }

    private static int parseInt(String value, String key) {
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("malformed integer for '" + key + "': " + value);
        }
    }

    private static double parseDouble(String value, String key) {
        try {
            return Double.parseDouble(value.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("malformed number for '" + key + "': " + value);
        }
    }

    /** Strips {@code //} comments, an optional {@code type ...} header and outer whitespace, joining
     *  the surviving lines with a single space (flat keys only — no nested tables). /
     *  去掉 {@code //} 注释、可选的 {@code type ...} 头与外围空白，保留行以单空格连接
     *  （仅扁平键——无嵌套表）。 */
    private static String normalize(String source) {
        StringBuilder sb = new StringBuilder(source.length());
        for (String line : source.split("\n", -1)) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("//") || trimmed.startsWith("type ")) {
                continue;
            }
            int comment = trimmed.indexOf("//");
            if (comment >= 0) {
                trimmed = trimmed.substring(0, comment).trim();
            }
            if (!trimmed.isEmpty()) {
                if (sb.length() > 0) {
                    sb.append(' ');
                }
                sb.append(trimmed);
            }
        }
        return sb.toString().trim();
    }
}
