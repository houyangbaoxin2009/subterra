// p.2.18.6: deterministic export-hub probe — the p.2.18.1..4 engine.export surface
// (ExportKind enum order / ExportHub registry determinism / the four deterministic
// exporters LanguageKeys+Config+Registries+MigrateMaps / the p.2.18.1 archive
// producers Save+Datapack+WorldPack) bridged to the api.export.ExporterRegistry
// built-in forms. Pure JVM: no MC runtime, no wall-clock, no timestamps, no random.
package io.toterra.subterra.probes;

import io.toterra.subterra.api.export.ExportKindSpec;
import io.toterra.subterra.api.export.ExportPortSpec;
import io.toterra.subterra.api.export.ExporterRegistry;
import io.toterra.subterra.engine.config.TdTable;
import io.toterra.subterra.engine.config.TdValue;
import io.toterra.subterra.engine.datapack.Datapack;
import io.toterra.subterra.engine.datapack.DatapackExportArchive;
import io.toterra.subterra.engine.export.ConfigProducer;
import io.toterra.subterra.engine.export.DatapackArchiveProducer;
import io.toterra.subterra.engine.export.ExportHub;
import io.toterra.subterra.engine.export.ExportKind;
import io.toterra.subterra.engine.export.ExportPort;
import io.toterra.subterra.engine.export.ExportProducer;
import io.toterra.subterra.engine.export.LanguageKeysExporter;
import io.toterra.subterra.engine.export.LanguageKeysProducer;
import io.toterra.subterra.engine.export.MigrateMapsProducer;
import io.toterra.subterra.engine.export.RegistriesProducer;
import io.toterra.subterra.engine.export.SaveArchiveProducer;
import io.toterra.subterra.engine.export.WorldPackProducer;
import io.toterra.subterra.engine.save.SaveContainer;
import io.toterra.subterra.engine.save.SaveSlot;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * p.2.18.6 确定性导出枢纽探针 —— 把 p.2.18.1..4 的 engine.export 表面（{@link ExportKind} 枚举
 * 序 / {@link ExportHub} 注册表确定性 / 四个确定性导出器 LanguageKeys+Config+Registries+
 * MigrateMaps / p.2.18.1 的归档生产者 Save+Datapack+WorldPack）桥接到 api.export
 * {@link ExporterRegistry} 内建形态，纯 JVM 全量断言。五组校验：
 * <ol>
 *   <li><b>七 form 齐全</b>：engine {@link ExportKind} 枚举序与 {@code form()} 锚定
 *       [world, save, datapack, language_keys, config, registries, migrate_maps]；
 *       api {@code ExporterRegistry} 内建 world/save/datapack 与 engine 前三个 form 逐一相等。</li>
 *   <li><b>注册表确定性</b>：{@link ExportHub} 从空表起步；{@code preload()} → [world, save]；
 *       重复端口注册 IAE；{@code preloadFormal()} → 七 form 枚举序；二次调用幂等 no-op；
 *       未注册 producer() 返回 null；七个生产者全部注册后 producer() 逐 kind 命中、tdType 与
 *       form 一致；同 kind 生产者二次注册 IAE；tdType/form 不匹配 IAE；注册不改变 forms() 序。</li>
 *   <li><b>确定性导出器恒等</b>：对 LanguageKeys / Config / Registries / MigrateMaps 四导出器与
 *       Save / Datapack 两归档生产者构造确定性样例源物（固定键序、含转义文本），断言
 *       exportTd 连跑两遍逐字节相等（确定性）、exportTd∘rehydrateTd∘exportTd 逐字节恒等、
 *       exportZd 连跑两遍逐字节相等、exportZd∘rehydrateZd∘exportZd 逐字节恒等（Arrays.equals）。
 *       WorldPack 生产者需真实数据包目录（DatapackPack 走文件树），其逐字节恒等由 p.2.9.4
 *       WorldPackProbe 断言；此处只断言其 tdType() 与 ExportHub 注册状态（如实说明）。</li>
 *   <li><b>双格式一致性</b>：经 {@link ExportHub#zdOf}/{@link ExportHub#tdOf} 转换路径，
 *       zdOf(tdOf(zd)) 与源 zd 逐字节一致（内容树往返；反向文本形态不承诺——Td.parse 剥离
 *       可选顶层表名，zd 只携带内容树，属设计使然）。</li>
 *   <li><b>样例核对</b>：LanguageKeysExporter 对固定 3 键样例（乱序输入验证字典序）的 td 输出
 *       与硬编码期望文本逐字符比较——文档形态冻结锚点。</li>
 * </ol>
 * 每项失败计数 +1 并给出明确诊断；全过才输出 {@code [ExportHubProbe] PASS (n checks)} 并
 * exit 0，否则 FAIL 计数 exit 1。确定性纪律：固定序、无时序、无随机；全部线性遍历（禁
 * O(n²)）；失败计数只在失败路径自增。纯 JVM——绝不触碰 Minecraft 类。
 *
 * <p>p.2.18.6 deterministic export-hub probe — bridges the p.2.18.1..4 engine.export
 * surface ({@link ExportKind} enum order / {@link ExportHub} registry determinism / the
 * four deterministic exporters LanguageKeys+Config+Registries+MigrateMaps / the
 * p.2.18.1 archive producers Save+Datapack+WorldPack) to the api.export
 * {@link ExporterRegistry} built-in forms, all asserted in pure JVM. Five groups:
 * <ol>
 *   <li><b>seven forms complete</b>: engine {@link ExportKind} enum order and
 *       {@code form()} anchored as [world, save, datapack, language_keys, config,
 *       registries, migrate_maps]; the api {@code ExporterRegistry} built-in
 *       world/save/datapack forms equal the engine first three forms one by one.</li>
 *   <li><b>registry determinism</b>: {@link ExportHub} starts empty; {@code preload()}
 *       → [world, save]; a duplicate port registration is an IAE;
 *       {@code preloadFormal()} → the seven enum forms; a second call is an idempotent
 *       no-op; producer() for an unregistered kind returns null; after all seven
 *       producers are registered producer() hits per kind with tdType == form; a
 *       duplicate producer registration is an IAE; a tdType/form mismatch is an IAE;
 *       registrations never change the forms() order.</li>
 *   <li><b>deterministic exporter identity</b>: for the four exporters LanguageKeys /
 *       Config / Registries / MigrateMaps and the two archive producers Save /
 *       Datapack, deterministic sample sources (fixed key order, escaped texts) are
 *       asserted: exportTd twice byte-identical (determinism), exportTd∘rehydrateTd∘
 *       exportTd byte-identical, exportZd twice byte-identical, and exportZd∘rehydrateZd∘
 *       exportZd byte-identical (Arrays.equals). The WorldPack producer needs a live
 *       datapack directory (DatapackPack walks the file tree), so its byte identity is
 *       asserted by the p.2.9.4 WorldPackProbe; here only its tdType() and ExportHub
 *       registration state are asserted (stated honestly).</li>
 *   <li><b>dual-format consistency</b>: through the {@link ExportHub#zdOf}/
 *       {@link ExportHub#tdOf} conversion path, zdOf(tdOf(zd)) is byte-identical to the
 *       source zd (content-tree round-trip; the reverse textual form is not promised —
 *       Td.parse strips the optional top-level name, so zd carries the content tree only,
 *       by design).</li>
 *   <li><b>sample anchor</b>: the LanguageKeysExporter td output for a fixed 3-key
 *       sample (shuffled input to prove lexicographic sorting) is compared char-by-char
 *       against a hardcoded expected text — the frozen document-shape anchor.</li>
 * </ol>
 * Every failure is counted and diagnosed; PASS only when all checks pass, then exit 0,
 * else FAIL with counts and exit 1. Determinism discipline: fixed order, no timing, no
 * randomness; all traversals linear (no O(n²)); failures are counted only on failing
 * paths. Pure JVM — never touches Minecraft classes.
 */
public final class ExportHubProbe {

    private static int checks = 0;
    private static int failures = 0;

    private ExportHubProbe() {
    }

    public static void main(String[] args) {
        try {
            sevenForms();
            LanguageKeysProducer lang = new LanguageKeysProducer(langKeys());
            ConfigProducer config = new ConfigProducer(configGlobal(), configOverrides());
            RegistriesProducer regs = new RegistriesProducer(registriesSample());
            MigrateMapsProducer migrate = new MigrateMapsProducer(migrateSample());
            SaveArchiveProducer save = new SaveArchiveProducer(saveSample());
            DatapackArchiveProducer datapack = new DatapackArchiveProducer(datapackSample());
            WorldPackProducer world = new WorldPackProducer(new SaveContainer(), Path.of("unused"), null);
            registryDeterminism(lang, config, regs, migrate, save, datapack, world);
            identityExporters(lang, config, regs, migrate, save, datapack, world);
            dualFormatConsistency(lang);
            sampleAnchor();
        } catch (Exception e) {
            failures++;
            System.out.println("[FAIL] probe exception: " + e);
            e.printStackTrace(System.out);
        }
        if (failures == 0) {
            System.out.println("[ExportHubProbe] PASS (" + checks + " checks)");
            System.exit(0);
        } else {
            System.out.println("[ExportHubProbe] FAIL (" + failures + " of " + checks + " checks)");
            System.exit(1);
        }
    }

    // ---------- (1) seven forms complete ----------

    private static void sevenForms() {
        String[] expected = {"world", "save", "datapack", "language_keys", "config", "registries", "migrate_maps"};
        boolean orderOk = ExportKind.values().length == expected.length;
        for (int i = 0; orderOk && i < expected.length; i++) {
            orderOk = ExportKind.values()[i].form().equals(expected[i]);
        }
        check("ExportKind enum order + form() == [world,save,datapack,language_keys,config,registries,migrate_maps]",
                orderOk);

        // api.export.ExporterRegistry built-ins (fixed registration order) vs engine first three forms
        ExporterRegistry.clear();
        ExporterRegistry.register(ExportKindSpec.worldPack(), port(ExportKindSpec.worldPack()));
        ExporterRegistry.register(ExportKindSpec.save(), port(ExportKindSpec.save()));
        ExporterRegistry.register(ExportKindSpec.datapack(), port(ExportKindSpec.datapack()));
        List<String> apiForms = ExporterRegistry.forms();
        boolean builtins = apiForms.size() == 3;
        for (int i = 0; builtins && i < 3; i++) {
            builtins = apiForms.get(i).equals(ExportKind.values()[i].form());
        }
        check("api ExporterRegistry built-in forms [world,save,datapack] == engine first three form() one-by-one",
                builtins);
        ExporterRegistry.clear();
    }

    // ---------- (2) registry determinism ----------

    private static void registryDeterminism(LanguageKeysProducer lang, ConfigProducer config,
                                            RegistriesProducer regs, MigrateMapsProducer migrate,
                                            SaveArchiveProducer save, DatapackArchiveProducer datapack,
                                            WorldPackProducer world) {
        // fresh JVM: the static hub starts empty
        check("ExportHub starts empty (forms() empty)", ExportHub.forms().isEmpty());
        check("ExportHub.producer() unregistered kind -> null",
                ExportHub.producer(ExportKind.LANGUAGE_KEYS) == null);

        ExportHub.preload();
        check("ExportHub.preload() -> forms [world, save] (enum order)",
                ExportHub.forms().equals(List.of("world", "save")));

        check("ExportHub duplicate port registration rejected (IAE)",
                expectIae(() -> ExportHub.register(ExportKind.WORLD_PACK, new ExportPort() {
                    @Override
                    public ExportKind kind() {
                        return ExportKind.WORLD_PACK;
                    }

                    @Override
                    public String description() {
                        return "dup";
                    }
                })));

        List<String> seven = List.of("world", "save", "datapack",
                "language_keys", "config", "registries", "migrate_maps");
        ExportHub.preloadFormal();
        check("ExportHub.preloadFormal() -> forms() == the seven enum forms", ExportHub.forms().equals(seven));
        ExportHub.preloadFormal();
        check("ExportHub.preloadFormal() idempotent (second call no-op, forms unchanged)",
                ExportHub.forms().equals(seven));

        // register all seven producers (the four content producers + the three p.2.18.1 archive producers)
        ExportHub.register(ExportKind.WORLD_PACK, world);
        ExportHub.register(ExportKind.SAVE, save);
        ExportHub.register(ExportKind.DATAPACK, datapack);
        ExportHub.register(ExportKind.LANGUAGE_KEYS, lang);
        ExportHub.register(ExportKind.CONFIG, config);
        ExportHub.register(ExportKind.REGISTRIES, regs);
        ExportHub.register(ExportKind.MIGRATE_MAPS, migrate);

        check("producer() lookup hit per registered kind (seven)",
                ExportHub.producer(ExportKind.WORLD_PACK) == world
                        && ExportHub.producer(ExportKind.SAVE) == save
                        && ExportHub.producer(ExportKind.DATAPACK) == datapack
                        && ExportHub.producer(ExportKind.LANGUAGE_KEYS) == lang
                        && ExportHub.producer(ExportKind.CONFIG) == config
                        && ExportHub.producer(ExportKind.REGISTRIES) == regs
                        && ExportHub.producer(ExportKind.MIGRATE_MAPS) == migrate);
        check("all seven producer tdType() == their kind form()",
                world.tdType().equals("world") && save.tdType().equals("save")
                        && datapack.tdType().equals("datapack") && lang.tdType().equals("language_keys")
                        && config.tdType().equals("config") && regs.tdType().equals("registries")
                        && migrate.tdType().equals("migrate_maps"));

        check("ExportHub duplicate producer registration rejected (IAE)",
                expectIae(() -> ExportHub.register(ExportKind.LANGUAGE_KEYS, new LanguageKeysProducer(Map.of()))));
        check("ExportHub producer tdType/kind-form mismatch rejected (IAE)",
                expectIae(() -> ExportHub.register(ExportKind.LANGUAGE_KEYS, new FakeProducer("config"))));
        check("producer registrations leave forms() fixed (still the seven enum forms)",
                ExportHub.forms().equals(seven));
    }

    // ---------- (3) deterministic exporter identity ----------

    private static void identityExporters(ExportProducer lang, ExportProducer config, ExportProducer regs,
                                         ExportProducer migrate, ExportProducer save, ExportProducer datapack,
                                         ExportProducer world) {
        assertProducerIdentity("LanguageKeysProducer", lang);
        assertProducerIdentity("ConfigProducer", config);
        assertProducerIdentity("RegistriesProducer", regs);
        assertProducerIdentity("MigrateMapsProducer", migrate);
        assertProducerIdentity("SaveArchiveProducer", save);
        assertProducerIdentity("DatapackArchiveProducer", datapack);
        // world-pack byte identity needs a live datapack directory (DatapackPack walks
        // the file tree) and is asserted by the p.2.9.4 WorldPackProbe — here we state
        // its tdType + hub registration state honestly.
        check("WorldPackProducer tdType() == \"world\"", world.tdType().equals("world"));
        check("WorldPackProducer hub registration intact (producer(WORLD_PACK) hit)",
                ExportHub.producer(ExportKind.WORLD_PACK) == world);
    }

    /** export/rehydrate/export + exportZd/rehydrateZd/exportZd byte identity, run twice each. */
    private static void assertProducerIdentity(String label, ExportProducer p) {
        String td1 = p.exportTd();
        String td2 = p.exportTd();
        check(label + ": exportTd twice byte-identical (determinism)", td1.equals(td2));
        check(label + ": exportTd->rehydrateTd->exportTd byte-identical", p.rehydrateTd(td1).equals(td1));
        byte[] zd1 = p.exportZd();
        byte[] zd2 = p.exportZd();
        check(label + ": exportZd twice byte-identical (determinism)", Arrays.equals(zd1, zd2));
        check(label + ": exportZd->rehydrateZd->exportZd byte-identical", Arrays.equals(p.rehydrateZd(zd1), zd1));
    }

    // ---------- (4) dual-format consistency (ExportHub conversion path) ----------

    private static void dualFormatConsistency(ExportProducer lang) {
        // required oracle: zdOf(tdOf(zd)) == source zd byte-for-byte. The reverse textual
        // direction is deliberately not asserted: Td.parse strips the optional top-level
        // name (`lang = [...]`), so the zd payload carries the content tree only — by design.
        byte[] zd = lang.exportZd();
        check("dual-format: zdOf(tdOf(zd)) == source zd byte-for-byte (ExportHub round-trip)",
                Arrays.equals(ExportHub.zdOf(ExportHub.tdOf(zd)), zd));
    }

    // ---------- (5) frozen sample anchor ----------

    private static void sampleAnchor() {
        String got = LanguageKeysExporter.exportTd(langKeys());
        String expected = "type tie<data>\nlang = [\n"
                + "  version = 1,\n"
                + "  count = 3,\n"
                + "  entries = [\n"
                + "    [\n"
                + "      key = \"greeting\",\n"
                + "      text = \"Hello, world!\",\n"
                + "    ],\n"
                + "    [\n"
                + "      key = \"multi\",\n"
                + "      text = \"line1\\nline2\",\n"
                + "    ],\n"
                + "    [\n"
                + "      key = \"quote\",\n"
                + "      text = \"say \\\"hi\\\"\",\n"
                + "    ],\n"
                + "  ],\n"
                + "]";
        check("sample anchor: LanguageKeysExporter td output for the fixed 3-key sample == frozen text (char-by-char)",
                got.equals(expected));
    }

    // ---------- deterministic samples ----------

    /** 3-key language-keys sample, shuffled input to prove lexicographic sorting (key-sorted output). */
    private static Map<String, String> langKeys() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("quote", "say \"hi\"");          // escaped quotes
        m.put("greeting", "Hello, world!");
        m.put("multi", "line1\nline2");        // escaped newline
        return m;
    }

    /** Two-tier config sample: global + save overrides, with escaped text values. */
    private static Map<String, String> configGlobal() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("render.distance", "12");
        m.put("log.level", "info");
        m.put("escaped", "a\"b\\c\nd\te");     // quote / backslash / newline / tab
        return m;
    }

    private static Map<String, String> configOverrides() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("render.distance", "32");        // overrides win this key in effective
        return m;
    }

    /** Registries sample: registry names shuffled (sorted on export), ids keep input order, one escaped id. */
    private static Map<String, List<String>> registriesSample() {
        Map<String, List<String>> m = new LinkedHashMap<>();
        m.put("item", List.of("stick", "a\"b"));
        m.put("mob", List.of("zombie", "skeleton"));
        return m;
    }

    /** Migrate-maps sample: from keys shuffled (sorted on export); blank `to` is legal (map-to-empty). */
    private static Map<String, String> migrateSample() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("q", "");
        m.put("old.name", "new.name");
        m.put("a", "b");
        return m;
    }

    /** SaveContainer source: one WORLD slot doc + global rules, pure JVM (no filesystem). */
    private static SaveContainer saveSample() {
        TdTable worldDoc = TdTable.builder().put("level", TdValue.str("mirror-001")).build();
        Map<String, TdValue> global = new LinkedHashMap<>();
        global.put("verbose", TdValue.str("on"));
        return new SaveContainer().attach(SaveSlot.WORLD, worldDoc).globalRules(global);
    }

    /** Datapack source via the archive rehydrate path (pass-through FUNCTION payload), pure JVM. */
    private static Datapack datapackSample() {
        // bare-root + `export` entry form (the p.2.18.1 archive convention: the archive's
        // own export writes `[ export = [...] ]` — Td.parse would strip a *named* top level)
        String canned = "type tie<data>\n[\n  export = [\n"
                + "    version = 1,\n"
                + "    entries = [\n"
                + "      [ kind = \"function\", ns = \"toterra\", path = \"hello\", payload = [ say = \"hi\" ] ],\n"
                + "    ],\n"
                + "  ],\n"
                + "]\n";
        return DatapackExportArchive.rehydrate(canned);
    }

    // ---------- helpers ----------

    /** Minimal port fixture: serves a kind with a fixed description. */
    private record Port(ExportKindSpec kind, String description) implements ExportPortSpec {
    }

    private static ExportPortSpec port(ExportKindSpec kind) {
        return new Port(kind, "desc:" + kind.form());
    }

    /** Producer fixture with an arbitrary tdType (for the mismatch-rejection check). */
    private static final class FakeProducer implements ExportProducer {
        private final String type;

        FakeProducer(String type) {
            this.type = type;
        }

        @Override
        public String tdType() {
            return type;
        }

        @Override
        public String exportTd() {
            return "";
        }

        @Override
        public byte[] exportZd() {
            return new byte[0];
        }

        @Override
        public String rehydrateTd(String td) {
            return td;
        }

        @Override
        public byte[] rehydrateZd(byte[] zd) {
            return zd;
        }
    }

    /** Runs a snippet that must throw {@link IllegalArgumentException}; true when it does. */
    private static boolean expectIae(Runnable snippet) {
        try {
            snippet.run();
            return false;
        } catch (IllegalArgumentException e) {
            return true;
        }
    }

    private static void check(String name, boolean ok) {
        checks++;
        if (ok) {
            System.out.println("[PASS] " + name);
        } else {
            failures++;
            System.out.println("[FAIL] " + name);
        }
    }
}
