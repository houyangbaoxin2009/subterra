package io.toterra.subterra.engine.worldgen.feature;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.TreeSet;

import io.toterra.subterra.engine.worldgen.guard.StructureFootprint;

/**
 * p.2.29.3 世界生成「规则 → 特征/成矿」装配核心（纯 JDK、确定性）：把「矿物 / 植被 placed feature 按
 * 规则挂载」的规则侧收敛为<b>确定性挂载计划</b>（{@link Plan}）。规则载体为值语义
 * {@code Map<String,String>}（对齐 api.config.Rule 与 DatapackRules 双层语义：later-pack-wins →
 * save-override-wins 后的<b>有效</b>规则表），本核心只做纯解析、不读存储、不碰 MC。
 *
 * <p>规则键前缀 {@code subterra.worldgen.feature.*}（与 p.2.29.1 {@link io.toterra.subterra.engine.worldgen.assembly.DensityAssembly}
 * 同一体系）：
 * <ul>
 *   <li>{@link #RULE_ENABLE} —— 总开关，缺省 {@code false}（缺省预设恒等）；</li>
 *   <li>{@code subterra.worldgen.feature.mineral.<id>.<field>} —— 矿物组：{@code block}（所置矿石，
 *       必填）、{@code host_rock}（<b>母岩前置</b>：逗号分隔的方块 id 或 {@code #tag}，空=不限）、
 *       {@code vein_trend}（矿脉走向，缺省 {@code cluster}）、{@code count}（缺省 {@code 0}=不出）、
 *       {@code min_y}/{@code max_y}（缺省 {@code 0}/{@code 0}）；</li>
 *   <li>{@code subterra.worldgen.feature.vegetation.<id>.<field>} —— 植被组：{@code block}（必填）、
 *       {@code climate}（气候带前置，缺省空=不限）、{@code count}、{@code min_y}/{@code max_y}；</li>
 *   <li>{@code subterra.worldgen.feature.guard.box.<id>} —— <b>结构护栏</b>：
 *       {@code minX,minZ,sizeX,sizeZ[,clearance]} 的保护 footprint，矿脉原点落在其中即被拒（防重叠）；</li>
 *   <li>{@link #RULE_GUARD_CLEARANCE} —— 全局护栏余量（额外外扩，缺省 {@code 0}）。</li>
 * </ul>
 *
 * <p>确定性纪律：{@link #DEFAULT_PLAN} 常量固定；{@link #of(Map)} 对同一输入恒得同一输出（未知键忽略、
 * 缺失键取缺省，组内未知字段确定性拒绝）；字段序固定（组内 id 规范序）；{@link #render(Plan)} 产规范串
 * （同输入同字节）；非法值（非 true/false 布尔、越界 count/y、未知 vein_trend、空 block、坏 box）确定性
 * 拒绝（{@link IllegalArgumentException}）。缺省即<b>恒等</b>：{@link Plan#mounts()} 为空、
 * {@link Plan#blocked(int,int)} 恒 false。<b>零 json</b>：全部规则走 td（tie:data）。
 *
 * <p>护栏协同：{@link GuardBox#footprint()} 复用 {@link StructureFootprint} 几何（含 clearance 外扩），
 * {@link Plan#blocked(int,int)} 与 {@link #guardConflicts(Plan)} 与结构护栏同语义、重叠判定同源；矿脉
 * 不得越过护栏（越界原点确定性拒绝，不自行放宽）。
 *
 * <p>p.2.29.3 the "rules → feature/ore" worldgen assembly core (pure JDK, deterministic): converges the
 * rule side of "mineral / vegetation placed features mounted by rule" into a <b>deterministic mount
 * plan</b> ({@link Plan}). The rule carrier is the value-semantic {@code Map<String,String>} (aligned
 * with api.config.Rule and the DatapackRules two-tier semantics — the <b>effective</b> rule table after
 * later-pack-wins → save-override-wins); this core only parses, never reads storage, never touches MC.
 *
 * <p>Rule keys share the {@code subterra.worldgen.feature.*} family: a master {@link #RULE_ENABLE}
 * (default {@code false} = identity), a mineral group ({@code block}, the <b>host-rock prerequisite</b>
 * {@code host_rock}, {@code vein_trend}, {@code count}, {@code min_y}/{@code max_y}), a vegetation group
 * ({@code block}, {@code climate}, {@code count}, {@code min_y}/{@code max_y}), the <b>structure guard</b>
 * boxes and the global {@link #RULE_GUARD_CLEARANCE}. Deterministic: identical input → identical plan;
 * canonical field order; {@link #render(Plan)} is same-input-same-bytes; invalid values are
 * deterministically rejected (all-or-nothing); the default plan is identity (no mounts, nothing blocked).
 */
public final class FeatureAssembly {

    /** 总开关规则键：缺省 {@code false}（缺省预设恒等）。 /
     *  Master enable rule key: default {@code false} (identity on the default preset). */
    public static final String RULE_ENABLE = "subterra.worldgen.feature.enable";

    /** 全局结构护栏余量规则键：额外外扩方块数，缺省 {@code 0}。 /
     *  Global structure-guard clearance rule key: extra block inflation, default {@code 0}. */
    public static final String RULE_GUARD_CLEARANCE = "subterra.worldgen.feature.guard.clearance";

    /** 矿物组规则键前缀。 / The mineral-group rule-key prefix. */
    public static final String MINERAL_PREFIX = "subterra.worldgen.feature.mineral.";
    /** 植被组规则键前缀。 / The vegetation-group rule-key prefix. */
    public static final String VEGETATION_PREFIX = "subterra.worldgen.feature.vegetation.";
    /** 结构护栏 box 规则键前缀。 / The structure-guard box rule-key prefix. */
    public static final String GUARD_BOX_PREFIX = "subterra.worldgen.feature.guard.box.";

    /** 允许的矿脉走向 id，固定规范序（缺省 {@code cluster}）。 /
     *  The allowed vein-trend ids in fixed canonical order (default {@code cluster}). */
    public static final List<String> VEIN_TRENDS = List.of("cluster", "vertical", "horizontal", "diagonal");

    /** count 上限（含）。 / The inclusive count upper bound. */
    public static final int MAX_COUNT = 64;
    /** y 下界（含）。 / The inclusive y lower bound. */
    public static final int MIN_Y_FLOOR = -128;
    /** y 上界（含）。 / The inclusive y upper bound. */
    public static final int MIN_Y_CEIL = 512;

    /** 矿脉走向。 / The vein trend. */
    public enum VeinTrend {
        /** 团簇状（缺省，近似各向同性）。 / Cluster (default, roughly isotropic). */
        CLUSTER("cluster"),
        /** 竖直走向（细长竖脉）。 / Vertical (long thin vertical vein). */
        VERTICAL("vertical"),
        /** 水平走向（扁平横脉）。 / Horizontal (flat horizontal vein). */
        HORIZONTAL("horizontal"),
        /** 斜向走向。 / Diagonal. */
        DIAGONAL("diagonal");

        private final String id;

        VeinTrend(String id) {
            this.id = id;
        }

        /** 规则字面量。 / The rule literal. */
        public String id() {
            return id;
        }

        /**
         * 解析走向 id（严格、大小写敏感）。 / Parses a trend id (strict, case-sensitive).
         *
         * @throws IllegalArgumentException 未知 id 时 / on an unknown id
         */
        public static VeinTrend byId(String id) {
            for (VeinTrend t : values()) {
                if (t.id.equals(id)) {
                    return t;
                }
            }
            throw new IllegalArgumentException("unknown vein_trend '" + id + "' (allowed: " + VEIN_TRENDS + ")");
        }
    }

    /**
     * 母岩前置：某矿物仅在这些方块 / 方块标签上出矿。两列均规范序去重、不可变；两列皆空 = 不限母岩。
     * / The host-rock prerequisite: the mineral only originates on these blocks / block tags. Both
     * lists are canonically sorted, de-duplicated and immutable; both empty = any host rock.
     */
    public record HostRock(List<String> blocks, List<String> tags) {

        /** 规范排序 + 去重 + 不可变。 / Canonical sort + de-dup + immutable. */
        public HostRock {
            blocks = List.copyOf(new TreeSet<>(blocks == null ? List.of() : blocks));
            tags = List.copyOf(new TreeSet<>(tags == null ? List.of() : tags));
        }

        /** 不限母岩（两列皆空）。 / Any host rock (both lists empty). */
        public static HostRock any() {
            return new HostRock(List.of(), List.of());
        }

        /** 是否不限母岩。 / Whether any host rock is allowed. */
        public boolean isAny() {
            return blocks.isEmpty() && tags.isEmpty();
        }

        /**
         * 解析 {@code host_rock} 字面量（逗号分隔；{@code #ns:tag} 为标签，否则为方块 id；空白=不限）。
         * / Parses the {@code host_rock} literal (comma-separated; {@code #ns:tag} = a tag, otherwise a
         * block id; blank = any).
         */
        public static HostRock parse(String raw) {
            if (raw == null || raw.isBlank()) {
                return any();
            }
            List<String> blocks = new ArrayList<>();
            List<String> tags = new ArrayList<>();
            for (String part : raw.split(",")) {
                String p = part.trim();
                if (p.isEmpty()) {
                    continue;
                }
                if (p.startsWith("#")) {
                    String tag = p.substring(1).trim();
                    if (tag.isEmpty()) {
                        throw new IllegalArgumentException("host_rock '#tag' entry requires a tag id");
                    }
                    tags.add(tag);
                } else {
                    blocks.add(p);
                }
            }
            return new HostRock(blocks, tags);
        }

        /** 规范渲染（两列皆规范序）。 / Canonical render (both lists canonically ordered). */
        public String render() {
            List<String> all = new ArrayList<>(blocks);
            tags.forEach(t -> all.add("#" + t));
            return String.join(",", all);
        }
    }

    /**
     * 一条矿物规则（规范、不可变）。 / One mineral rule (canonical, immutable).
     *
     * @param id       矿物 id（规则路径段） / the mineral id (rule path segment)
     * @param block    所置矿石方块 id / the ore block id placed
     * @param hostRock 母岩前置 / the host-rock prerequisite
     * @param trend    矿脉走向 / the vein trend
     * @param count    每区块矿脉原点数（0 = 不出矿） / vein origins per chunk (0 = none)
     * @param minY     最低 y（含） / minimum y (inclusive)
     * @param maxY     最高 y（含） / maximum y (inclusive)
     */
    public record Mineral(String id, String block, HostRock hostRock, VeinTrend trend, int count, int minY, int maxY) {

        /** 不可为 null / 空。 / Never null / blank. */
        public Mineral {
            Objects.requireNonNull(id, "id must not be null");
            Objects.requireNonNull(block, "block must not be null");
            Objects.requireNonNull(hostRock, "hostRock must not be null");
            Objects.requireNonNull(trend, "trend must not be null");
            if (id.isBlank()) {
                throw new IllegalArgumentException("mineral id must not be blank");
            }
            if (block.isBlank()) {
                throw new IllegalArgumentException("mineral '" + id + "' requires a non-blank block id");
            }
            requireCount(id, count);
            requireY(id, minY, maxY);
        }

        /** 规范渲染。 / Canonical render. */
        public String render() {
            return "mineral." + id + " = { block=" + block + "; host_rock=" + hostRock.render()
                    + "; vein_trend=" + trend.id() + "; count=" + count + "; min_y=" + minY + "; max_y=" + maxY + " }";
        }
    }

    /**
     * 一条植被规则（规范、不可变）。 / One vegetation rule (canonical, immutable).
     *
     * @param id      植被 id / the vegetation id
     * @param block   所置植被方块 id / the vegetation block id placed
     * @param climate 气候带前置（空 = 不限） / the climate-band prerequisite (blank = any)
     * @param count   每区块数量（0 = 不出） / count per chunk (0 = none)
     * @param minY    最低 y（含） / minimum y (inclusive)
     * @param maxY    最高 y（含） / maximum y (inclusive)
     */
    public record Vegetation(String id, String block, String climate, int count, int minY, int maxY) {

        /** 不可为 null / 空。 / Never null / blank. */
        public Vegetation {
            Objects.requireNonNull(id, "id must not be null");
            Objects.requireNonNull(block, "block must not be null");
            if (climate == null) {
                climate = "";
            }
            if (id.isBlank()) {
                throw new IllegalArgumentException("vegetation id must not be blank");
            }
            if (block.isBlank()) {
                throw new IllegalArgumentException("vegetation '" + id + "' requires a non-blank block id");
            }
            requireCount(id, count);
            requireY(id, minY, maxY);
        }

        /** 规范渲染。 / Canonical render. */
        public String render() {
            return "vegetation." + id + " = { block=" + block + "; climate=" + climate + "; count=" + count
                    + "; min_y=" + minY + "; max_y=" + maxY + " }";
        }
    }

    /**
     * 一个结构护栏 footprint（二维）：矿脉原点落在其中即被拒（含 clearance 外扩）。 /
     * One structure-guard footprint (2D): a vein origin inside it is rejected (clearance-inflated).
     */
    public record GuardBox(String id, int minX, int minZ, int sizeX, int sizeZ, int clearance) {

        /** 不可为 null / 空；尺寸正、clearance ≥ 0。 / Never null / blank; positive size, clearance ≥ 0. */
        public GuardBox {
            Objects.requireNonNull(id, "id must not be null");
            if (id.isBlank()) {
                throw new IllegalArgumentException("guard box id must not be blank");
            }
            if (sizeX <= 0 || sizeZ <= 0) {
                throw new IllegalArgumentException("guard box '" + id + "' must have positive size");
            }
            if (clearance < 0) {
                throw new IllegalArgumentException("guard box '" + id + "' clearance must be >= 0");
            }
        }

        /** 复用结构护栏几何（{@link StructureFootprint}，含 clearance 外扩）。 /
         *  Reuses the structure-guard geometry ({@link StructureFootprint}, clearance-inflated). */
        public StructureFootprint footprint() {
            return new StructureFootprint(id, minX, minZ, sizeX, sizeZ, clearance);
        }

        /** 规范渲染。 / Canonical render. */
        public String render() {
            return "guard.box." + id + " = " + minX + ',' + minZ + ',' + sizeX + ',' + sizeZ + ',' + clearance;
        }
    }

    /**
     * 一条确定性挂载描述（placed feature 的规则侧）：矿物在前、植被在后，组内规范序。 /
     * One deterministic mount descriptor (the rule side of a placed feature): minerals first,
     * vegetation after, canonically ordered within each group.
     */
    public record Mount(String id, String group, String block, int count, int minY, int maxY) {

        /** 不可为 null；group ∈ {mineral, vegetation}。 / Never null; group ∈ {mineral, vegetation}. */
        public Mount {
            Objects.requireNonNull(id, "id must not be null");
            Objects.requireNonNull(group, "group must not be null");
            Objects.requireNonNull(block, "block must not be null");
        }

        /** 规范渲染。 / Canonical render. */
        public String render() {
            return group + ":" + id + " -> " + block + " (count=" + count + ", y=[" + minY + ',' + maxY + "])";
        }
    }

    /**
     * 确定性挂载计划（规范、不可变）：矿物与植被按 id 规范序，guard box 按 id 规范序。 /
     * The deterministic mount plan (canonical, immutable): minerals and vegetation sorted by id,
     * guard boxes sorted by id.
     *
     * @param enabled        总开关 / the master switch
     * @param minerals       矿物规则（规范序） / mineral rules (canonical order)
     * @param vegetation     植被规则（规范序） / vegetation rules (canonical order)
     * @param guardClearance 全局护栏余量 / the global guard clearance
     * @param guards         结构护栏 box（规范序） / structure-guard boxes (canonical order)
     */
    public record Plan(boolean enabled, List<Mineral> minerals, List<Vegetation> vegetation,
                       int guardClearance, List<GuardBox> guards) {

        /** 规范序 + 不可变。 / Canonical order + immutable. */
        public Plan {
            minerals = List.copyOf(minerals == null ? List.of() : minerals);
            vegetation = List.copyOf(vegetation == null ? List.of() : vegetation);
            guards = List.copyOf(guards == null ? List.of() : guards);
            if (guardClearance < 0) {
                throw new IllegalArgumentException("guard clearance must be >= 0, got " + guardClearance);
            }
        }

        /** 是否恒等（缺省预设零行为变化）：关闭、无挂载、无护栏、无余量。 /
         *  Whether identity (zero behaviour change on the default preset): off, no mounts, no guards,
         *  no clearance. */
        public boolean isIdentity() {
            return !enabled && minerals.isEmpty() && vegetation.isEmpty() && guards.isEmpty()
                    && guardClearance == 0;
        }

        /**
         * 确定性挂载列表：关闭 → 空；否则矿物（规范序）在前、植被（规范序）在后。 /
         * The deterministic mount list: empty when off; otherwise minerals (canonical order) first,
         * vegetation (canonical order) after.
         */
        public List<Mount> mounts() {
            if (!enabled) {
                return List.of();
            }
            List<Mount> out = new ArrayList<>(minerals.size() + vegetation.size());
            for (Mineral m : minerals) {
                out.add(new Mount(m.id(), "mineral", m.block(), m.count(), m.minY(), m.maxY()));
            }
            for (Vegetation v : vegetation) {
                out.add(new Mount(v.id(), "vegetation", v.block(), v.count(), v.minY(), v.maxY()));
            }
            return List.copyOf(out);
        }

        /** 按 id 取矿物规则。 / Looks up a mineral rule by id. */
        public Optional<Mineral> mineral(String id) {
            return minerals.stream().filter(m -> m.id().equals(id)).findFirst();
        }

        /** 按 id 取植被规则。 / Looks up a vegetation rule by id. */
        public Optional<Vegetation> vegetation(String id) {
            return vegetation.stream().filter(v -> v.id().equals(id)).findFirst();
        }

        /**
         * 该 (x,z) 是否被结构护栏拦住（含全局余量）：任一 guard box 的外扩 bounds 命中即 true。 /
         * Whether {@code (x,z)} is blocked by the structure guard (incl. global clearance): true when
         * any guard box's inflated bounds hit.
         */
        public boolean blocked(int x, int z) {
            for (GuardBox g : guards) {
                StructureFootprint f = g.footprint();
                if (x >= f.minX() - guardClearance && x <= f.maxX() + guardClearance
                        && z >= f.minZ() - guardClearance && z <= f.maxZ() + guardClearance) {
                    return true;
                }
            }
            return false;
        }
    }

    /** vanilla 等价缺省计划（关闭、空；恒等）。 / The vanilla-equivalent default plan (off, empty; identity). */
    public static final Plan DEFAULT_PLAN = new Plan(false, List.of(), List.of(), 0, List.of());

    private FeatureAssembly() {
    }

    /**
     * 解析有效规则表为规范 {@link Plan}；未知键忽略、缺失键取缺省，组内未知字段确定性拒绝；非法值
     * 整表确定性拒绝。纯函数且确定性：同表恒得同计划。 /
     * Parses an effective rule table into a canonical {@link Plan}; unknown keys are ignored, missing
     * keys take defaults, an unknown field inside a known group is deterministically rejected, and any
     * invalid value rejects the whole table. Pure and deterministic: the same table always yields the
     * same plan.
     *
     * @param rules 有效规则表（可为 null → 缺省） / the effective rule table (may be null → defaults)
     * @return 规范计划 / the canonical plan
     * @throws IllegalArgumentException 值非法时 / on an invalid value
     */
    public static Plan of(Map<String, String> rules) {
        Map<String, String> r = rules == null ? Map.of() : rules;
        boolean enabled = false;
        int guardClearance = 0;
        // deterministic accumulation: id -> field -> raw value (TreeMap gives a canonical id order)
        Map<String, Map<String, String>> mineralFields = new TreeMap<>();
        Map<String, Map<String, String>> vegetationFields = new TreeMap<>();
        Map<String, Map<String, String>> guardFields = new TreeMap<>();

        List<String> keys = new ArrayList<>();
        for (String k : r.keySet()) {
            if (k != null) {
                keys.add(k);
            }
        }
        keys.sort(null); // canonical, null-safe

        for (String k : keys) {
            String value = r.get(k);
            if (k.equals(RULE_ENABLE)) {
                enabled = parseBool(k, value);
            } else if (k.equals(RULE_GUARD_CLEARANCE)) {
                guardClearance = parseGuardClearance(value);
            } else if (k.startsWith(GUARD_BOX_PREFIX)) {
                splitField(guardFields, k, GUARD_BOX_PREFIX, value);
            } else if (k.startsWith(MINERAL_PREFIX)) {
                splitField(mineralFields, k, MINERAL_PREFIX, value);
            } else if (k.startsWith(VEGETATION_PREFIX)) {
                splitField(vegetationFields, k, VEGETATION_PREFIX, value);
            }
            // else: unknown top-level key ignored deterministically
        }

        List<Mineral> minerals = new ArrayList<>(mineralFields.size());
        for (Map.Entry<String, Map<String, String>> e : mineralFields.entrySet()) {
            minerals.add(buildMineral(e.getKey(), e.getValue()));
        }
        List<Vegetation> vegetation = new ArrayList<>(vegetationFields.size());
        for (Map.Entry<String, Map<String, String>> e : vegetationFields.entrySet()) {
            vegetation.add(buildVegetation(e.getKey(), e.getValue()));
        }
        List<GuardBox> guards = new ArrayList<>(guardFields.size());
        for (Map.Entry<String, Map<String, String>> e : guardFields.entrySet()) {
            guards.add(buildGuardBox(e.getKey(), e.getValue()));
        }
        return new Plan(enabled, minerals, vegetation, guardClearance, guards);
    }

    /** 计划的规范渲染（固定字段序；同计划同字节）。 / The canonical render (fixed field order; same plan → same bytes). */
    public static String render(Plan plan) {
        Objects.requireNonNull(plan, "plan must not be null");
        return "enable=" + plan.enabled() + "; guard_clearance=" + plan.guardClearance()
                + "; minerals=[" + join(plan.minerals().stream().map(Mineral::render).toList()) + ']'
                + "; vegetation=[" + join(plan.vegetation().stream().map(Vegetation::render).toList()) + ']'
                + "; guards=[" + join(plan.guards().stream().map(GuardBox::render).toList()) + ']';
    }

    /**
     * 结构护栏自检（工具用，确定性）：报告互相重叠的 guard box 对（{@code a x b}），规范序。 /
     * Structure-guard self-check (tooling, deterministic): reports mutually overlapping guard-box
     * pairs ({@code a x b}), canonically ordered.
     */
    public static List<String> guardConflicts(Plan plan) {
        Objects.requireNonNull(plan, "plan must not be null");
        List<GuardBox> gs = plan.guards();
        List<String> out = new ArrayList<>();
        for (int i = 0; i < gs.size(); i++) {
            for (int j = i + 1; j < gs.size(); j++) {
                if (StructureFootprint.overlaps(gs.get(i).footprint(), gs.get(j).footprint())) {
                    out.add(gs.get(i).id() + " x " + gs.get(j).id());
                }
            }
        }
        return List.copyOf(out);
    }

    // ---- internals ----

    /** 拆分 {@code <id>.<field>} 路径段并累计原值（缺 field → 拒绝）。 /
     *  Splits a {@code <id>.<field>} path and accumulates the raw value (missing field → reject). */
    private static void splitField(Map<String, Map<String, String>> sink, String fullKey, String prefix, String value) {
        String path = fullKey.substring(prefix.length());
        int dot = path.lastIndexOf('.');
        if (dot <= 0 || dot == path.length() - 1) {
            throw new IllegalArgumentException("rule '" + fullKey + "' must be of shape <prefix><id>.<field>");
        }
        String id = path.substring(0, dot);
        String field = path.substring(dot + 1);
        Map<String, String> fields = sink.computeIfAbsent(id, k -> new LinkedHashMap<>());
        if (fields.containsKey(field)) {
            throw new IllegalArgumentException("duplicate field '" + field + "' for '" + id + "'");
        }
        fields.put(field, value == null ? "" : value);
    }

    /** 由字段表构建矿物规则（未知字段拒绝；block 必填；其余取缺省）。 / Builds a mineral rule from its fields. */
    private static Mineral buildMineral(String id, Map<String, String> f) {
        String block = null;
        HostRock hostRock = HostRock.any();
        VeinTrend trend = VeinTrend.CLUSTER;
        int count = 0;
        int minY = 0;
        int maxY = 0;
        for (Map.Entry<String, String> e : f.entrySet()) {
            String key = MINERAL_PREFIX + id + "." + e.getKey();
            switch (e.getKey()) {
                case "block" -> block = requireRaw(key, e.getValue());
                case "host_rock" -> hostRock = HostRock.parse(e.getValue());
                case "vein_trend" -> trend = VeinTrend.byId(requireRaw(key, e.getValue()));
                case "count" -> count = parseInt(key, e.getValue());
                case "min_y" -> minY = parseInt(key, e.getValue());
                case "max_y" -> maxY = parseInt(key, e.getValue());
                default -> throw new IllegalArgumentException("unknown mineral field in rule '" + key + "'");
            }
        }
        if (block == null) {
            throw new IllegalArgumentException("mineral '" + id + "' requires a block field");
        }
        return new Mineral(id, block, hostRock, trend, count, minY, maxY);
    }

    /** 由字段表构建植被规则（未知字段拒绝；block 必填；其余取缺省）。 / Builds a vegetation rule from its fields. */
    private static Vegetation buildVegetation(String id, Map<String, String> f) {
        String block = null;
        String climate = "";
        int count = 0;
        int minY = 0;
        int maxY = 0;
        for (Map.Entry<String, String> e : f.entrySet()) {
            String key = VEGETATION_PREFIX + id + "." + e.getKey();
            switch (e.getKey()) {
                case "block" -> block = requireRaw(key, e.getValue());
                case "climate" -> climate = e.getValue() == null ? "" : e.getValue().trim();
                case "count" -> count = parseInt(key, e.getValue());
                case "min_y" -> minY = parseInt(key, e.getValue());
                case "max_y" -> maxY = parseInt(key, e.getValue());
                default -> throw new IllegalArgumentException("unknown vegetation field in rule '" + key + "'");
            }
        }
        if (block == null) {
            throw new IllegalArgumentException("vegetation '" + id + "' requires a block field");
        }
        return new Vegetation(id, block, climate, count, minY, maxY);
    }

    /** 由字段表构建结构护栏 box（未知字段拒绝；box 必填）。 / Builds a guard box from its fields. */
    private static GuardBox buildGuardBox(String id, Map<String, String> f) {
        String box = null;
        for (Map.Entry<String, String> e : f.entrySet()) {
            String key = GUARD_BOX_PREFIX + id + "." + e.getKey();
            if ("box".equals(e.getKey())) {
                box = e.getValue();
            } else {
                throw new IllegalArgumentException("unknown guard box field in rule '" + key + "'");
            }
        }
        if (box == null) {
            throw new IllegalArgumentException("guard box '" + id + "' requires a box field");
        }
        return parseBox(id, box);
    }

    /** 解析 {@code minX,minZ,sizeX,sizeZ[,clearance]}。 / Parses {@code minX,minZ,sizeX,sizeZ[,clearance]}. */
    private static GuardBox parseBox(String id, String raw) {
        String[] parts = raw.trim().split(",");
        if (parts.length != 4 && parts.length != 5) {
            throw new IllegalArgumentException("guard box '" + id + "' box must be 'minX,minZ,sizeX,sizeZ[,clearance]'");
        }
        int[] vals = new int[parts.length];
        for (int i = 0; i < parts.length; i++) {
            vals[i] = parseInt(GUARD_BOX_PREFIX + id + ".box", parts[i]);
        }
        int clearance = parts.length == 5 ? vals[4] : 0;
        return new GuardBox(id, vals[0], vals[1], vals[2], vals[3], clearance);
    }

    private static boolean parseBool(String key, String raw) {
        if (raw == null) {
            throw new IllegalArgumentException("rule '" + key + "' requires true/false, got null");
        }
        String v = raw.trim();
        if ("true".equals(v)) {
            return true;
        }
        if ("false".equals(v)) {
            return false;
        }
        throw new IllegalArgumentException("rule '" + key + "' requires true/false, got '" + raw + "'");
    }

    private static int parseGuardClearance(String raw) {
        int v = parseInt(RULE_GUARD_CLEARANCE, raw);
        if (v < 0) {
            throw new IllegalArgumentException("rule '" + RULE_GUARD_CLEARANCE + "' must be >= 0, got " + v);
        }
        return v;
    }

    private static int parseInt(String key, String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("rule '" + key + "' requires an integer, got <blank>");
        }
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("rule '" + key + "' requires an integer, got '" + raw + "'", e);
        }
    }

    private static void requireCount(String id, int count) {
        if (count < 0 || count > MAX_COUNT) {
            throw new IllegalArgumentException("'" + id + "' count must be in [0, " + MAX_COUNT + "], got " + count);
        }
    }

    private static void requireY(String id, int minY, int maxY) {
        if (minY < MIN_Y_FLOOR || minY > MIN_Y_CEIL || maxY < MIN_Y_FLOOR || maxY > MIN_Y_CEIL) {
            throw new IllegalArgumentException("'" + id + "' y must be in [" + MIN_Y_FLOOR + ", " + MIN_Y_CEIL
                    + "], got [" + minY + ", " + maxY + "]");
        }
        if (minY > maxY) {
            throw new IllegalArgumentException("'" + id + "' min_y must be <= max_y, got [" + minY + ", " + maxY + "]");
        }
    }

    private static String requireRaw(String key, String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("rule '" + key + "' requires a non-blank value");
        }
        return raw.trim();
    }

    private static String join(List<String> parts) {
        return String.join("; ", parts);
    }
}
