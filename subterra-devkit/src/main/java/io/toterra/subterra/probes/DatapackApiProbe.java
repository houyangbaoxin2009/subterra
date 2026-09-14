// p.2.33.1: deterministic api.datapack DatapackApi probe — bridges the api contract
// (io.toterra.subterra.api.datapack.DatapackApi) against the engine mirror
// (engine.datapack.DatapackApiMirror) and asserts same-input-same-output byte-identity across
// kinds / entry-id / rules two-layer resolve+render / td export∘rehydrate∘export round-trip /
// empty-pack defaults. Pure JVM: no MC runtime, no wall-clock, no randomness, no timestamps.
package io.toterra.subterra.probes;

import io.toterra.subterra.api.datapack.DatapackApi;
import io.toterra.subterra.api.datapack.DatapackApi.Kind;
import io.toterra.subterra.api.datapack.DatapackApi.Entry;
import io.toterra.subterra.api.datapack.DatapackApi.Datapack;
import io.toterra.subterra.engine.config.TdValue;
import io.toterra.subterra.engine.datapack.DatapackApiMirror;
import io.toterra.subterra.engine.datapack.EntryKind;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * p.2.33.1 api.datapack 确定性探针 —— 把 api 契约面 {@code api.datapack.DatapackApi} 与引擎侧镜像
 * {@code engine.datapack.DatapackApiMirror} 固定锚定并断言“同输入同输出逐位一致”。五组校验：
 * <ol>
 *   <li><b>kind 固定目录段序</b>：api {@code kinds()/kindDirs()} 与 engine
 *       {@code DatapackApiMirror.kindDirs()}（源自 {@code engine.datapack.EntryKind}）逐字同序（
 *       {@code function/recipe/loot_table/worldgen/structure/tag/lang/noise_settings}）；{@code dir()} 往返
 *       （{@code fromDir} 回映射命中自身）与未知段拒绝。</li>
 *   <li><b>规范条目 id 逐位一致</b>：api {@code entryId(Kind, ns, path)} 与镜像
 *       {@code entryId(EntryKind, ns, path)} 对同一 {@code ns/path} 同输入同字节，且与
 *       {@code <ns>:<dir>/<path>} 手工钉死串相等。</li>
 *   <li><b>规则双层确定性</b>：同一组包级规则（后包 key 胜出）+ 存档覆盖（全胜）下，api
 *       {@code resolveRules} 与镜像 {@code resolveRules} 各自 resolve→render 输出的确定性标记文本逐字符
 *       一致；空覆盖不改变包级结果；render(空)==""（镜像 {@code DatapackRules}）。</li>
 *   <li><b>td 规范往返逐字节</b>：固定规范 payload 文本下，api 与镜像各自 export∘rehydrate∘export 连跑
 *       同字节；且 api {@code exportEntryTd} 与镜像 {@code exportEntryTd}（经
 *       {@code Td.parse/write} 规范化）对同输入同输出。</li>
 *   <li><b>空数据包缺省</b>：api {@code emptyDatapack()} 无条目无规则、名=镜像
 *       {@code emptyName()}={@code EMPTY_NAME}=""；镜像 {@code defaultRules()} 为空。</li>
 * </ol>
 * 每项失败计数 +1 并给出明确诊断；全过才输出 {@code [DatapackApiProbe] PASS (n checks)} 并 exit 0，
 * 否则 FAIL 计数 exit 1。确定性纪律：固定序、无时序、无随机；全部线性遍历。纯 JVM——绝不触碰
 * Minecraft 类。
 *
 * <p>p.2.33.1 deterministic api.datapack probe — pins the api contract
 * {@code api.datapack.DatapackApi} against the engine-side mirror
 * {@code engine.datapack.DatapackApiMirror} and asserts same-input-same-output byte-identity. Five groups:
 * ⟨1⟩ fixed kind directory-segment order (api vs mirror, source {@code engine.datapack.EntryKind}) plus
 * {@code dir()/fromDir} round-trip and unknown-segment rejection; ⟨2⟩ canonical entry id byte-identity
 * (api {@code entryId(Kind,…)} vs mirror {@code entryId(EntryKind,…)} vs the hand-pinned
 * {@code <ns>:<dir>/<path>}); ⟨3⟩ two-layer rule determinism (later pack wins + save override wins) — api
 * and mirror resolve→render marker text char-identical, empty overrides change nothing, render(empty)=="";
 * ⟨4⟩ td canonical round-trip (export∘rehydrate∘export) byte-identical on both sides, and api vs mirror
 * export agree for the same input; ⟨5⟩ empty-pack defaults ({@code emptyDatapack()} empty, name == mirror
 * {@code emptyName()} == {@code EMPTY_NAME} == "").
 */
public final class DatapackApiProbe {

    private DatapackApiProbe() {
    }

    private static int checks = 0;
    private static int failures = 0;

    private static void check(String name, boolean ok) {
        checks++;
        if (ok) {
            System.out.println("[PASS] " + name);
        } else {
            failures++;
            System.out.println("[FAIL] " + name);
        }
    }

    public static void main(String[] args) {
        try {
            kindOrder();
            entryIds();
            rulesDeterminism();
            tdRoundTrip();
            emptyDefaults();
        } catch (Exception e) {
            failures++;
            System.out.println("[FAIL] probe exception: " + e);
            e.printStackTrace(System.out);
        }
        if (failures == 0) {
            System.out.println("[DatapackApiProbe] PASS (" + checks + " checks)");
            System.exit(0);
        } else {
            System.out.println("[DatapackApiProbe] FAIL (" + failures + " of " + checks + " checks)");
            System.exit(1);
        }
    }

    // ---------- (1) kind fixed directory-segment order ----------

    private static void kindOrder() {
        List<String> expectedDirs = List.of(
                "function", "recipe", "loot_table", "worldgen",
                "structure", "tag", "lang", "noise_settings");
        check("kinds: api kindDirs() == engine mirror kindDirs() verbatim (8 dirs)",
                DatapackApi.kindDirs().equals(DatapackApiMirror.kindDirs())
                        && DatapackApi.kindDirs().equals(expectedDirs)
                        && DatapackApi.kinds().size() == 8);
        // per-kind dir + fromDir round-trip + unknown rejection
        boolean roundTrip = true;
        boolean dirMatch = true;
        for (Kind k : Kind.values()) {
            roundTrip = roundTrip && Kind.fromDir(k.dir()) == k;
            EngineEntryKindMatch match = engineKindByDir(k.dir());
            dirMatch = dirMatch && match != null && match.kind().dir().equals(k.dir());
        }
        check("kinds: Kind.dir()/fromDir() full round-trip (8 kinds, each one pass)",
                roundTrip);
        check("kinds: every api Kind.dir() has an engine EntryKind with the same dir",
                dirMatch);
        check("kinds: unknown kind dir rejected (IAE) on both sides",
                throwsIae(() -> Kind.fromDir("bogus"))
                        && throwsIae(() -> DatapackApiMirror.kindFromDir("bogus")));
    }

    // ---------- (2) canonical entry id byte-identity ----------

    private static void entryIds() {
        String ns = "toterra";
        String[][] paths = {
                {"recipe", "example"},
                {"structure", "shrine"},
                {"tag", "item/special"},
                {"lang", "en_us"},
                {"noise_settings", "climate/detail"},
        };
        boolean allMatch = true;
        for (String[] p : paths) {
            String dir = p[0];
            String path = p[1];
            Kind k = Kind.fromDir(dir);
            String apiId = DatapackApi.entryId(k, ns, path);
            String mirrorId = DatapackApiMirror.entryId(EntryKind.fromDirectory(dir), ns, path);
            String pinned = ns + ":" + dir + "/" + path;
            allMatch = allMatch && apiId.equals(mirrorId) && apiId.equals(pinned);
        }
        check("entryId: api == mirror == <ns>:<dir>/<path> byte-identical (5 fixed paths)", allMatch);
        check("entryId: Entry.id() derives the same canonical id",
                DatapackApi.rehydrateEntry(Kind.RECIPE, ns, "example", "[]").id()
                        .equals(DatapackApi.entryId(Kind.RECIPE, ns, "example")));
    }

    // ---------- (3) two-layer rule determinism (api vs mirror) ----------

    private static void rulesDeterminism() {
        // pack rules: two packs; a later pack key (b) must win over the earlier one's value
        Map<String, String> packA = order("a", "1", "b", "1");
        Map<String, String> packB = order("b", "2", "c", "3");
        Map<String, String> overrides = order("c", "30", "d", "40");

        // engine-side analogue on TdValue maps
        Map<String, TdValue> packATd = strMap(packA);
        Map<String, TdValue> packBTd = strMap(packB);
        Map<String, TdValue> overridesTd = strMap(overrides);

        Map<String, String> apiResolved = DatapackApi.resolveRules(List.of(packA, packB), overrides);
        Map<String, TdValue> mirrorResolved = DatapackApiMirror.resolveRules(List.of(packATd, packBTd), overridesTd);
        String apiRender = DatapackApi.renderRules(apiResolved);
        String mirrorRender = DatapackApiMirror.renderRules(mirrorResolved);

        check("rules: resolve two-layer (later pack wins b=2, override wins c=30,d=40) values",
                apiResolved.get("a").equals("1")
                        && apiResolved.get("b").equals("2")
                        && apiResolved.get("c").equals("30")
                        && apiResolved.get("d").equals("40")
                        && apiResolved.size() == 4);
        check("rules: api render == engine mirror render char-by-char",
                apiRender.equals(mirrorRender) && apiRender.equals("a=1; b=2; c=30; d=40"));
        check("rules: repeated resolve deterministic (same input -> same bytes)",
                apiRender.equals(DatapackApi.renderRules(
                        DatapackApi.resolveRules(List.of(packA, packB), overrides))));
        check("rules: empty save overrides keep pack-level result",
                DatapackApi.renderRules(DatapackApi.resolveRules(List.of(packA, packB), Map.of()))
                        .equals("a=1; b=2; c=3"));
        check("rules: render(empty) == \"\" on both sides",
                DatapackApi.renderRules(Map.of()).equals("")
                        && DatapackApiMirror.renderRules(Map.of()).equals(""));
    }

    // ---------- (4) td canonical export∘rehydrate∘export byte-identical ----------

    private static void tdRoundTrip() {
        // raw payload literals (bare tables); the engine canonical form is computed once via
        // Td.parse/Td.write so BOTH sides are fed the identical canonical text.
        String[][] raws = {
                {"[]"},
                {"[\n  replace = false,\n  values = [\n    \"minecraft:stick\",\n  ],\n]"},
                {"[\n  type = \"minecraft:crafting_shaped\",\n  result = \"toterra:crystal\",\n]"},
        };
        boolean fivex = true;
        boolean apiVsMirrorExport = true;
        for (String[] p : raws) {
            String canonical = DatapackApiMirror.writeTd(
                    io.toterra.subterra.engine.config.Td.parse(p[0]));
            // passthrough kind (WORLDGEN): the engine DatapackExporter passthrough branch yields
            // exactly Td.write(payload), so api passthrough == engine passthrough for the same
            // canonical input; per-side round-trip identity holds regardless of kind.
            Entry e1 = DatapackApi.rehydrateEntry(Kind.WORLDGEN, "toterra", "configured_feature/x", canonical);
            String exA1 = DatapackApi.exportEntryTd(e1);
            String exA2 = DatapackApi.exportEntryTd(
                    DatapackApi.rehydrateEntry(Kind.WORLDGEN, "toterra", "configured_feature/x", exA1));
            fivex = fivex && exA1.equals(exA2);

            io.toterra.subterra.engine.datapack.DatapackEntry me =
                    DatapackApiMirror.rehydrateEntry(EntryKind.WORLDGEN, "toterra", "configured_feature/x", canonical);
            String exM1 = DatapackApiMirror.exportEntryTd(me);
            String exM2 = DatapackApiMirror.exportEntryTd(
                    DatapackApiMirror.rehydrateEntry(EntryKind.WORLDGEN, "toterra", "configured_feature/x", exM1));
            fivex = fivex && exM1.equals(exM2) && exA1.equals(exA2);
            apiVsMirrorExport = apiVsMirrorExport && exA1.equals(exM1);
        }
        check("td: export\u2218rehydrate\u2218export round-trip byte-identical (api & mirror, 3 payloads)",
                fivex);
        check("td: api export == engine mirror export for the same canonical input (passthrough kind)",
                apiVsMirrorExport);
    }

    // ---------- (5) empty-pack defaults ----------

    private static void emptyDefaults() {
        Datapack empty = DatapackApi.emptyDatapack();
        check("empty: emptyDatapack() has no entries / no rules / name == mirror emptyName()",
                empty.entries().isEmpty()
                        && empty.byKind(Kind.RECIPE).isEmpty()
                        && empty.rules().isEmpty()
                        && empty.isEmpty()
                        && empty.name().equals(DatapackApiMirror.emptyName())
                        && DatapackApiMirror.emptyName().equals(DatapackApiMirror.EMPTY_NAME)
                        && DatapackApiMirror.EMPTY_NAME.equals(DatapackApi.EMPTY_NAME));
        check("empty: mirror defaultRules() is empty",
                DatapackApiMirror.defaultRules().isEmpty());
        check("empty: lookups against an empty datapack are null / empty deterministically",
                empty.get(Kind.RECIPE, "toterra", "example") == null
                        && empty.getById("toterra:recipe/example") == null);
    }

    // ---------- helpers ----------

    /** Identifier-ordering LinkedHashMap helper (fixed order, no Map.of() empty ambiguity). */
    private static Map<String, String> order(String... kv) {
        Map<String, String> m = new LinkedHashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            m.put(kv[i], kv[i + 1]);
        }
        return m;
    }

    private static Map<String, TdValue> strMap(Map<String, String> src) {
        Map<String, TdValue> m = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : src.entrySet()) {
            m.put(e.getKey(), TdValue.str(e.getValue()));
        }
        return m;
    }

    /** Returns the engine EntryKind whose dir equals the given segment, or null. */
    private static EngineEntryKindMatch engineKindByDir(String dir) {
        for (EntryKind k : EntryKind.values()) {
            if (k.dir().equals(dir)) {
                return new EngineEntryKindMatch(k);
            }
        }
        return null;
    }

    private record EngineEntryKindMatch(EntryKind kind) {
    }

    private static boolean throwsIae(Runnable r) {
        try {
            r.run();
            return false;
        } catch (IllegalArgumentException e) {
            return true;
        }
    }
}