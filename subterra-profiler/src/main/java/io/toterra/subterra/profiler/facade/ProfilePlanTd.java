package io.toterra.subterra.profiler.facade;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import io.toterra.subterra.api.worldgen.profiler.ProfileAxis;
import io.toterra.subterra.api.worldgen.profiler.ProfileCategory;
import io.toterra.subterra.api.worldgen.profiler.ProfileFormat;
import io.toterra.subterra.api.worldgen.profiler.ProfilePlan;
import io.toterra.subterra.api.worldgen.profiler.ProfileSink;
import io.toterra.subterra.api.worldgen.profiler.ProfileSlice;
import io.toterra.subterra.api.worldgen.profiler.ProfileStep;
import io.toterra.subterra.api.worldgen.profiler.ProfileWindow;
import io.toterra.subterra.api.worldgen.profiler.SliceUnit;
import io.toterra.subterra.engine.config.Td;
import io.toterra.subterra.engine.config.TdTable;
import io.toterra.subterra.engine.config.TdValue;

/**
 * td serialization and parsing for a {@link ProfilePlan} (p.1.8.30 "World
 * Profiler"), using the {@code subterra-config} td model. The td shape follows
 * design document section 4.1; parsing is tolerant, filling every missing key
 * with its documented default, but rejects unknown keys and unknown enum values
 * with a located {@link IllegalArgumentException}. {@code toTd} and {@code
 * fromTd} are symmetric: writing a plan then parsing the result restores it.
 * <p>
 * {@link ProfilePlan} 的 td 序列化与解析（p.1.8.30 "World Profiler"），使用 {@code
 * subterra-config} 的 td 模型。td 形状遵照设计文档第 4.1 节；解析宽容，缺省键均按文档默认
 * 补足，但对未知键与未知枚举值以带定位信息的 {@link IllegalArgumentException} 拒绝。
 * {@code toTd} 与 {@code fromTd} 对称：写出计划后再解析其结果即可还原原样。
 * <p>
 * Defaults (per section 4.1): residency {@code false}; window center {@code
 * [0,0]} radius {@code 8}; categories all four; step {@code xz=2, y=16};
 * slice absent (null); format {@code ["zd"]}; sink {@code "run"}.
 * <p>
 * 缺省值（按第 4.1 节）：residency {@code false}；window center {@code [0,0]} radius
 * {@code 8}；categories 四类全开；step {@code xz=2, y=16}；slice 缺省（null）；format
 * {@code ["zd"]}；sink {@code "run"}。
 */
public final class ProfilePlanTd {

    private ProfilePlanTd() {
    }

    /**
     * Serializes a plan to its td table. The slice, when present, is written in
     * {@code axis/start/length/unit/planes/positions} form; because a {@code
     * ProfileSlice} stores only start/end, {@code length} is always
     * {@code end - start} and no fallback to an explicit end is needed.
     * <p>
     * 把计划序列化为 td 表。切片存在时以 {@code axis/start/length/unit/planes/positions}
     * 形式写出；因 {@code ProfileSlice} 只存 start/end，故 {@code length} 恒等于
     * {@code end - start}，无需回退显式 end。
     *
     * @param p the plan.
     * @return the td table.
     */
    public static TdTable toTd(ProfilePlan p) {
        TdTable.Builder b = TdTable.builder();
        b.put("residency", TdValue.of(p.residency()));

        TdTable.Builder center = TdTable.builder();
        center.element(TdValue.of((long) p.window().centerX()));
        center.element(TdValue.of((long) p.window().centerZ()));
        TdTable.Builder window = TdTable.builder();
        window.put("center", center.build());
        window.put("radius", TdValue.of((long) p.window().radiusChunks()));
        b.put("window", window.build());

        TdTable.Builder cats = TdTable.builder();
        for (ProfileCategory c : sortedCategories(p.categories())) {
            cats.element(TdValue.str(catLower(c.name())));
        }
        b.put("categories", cats.build());

        TdTable.Builder step = TdTable.builder();
        step.put("xz", TdValue.of((long) p.step().xz()));
        step.put("y", TdValue.of((long) p.step().y()));
        b.put("step", step.build());

        if (p.slice() != null) {
            ProfileSlice s = p.slice();
            TdTable.Builder box = TdTable.builder();
            box.put("axis", axisLower(s.axis().name()));
            box.put("start", TdValue.of((long) s.start()));
            box.put("length", TdValue.of((long) (s.end() - s.start())));
            box.put("unit", unitLower(s.unit().name()));
            box.put("planes", TdValue.of(s.planes()));
            box.put("positions", TdValue.of(s.positions()));
            b.put("slice", box.build());
        }

        TdTable.Builder fmts = TdTable.builder();
        for (ProfileFormat f : p.formats()) {
            fmts.element(TdValue.str(formatLower(f.name())));
        }
        b.put("format", fmts.build());
        b.put("sink", sinkLower(p.sink().name()));
        return b.build();
    }

    /**
     * Parses a plan table, applying defaults for missing keys and rejecting
     * unknown keys, unknown enum values and structural problems with located
     * messages.
     * <p>
     * 解析计划表，对缺省键应用默认值，并以带定位的消息拒绝未知键、未知枚举值与结构问题。
     *
     * @param t the td table.
     * @return the parsed plan.
     */
    public static ProfilePlan fromTd(TdTable t) {
        if (t == null) {
            throw new IllegalArgumentException("plan table must not be null");
        }
        rejectUnknown(t, "plan", Set.of("residency", "window", "categories",
            "step", "slice", "format", "sink"));

        boolean residency = optBool(t, "residency", false);
        ProfileWindow window = parseWindow(t.get("window"));
        Set<ProfileCategory> categories = parseCategories(t.get("categories"));
        ProfileStep step = parseStep(t.get("step"));
        ProfileSlice slice = parseSlice(t.get("slice"));
        List<ProfileFormat> formats = parseFormats(t.get("format"));
        ProfileSink sink = parseSink(t.get("sink"));

        return new ProfilePlan(residency, window, categories, step, slice, formats, sink);
    }

    /**
     * Parses a td source string directly: strips the optional header/table name
     * via {@link Td#parse}, then delegates to {@link #fromTd(TdTable)}.
     * <p>
     * 直接解析 td 源字符串：先经 {@link Td#parse} 去除可选头部/表名，再委托给
     * {@link #fromTd(TdTable)}。
     *
     * @param tdText the td document.
     * @return the parsed plan.
     */
    public static ProfilePlan fromSource(String tdText) {
        TdTable t = Td.parse(tdText);
        return fromTd(t);
    }

    private static ProfileWindow parseWindow(TdValue v) {
        if (v == null) {
            return new ProfileWindow(0, 0, 8);
        }
        TdTable t = table(v, "window");
        rejectUnknown(t, "window", Set.of("center", "radius"));
        TdTable center = fieldTable(t.get("center"), "window.center");
        int cx = center != null ? intAt(center, 0, "window.center[0]") : 0;
        int cz = center != null ? intAt(center, 1, "window.center[1]") : 0;
        return new ProfileWindow(cx, cz, radius(t));
    }

    /** Returns the field as a table, or null when the value is absent. */
    private static TdTable fieldTable(TdValue v, String path) {
        if (v == null) {
            return null;
        }
        return table(v, path);
    }

    private static int intAt(TdTable t, int index, String path) {
        List<TdValue> els = t.elements();
        if (index >= els.size()) {
            throw new IllegalArgumentException(path + " missing");
        }
        return intVal(els.get(index), path);
    }

    private static int radius(TdTable t) {
        TdValue r = t.get("radius");
        if (r == null) {
            return 8;
        }
        long v = scalar(r).asInt();
        if (v < 0) {
            throw new IllegalArgumentException("window.radius must be >= 0");
        }
        return (int) v;
    }

    private static Set<ProfileCategory> parseCategories(TdValue v) {
        if (v == null) {
            return EnumSet.allOf(ProfileCategory.class);
        }
        TdTable t = table(v, "categories");
        Set<ProfileCategory> out = EnumSet.noneOf(ProfileCategory.class);
        List<TdValue> els = t.elements();
        for (int i = 0; i < els.size(); i++) {
            String s = stringVal(els.get(i), "categories[" + i + "]");
            out.add(category(s, "categories[" + i + "]=" + s));
        }
        return out;
    }

    private static ProfileStep parseStep(TdValue v) {
        if (v == null) {
            return new ProfileStep(2, 16);
        }
        TdTable t = table(v, "step");
        rejectUnknown(t, "step", Set.of("xz", "y"));
        int xz = intVal(opt(t, "xz", 2), "step.xz");
        int y = intVal(opt(t, "y", 16), "step.y");
        return new ProfileStep(xz, y);
    }

    private static ProfileSlice parseSlice(TdValue v) {
        if (v == null) {
            return null;
        }
        TdTable t = table(v, "slice");
        rejectUnknown(t, "slice", Set.of("axis", "start", "length", "end",
            "unit", "planes", "positions"));
        String axisS = requiredString(t, "axis", "slice.axis");
        ProfileAxis axis = axis(axisS, "slice.axis=" + axisS);
        int start = intVal(opt(t, "start", 0), "slice.start");
        int end;
        if (t.get("length") != null && t.get("end") != null) {
            throw new IllegalArgumentException("slice must provide only one of length/end");
        }
        if (t.get("length") != null) {
            long len = scalar(t.get("length")).asInt();
            end = (int) (start + len);
        } else if (t.get("end") != null) {
            end = intVal(t.get("end"), "slice.end");
        } else {
            throw new IllegalArgumentException("slice requires length or end");
        }
        String unitS = requiredString(t, "unit", "slice.unit");
        SliceUnit unit = unit(unitS, "slice.unit=" + unitS);
        boolean planes = optBool(t, "planes", false);
        boolean positions = optBool(t, "positions", false);
        return new ProfileSlice(axis, start, end, unit, planes, positions);
    }

    private static List<ProfileFormat> parseFormats(TdValue v) {
        if (v == null) {
            return List.of(ProfileFormat.ZD);
        }
        TdTable t = table(v, "format");
        List<ProfileFormat> out = new ArrayList<>();
        List<TdValue> els = t.elements();
        for (int i = 0; i < els.size(); i++) {
            String s = stringVal(els.get(i), "format[" + i + "]");
            out.add(format(s, "format[" + i + "]=" + s));
        }
        return out;
    }

    private static ProfileSink parseSink(TdValue v) {
        if (v == null) {
            return ProfileSink.RUN;
        }
        String s = stringVal(v, "sink");
        return sink(s, "sink=" + s);
    }

    // ---------- value coercions ----------

    /** Reads v as a table, else throws a located error. */
    private static TdTable table(TdValue v, String path) {
        if (v instanceof TdTable t) {
            return t;
        }
        throw new IllegalArgumentException(path + " must be a table");
    }

    private static TdValue.Scalar scalar(TdValue v) {
        if (v instanceof TdValue.Scalar s) {
            return s;
        }
        throw new IllegalArgumentException("expected a scalar value");
    }

    private static String stringVal(TdValue v, String path) {
        TdValue.Scalar s = scalar(v);
        if (s.kind() != TdValue.Kind.STRING) {
            throw new IllegalArgumentException(path + " must be a string");
        }
        return s.str();
    }

    private static int intVal(TdValue v, String path) {
        return (int) scalar(v).asInt();
    }

    private static boolean optBool(TdTable t, String key, boolean def) {
        TdValue v = t.get(key);
        return v == null ? def : scalar(v).asBool();
    }

    /** Returns the int field or a default value when absent. */
    private static TdValue opt(TdTable t, String key, long def) {
        TdValue v = t.get(key);
        return v != null ? v : TdValue.of(def);
    }

    private static String requiredString(TdTable t, String key, String path) {
        TdValue v = t.get(key);
        if (v == null) {
            throw new IllegalArgumentException(path + " is required");
        }
        return stringVal(v, path);
    }

    private static void rejectUnknown(TdTable t, String path, Set<String> known) {
        for (String k : t.keys()) {
            if (!known.contains(k)) {
                throw new IllegalArgumentException("unknown key " + path + "." + k);
            }
        }
    }

    // ---------- enum coercion ----------

    private static ProfileCategory category(String s, String path) {
        return switch (s) {
            case "terrain" -> ProfileCategory.TERRAIN;
            case "blocks" -> ProfileCategory.BLOCKS;
            case "caves" -> ProfileCategory.CAVES;
            case "biome" -> ProfileCategory.BIOME;
            default -> throw new IllegalArgumentException("unknown category " + path);
        };
    }

    private static ProfileAxis axis(String s, String path) {
        return switch (s) {
            case "x" -> ProfileAxis.X;
            case "y" -> ProfileAxis.Y;
            case "z" -> ProfileAxis.Z;
            default -> throw new IllegalArgumentException("unknown axis " + path);
        };
    }

    private static SliceUnit unit(String s, String path) {
        return switch (s) {
            case "block" -> SliceUnit.BLOCK;
            case "chunk" -> SliceUnit.CHUNK;
            default -> throw new IllegalArgumentException("unknown unit " + path);
        };
    }

    private static ProfileFormat format(String s, String path) {
        return switch (s) {
            case "zd" -> ProfileFormat.ZD;
            case "td" -> ProfileFormat.TD;
            default -> throw new IllegalArgumentException("unknown format " + path);
        };
    }

    private static ProfileSink sink(String s, String path) {
        return switch (s) {
            case "run" -> ProfileSink.RUN;
            case "world" -> ProfileSink.WORLD;
            case "none" -> ProfileSink.NONE;
            default -> throw new IllegalArgumentException("unknown sink " + path);
        };
    }

    private static List<ProfileCategory> sortedCategories(Set<ProfileCategory> set) {
        List<ProfileCategory> l = new ArrayList<>(set);
        l.sort(java.util.Comparator.comparing(Enum::name));
        return l;
    }

    private static String lower(String s) {
        return s.toLowerCase(Locale.ROOT);
    }

    private static String catLower(String s) {
        return lower(s);
    }

    private static String formatLower(String s) {
        return lower(s);
    }

    private static String sinkLower(String s) {
        return lower(s);
    }

    private static String axisLower(String s) {
        return lower(s);
    }

    private static String unitLower(String s) {
        return lower(s);
    }
}