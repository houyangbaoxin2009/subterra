package io.toterra.subterra.engine.worldgen.pipeline.dimworlds;

import io.toterra.subterra.engine.worldgen.pipeline.dimension.DimensionPlan;
import io.toterra.subterra.engine.worldgen.pipeline.dimension.OverworldBounds;
import io.toterra.subterra.engine.worldgen.pipeline.router.NoiseRouter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The td-driven dimension-wiring table (p.1.8.19): the three primary worlds'
 * {@link DimensionSection}s (vertical windows + flags) plus a per-world
 * {@link DimensionPlan}, defaulted to the all-vanilla six sets so the default
 * pipeline stays bit-identical to 1.21.1. {@link #td()} emits the full vanilla
 * triple; {@link #fromTd(String)} accepts the same shape with optional per-world
 * overrides (partial bodies default to the pinned vanilla values). {@link
 * #materializeRouter(long, WorldDim)} deploys the p.1.8.12 overworld
 * {@link NoiseRouter} for the overworld and the p.1.8.20 nether / end routers for
 * the nether and end ({@code null} only for an unknown world). {@link
 * #validate()} health-checks the relations (positive coordinate scales, windows
 * within limits, plans present). Immutable, deterministic; construction and
 * parsing are linear in the number of fields (no O(n²)).
 * <p>
 * td 驱动的维度接线表（p.1.8.19）：三个主世界的 {@link DimensionSection}（纵向窗口+标志）
 * 加逐世界 {@link DimensionPlan}，默认全部为原生六件套以保证默认管线与 1.21.1 逐位一致。
 * {@link #td()} 输出完整原生三元组；{@link #fromTd(String)} 接受同形并允许逐世界可选覆盖
 * （部分主体回落到已钉定的原生值）。{@link #materializeRouter(long, WorldDim)} 为主世界部署
 * p.1.8.12 {@link NoiseRouter}，并对下界/末地分别部署 p.1.8.20 的 nether / end 路由器
 * （仅未知世界返回 {@code null}）。{@link #validate()} 做关系健康检查（正坐标尺度、窗口在限内、规划在场）。
 * 不可变、确定；构建与解析按字段数线性（无 O(n²)）。
 */
public final class DimensionWorlds {

    private final Map<WorldDim, DimensionSection> sections;
    private final Map<WorldDim, DimensionPlan> plans;

    /**
     * Full constructor requiring all three primary worlds present in
     * {@code sections}, each key matching its section's {@code worldDim}, with
     * plans defaulted to {@link DimensionPlan#defaultPlan()} where missing.
     * Unmodifiable, deterministic iteration.
     */
    public DimensionWorlds(Map<WorldDim, DimensionSection> sections, Map<WorldDim, DimensionPlan> plans) {
        if (sections == null) {
            throw new IllegalArgumentException("sections must not be null");
        }
        Map<WorldDim, DimensionSection> s = new LinkedHashMap<>(sections);
        for (WorldDim w : WorldDim.ALL) {
            DimensionSection sec = s.get(w);
            if (sec == null) {
                throw new IllegalArgumentException("missing dimension section: " + w.id());
            }
            if (sec.worldDim() != w) {
                throw new IllegalArgumentException(
                        "section key/worldDim mismatch for " + w.id() + ": " + sec.worldDim().id());
            }
        }
        Map<WorldDim, DimensionPlan> p = new LinkedHashMap<>();
        for (WorldDim w : WorldDim.ALL) {
            DimensionPlan plan = plans == null ? null : plans.get(w);
            p.put(w, plan == null ? DimensionPlan.defaultPlan() : plan);
        }
        this.sections = Collections.unmodifiableMap(s);
        this.plans = Collections.unmodifiableMap(p);
    }

    /** The default all-vanilla table: the three windows + flags + empty plans. */
    public static DimensionWorlds defaultWorlds() {
        Map<WorldDim, DimensionSection> s = new LinkedHashMap<>();
        Map<WorldDim, DimensionPlan> p = new LinkedHashMap<>();
        for (WorldDim w : WorldDim.ALL) {
            s.put(w, DimensionSection.vanilla(w));
            p.put(w, DimensionPlan.defaultPlan());
        }
        return new DimensionWorlds(s, p);
    }

    /** The current table as an unmodifiable map in canonical {@link WorldDim#ALL} order. */
    public Map<WorldDim, DimensionSection> sections() {
        return sections;
    }

    /** The per-world plans in canonical {@link WorldDim#ALL} order. */
    public Map<WorldDim, DimensionPlan> plans() {
        return plans;
    }

    /** The section for the given world (throws IllegalArgumentException when null). */
    public DimensionSection section(WorldDim wd) {
        if (wd == null) {
            throw new IllegalArgumentException("wd must not be null");
        }
        DimensionSection s = sections.get(wd);
        if (s == null) {
            throw new IllegalArgumentException("no section for " + wd.id());
        }
        return s;
    }

    /** The overworld section — the conventional entry point for the nine-dimension pipeline. */
    public DimensionSection overworld9n() {
        return section(WorldDim.OVERWORLD);
    }

    /** The vertical block-space window for the given world. */
    public OverworldBounds boundsFor(WorldDim wd) {
        return section(wd).window();
    }

    /** The algorithm plan for the given world (never null). */
    public DimensionPlan plan(WorldDim wd) {
        if (wd == null) {
            throw new IllegalArgumentException("wd must not be null");
        }
        DimensionPlan plan = plans.get(wd);
        return plan == null ? DimensionPlan.defaultPlan() : plan;
    }

    /**
     * The canonical td table-literal for the whole triple, exactly round-trippable
     * through {@link #fromTd(String)}. Each world appears as {@code id=[…]} with
     * its full section body; non-default plans are embedded as a nested
     * {@code plan=[…]} (default empty plans are omitted). Guarantees
     * {@code fromTd(td()).equals(this)} for the default (all-vanilla) table.
     */
    public String td() {
        StringBuilder sb = new StringBuilder("[");
        boolean first = true;
        for (WorldDim w : WorldDim.ALL) {
            DimensionSection sec = sections.get(w);
            if (sec == null) {
                continue;
            }
            if (!first) {
                sb.append(", ");
            }
            first = false;
            String body = sec.td();
            sb.append(w.id()).append('=').append(body);
            DimensionPlan plan = plans.get(w);
            if (plan != null && !plan.entries().isEmpty()) {
                String ptd = plan.td();
                sb.append(", plan=").append(ptd); // ptd already bracket-wrapped
            }
        }
        return sb.append(']').toString();
    }

    /**
     * Parses the {@link #td()} table-literal (or any equivalent with optional
     * per-world overrides). Unknown world ids, unknown section keys, malformed
     * values and any window rejected by {@link OverworldBounds} validation are
     * refused via IllegalArgumentException; omitted worlds / fields default to
     * the pinned vanilla values. Self-contained, linear in field count.
     */
    public static DimensionWorlds fromTd(String source) {
        if (source == null) {
            throw new IllegalArgumentException("td must not be null");
        }
        Tokens t = new Tokens(source);
        int depth = 0;
        while (t.peek('[')) {
            depth++;
            t.next();
        }
        Map<WorldDim, DimensionSection> s = new LinkedHashMap<>();
        Map<WorldDim, DimensionPlan> p = new LinkedHashMap<>();
        while (true) {
            if (t.done()) {
                if (depth != 0) {
                    throw new IllegalArgumentException("td: unterminated table: " + source);
                }
                break;
            }
            char c = t.src.charAt(t.pos);
            if (c == ']') {
                if (depth == 0) {
                    throw new IllegalArgumentException("td: unexpected ']': " + source);
                }
                depth--;
                t.next();
                continue;
            }
            if (c == ',') {
                t.next();
                continue;
            }
            String key = t.readKey();
            if (key == null) {
                throw new IllegalArgumentException("td: expected dimension key: " + source);
            }
            if (!t.consume('=')) {
                throw new IllegalArgumentException("td: expected '=' after " + key);
            }
            WorldDim wd = WorldDim.of(key);
            if (wd == null) {
                throw new IllegalArgumentException("td: unknown world dimension: " + key);
            }
            if (!t.peek('[')) {
                throw new IllegalArgumentException("td: expected '[' section for " + key + ": " + source);
            }
            String body = t.readBracketed();
            // Strip any nested `plan=[…]` item; pass the remaining fields to the section parser.
            StringBuilder fields = new StringBuilder();
            DimensionPlan plan = DimensionPlan.defaultPlan();
            boolean firstField = true;
            for (String item : splitTopLevel(body)) {
                int eq = item.indexOf('=');
                if (eq < 0) {
                    throw new IllegalArgumentException("td: malformed key=value pair: " + item);
                }
                String k = item.substring(0, eq).trim();
                if (k.equals("plan")) {
                    plan = DimensionPlan.fromTd(item.substring(eq + 1).trim());
                } else {
                    if (!firstField) {
                        fields.append(", ");
                    }
                    firstField = false;
                    fields.append(item);
                }
            }
            s.put(wd, DimensionSection.fromTd(wd, fields.toString()));
            p.put(wd, plan);
        }
        // Omitted worlds default to the pinned vanilla triple (optional overrides).
        for (WorldDim w : WorldDim.ALL) {
            if (!s.containsKey(w)) {
                s.put(w, DimensionSection.vanilla(w));
                p.putIfAbsent(w, DimensionPlan.defaultPlan());
            }
        }
        return new DimensionWorlds(s, p);
    }

    /**
     * Health check over the table's relations: returns an empty list when the
     * default table (or any consistent override) is healthy, else the list of
     * issues. Each world must be present; its coordinate scale must be strictly
     * positive; its window must satisfy the {@link OverworldBounds} invariant;
     * and a plan must be present.
     */
    public List<String> validate() {
        List<String> issues = new ArrayList<>();
        for (WorldDim w : WorldDim.ALL) {
            DimensionSection sec = sections.get(w);
            if (sec == null) {
                issues.add("missing section for " + w.id());
                continue;
            }
            if (!(sec.coordinateScale() > 0.0)) {
                issues.add(w.id() + " coordinate scale <= 0: " + sec.coordinateScale());
            }
            OverworldBounds win = sec.window();
            if (win == null) {
                issues.add(w.id() + " window is null");
                continue;
            }
            if (win.height() <= 0) {
                issues.add(w.id() + " window height <= 0: " + win.height());
            }
            if (win.seaLevel() < win.minY() || win.seaLevel() >= win.minY() + win.height()) {
                issues.add(w.id() + " sea level outside window: " + win.seaLevel());
            }
            if (win.buildLimit() > win.minY() + win.height() || win.buildLimit() < win.minY()) {
                issues.add(w.id() + " build limit outside window: " + win.buildLimit());
            }
            if (plans.get(w) == null) {
                issues.add(w.id() + " plan missing");
            }
        }
        return issues;
    }

    /**
     * Materialises the world's noise router at the given seed. The overworld builds
     * the p.1.8.12 {@link NoiseRouter} over the section's {@code [minY,
     * minY + height)} window (vanilla {@code [-64, 320)}); the nether and end build
     * the p.1.8.20 nether / end routers over their sections' windows (vanilla
     * {@code [0, 256)} each). Only an unknown / unsupported world returns {@code null}.
     *
     * @param seed the deterministic world seed
     * @param wd   the world to materialise (never null)
     * @return the world's router, or {@code null} for an unknown world
     */
    public NoiseRouter materializeRouter(long seed, WorldDim wd) {
        if (wd == null) {
            throw new IllegalArgumentException("wd must not be null");
        }
        OverworldBounds win = section(wd).window();
        int minY = win.minY();
        int maxY = minY + win.height();
        return switch (wd) {
            case OVERWORLD -> NoiseRouter.overworld(seed, minY, maxY);
            case THE_NETHER -> NoiseRouter.nether(seed, minY, maxY);
            case THE_END -> NoiseRouter.end(seed, minY, maxY);
        };
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof DimensionWorlds that)) {
            return false;
        }
        return sections.equals(that.sections) && plans.equals(that.plans);
    }

    @Override
    public int hashCode() {
        return 31 * sections.hashCode() + plans.hashCode();
    }

    @Override
    public String toString() {
        return "DimensionWorlds" + td();
    }

    /** Splits a body on top-level commas, skipping nested brackets and quoted strings. */
    static List<String> splitTopLevel(String body) {
        List<String> out = new ArrayList<>();
        int depth = 0;
        int start = 0;
        int i = 0;
        while (i < body.length()) {
            char c = body.charAt(i);
            if (c == '"') {
                i = skipString(body, i) + 1;
                continue;
            }
            if (c == '[') {
                depth++;
            } else if (c == ']') {
                depth--;
            } else if (c == ',' && depth == 0) {
                String item = body.substring(start, i).trim();
                if (!item.isEmpty()) {
                    out.add(item);
                }
                start = i + 1;
            }
            i++;
        }
        String last = body.substring(start).trim();
        if (!last.isEmpty()) {
            out.add(last);
        }
        return out;
    }

    private static int skipString(String s, int p) {
        int i = p + 1;
        while (i < s.length()) {
            char c = s.charAt(i);
            if (c == '\\') {
                i += 2;
                continue;
            }
            if (c == '"') {
                return i;
            }
            i++;
        }
        throw new IllegalArgumentException("td: unterminated string: " + s);
    }

    /** Minimal character scanner for the outer {@code id=[...]} table shape. */
    private static final class Tokens {
        private final String src;
        private int pos;

        Tokens(String src) {
            this.src = src;
        }

        void skip() {
            while (pos < src.length()) {
                char c = src.charAt(pos);
                if (c == ' ' || c == '\t' || c == '\n' || c == '\r') {
                    pos++;
                } else {
                    break;
                }
            }
        }

        boolean done() {
            skip();
            return pos >= src.length();
        }

        boolean peek(char c) {
            skip();
            return pos < src.length() && src.charAt(pos) == c;
        }

        void next() {
            if (pos < src.length()) {
                pos++;
            }
        }

        boolean consume(char c) {
            skip();
            if (pos < src.length() && src.charAt(pos) == c) {
                pos++;
                return true;
            }
            return false;
        }

        /** Reads a key up to any of {@code = , [ ], whitespace}. */
        String readKey() {
            skip();
            int start = pos;
            while (pos < src.length()) {
                char c = src.charAt(pos);
                if (c == '=' || c == ',' || c == '[' || c == ']'
                        || c == ' ' || c == '\t' || c == '\n' || c == '\r') {
                    break;
                }
                pos++;
            }
            return pos == start ? null : src.substring(start, pos);
        }

        /** Consumes a {@code [ … ]} group (bracket- and quote-aware) and returns its inner body. */
        String readBracketed() {
            skip();
            if (pos >= src.length() || src.charAt(pos) != '[') {
                throw new IllegalArgumentException("td: expected '['");
            }
            int start = pos + 1;
            int depth = 1;
            pos = start;
            while (pos < src.length()) {
                char c = src.charAt(pos);
                if (c == '"') {
                    pos = skipString(src, pos) + 1;
                    continue;
                }
                if (c == '[') {
                    depth++;
                } else if (c == ']') {
                    depth--;
                    if (depth == 0) {
                        String inner = src.substring(start, pos);
                        pos++;
                        return inner.trim();
                    }
                }
                pos++;
            }
            throw new IllegalArgumentException("td: unterminated '[': " + src);
        }
    }

    }