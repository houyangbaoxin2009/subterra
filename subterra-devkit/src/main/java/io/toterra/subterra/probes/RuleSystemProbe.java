// p.2.17.5: deterministic rule-system probe — the p.2.17.1..4 engine.config.rules stack
// (RuleType / RuleStore / RuleReloader / RuleDocument) against the api RuleRegistry and
// engine DatapackRules mirrors. Pure JVM: no MC runtime, no wall-clock, no timestamps.
package io.toterra.subterra.probes;

import io.toterra.subterra.api.config.Rule;
import io.toterra.subterra.api.config.RuleRegistry;
import io.toterra.subterra.api.config.RuleSet;
import io.toterra.subterra.engine.config.Td;
import io.toterra.subterra.engine.config.TdTable;
import io.toterra.subterra.engine.config.TdValue;
import io.toterra.subterra.engine.config.rules.RuleDocument;
import io.toterra.subterra.engine.config.rules.RuleKey;
import io.toterra.subterra.engine.config.rules.RuleReloader;
import io.toterra.subterra.engine.config.rules.RuleReport;
import io.toterra.subterra.engine.config.rules.RuleSpec;
import io.toterra.subterra.engine.config.rules.RuleStore;
import io.toterra.subterra.engine.config.rules.RuleType;
import io.toterra.subterra.engine.config.rules.RuleValidator;
import io.toterra.subterra.engine.config.rules.RuleViolation;
import io.toterra.subterra.engine.config.rules.RuleViolationException;
import io.toterra.subterra.engine.datapack.DatapackRules;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * p.2.17.5 确定性规则系统探针 —— 把 p.2.17.1..4 的 engine.config.rules 栈（RuleType /
 * RuleStore / RuleReloader / RuleDocument）逐一桥接到 api RuleRegistry 与 engine
 * DatapackRules 镜像，纯 JVM 全量断言。七组校验：
 * <ol>
 *   <li><b>类型化规则</b>：RuleType 权威序锚定 [boolean,int,float,string,table]；
 *       各型 form()/fromForm() 往返（未知注册名 IAE）；accepts() 判定——BOOLEAN 仅
 *       true/false、INT 十进制长整（非数字拒）、FLOAT double（非数字拒）、STRING 任意非 null、
 *       TABLE 拒字符串 raw；null 全型拒绝。</li>
 *   <li><b>注册序与重复拒绝</b>：RuleStore 对同一规格集合按不同注册序构造，
 *       specs() 顺序与各自注册序一致；增量 register 追加到尾部；同 key 二次注册 IAE 且
 *       specs() 不变。</li>
 *   <li><b>双层覆盖</b>：RuleStore 全局档 + 存档覆盖档——同 key 覆盖档胜出、未覆盖键保持
 *       全局、resolve() 保插入序；与 api RuleRegistry.resolveTiered（同输入字符串版）产出一致；
 *       与 DatapackRules.resolveTiered（TdValue 转换版）key 集合与值渲染一致。</li>
 *   <li><b>校验违约</b>：RuleValidator 对未知键 UNKNOWN_KEY / 类型违约 TYPE_VIOLATION /
 *       缺失必填 OTHER / 重复提供 CONFLICT 逐类命中，且违约按固定 kind 序输出；
 *       load 违约抛 RuleViolationException 且不写入任何条目（全有或全无原子性）。</li>
 *   <li><b>render 对齐</b>：RuleStore.render() 与 RuleRegistry.render /
 *       DatapackRules.render 对同一有效值集逐字符一致（标量场景）；空 → 空串。</li>
 *   <li><b>热重载</b>：RuleReloader 逐字节变更检测（同文本 → unchanged、revision 不自增）；
 *       变更应用（revision+1、终态更新）；违约文档 failed 保旧态；重入（同一目标重复送达
 *       unchanged）；同序列在两个 fresh store 上重放终态与 revision 一致。</li>
 *   <li><b>规则文档往返</b>：RuleDocument.fromTd(Td.parse(toTd(...))) 往返恒等
 *       （key/value 集合、行序 = 注册序后接字典序、re-write 文本确定性、空值集 → 空列表）。</li>
 * </ol>
 * 每项失败计数 +1 并给出明确诊断；全过才输出 {@code [RuleSystemProbe] PASS (n checks)} 并
 * exit 0，否则 FAIL 计数 exit 1。确定性纪律：固定序、无时序、无随机；全部线性遍历（禁
 * O(n²)）；失败计数只在失败路径自增。纯 JVM——绝不触碰 Minecraft 类。
 *
 * <p>p.2.17.5 deterministic rule-system probe — bridges the p.2.17.1..4
 * engine.config.rules stack (RuleType / RuleStore / RuleReloader / RuleDocument) one by one to
 * the api RuleRegistry and engine DatapackRules mirrors, all asserted in pure JVM. Seven groups:
 * <ol>
 *   <li><b>typed rules</b>: RuleType authoritative order anchored [boolean,int,float,string,table];
 *       form()/fromForm() round-trip per type (unknown form → IAE); accepts() — BOOLEAN only
 *       true/false, INT decimal long (non-numeric rejected), FLOAT double (non-numeric rejected),
 *       STRING any non-null, TABLE rejects a string raw; null rejected by every type.</li>
 *   <li><b>registration order &amp; dup-reject</b>: two RuleStores built from the same spec set in
 *       different registration orders keep their own order in specs(); incremental register appends
 *       to the tail; a duplicate-key register is an IAE leaving specs() unchanged.</li>
 *   <li><b>two-tier override</b>: RuleStore global layer + save-overrides layer — the override wins
 *       a conflicting key, untouched keys stay global, resolve() keeps insertion order; equal to api
 *       RuleRegistry.resolveTiered (same string inputs); key set and value rendering equal to
 *       DatapackRules.resolveTiered (TdValue conversion version).</li>
 *   <li><b>validation violations</b>: RuleValidator hits every class — UNKNOWN_KEY /
 *       TYPE_VIOLATION / OTHER (missing required) / CONFLICT (duplicate) — with the fixed kind
 *       order; a violating load throws RuleViolationException writing nothing (all-or-nothing
 *       atomicity).</li>
 *   <li><b>render alignment</b>: RuleStore.render() is char-identical to RuleRegistry.render and
 *       DatapackRules.render for the same effective scalar set; empty → {@code ""}.</li>
 *   <li><b>hot reload</b>: RuleReloader byte-exact change detection (same text → unchanged, no
 *       revision bump); a changed text applies (revision+1, final state updated); a violating
 *       document is failed keeping the old state; re-entrant delivery of the same target is
 *       unchanged; the same sequence replayed on a fresh store ends in the same state and
 *       revision.</li>
 *   <li><b>rule-doc round-trip</b>: RuleDocument.fromTd(Td.parse(toTd(...))) round-trips
 *       identically (key/value set, row order = registration order then lexicographic, re-write
 *       text deterministic, empty values → empty list).</li>
 * </ol>
 * Every failure is counted and diagnosed; PASS only when all checks pass, then exit 0, else FAIL
 * with counts and exit 1. Determinism discipline: fixed order, no timing, no randomness; all
 * traversals linear (no O(n²)); failures are counted only on failing paths. Pure JVM — never
 * touches Minecraft classes.
 */
public final class RuleSystemProbe {

    private static int checks = 0;
    private static int failures = 0;

    private RuleSystemProbe() {
    }

    public static void main(String[] args) {
        try {
            typedRules();
            registrationOrder();
            RuleStore store = twoTierOverride();
            validationViolations();
            renderAlignment(store);
            hotReload();
            docRoundTrip();
        } catch (Exception e) {
            failures++;
            System.out.println("[FAIL] probe exception: " + e);
            e.printStackTrace(System.out);
        }
        if (failures == 0) {
            System.out.println("[RuleSystemProbe] PASS (" + checks + " checks)");
            System.exit(0);
        } else {
            System.out.println("[RuleSystemProbe] FAIL (" + failures + " of " + checks + " checks)");
            System.exit(1);
        }
    }

    // ---------- (1) typed rules ----------

    private static void typedRules() {
        String[] forms = {"boolean", "int", "float", "string", "table"};
        boolean orderOk = RuleType.values().length == forms.length;
        for (int i = 0; orderOk && i < forms.length; i++) {
            orderOk = RuleType.values()[i].form().equals(forms[i]);
        }
        check("RuleType authoritative order anchored as [boolean,int,float,string,table]", orderOk);

        boolean roundTrip = true;
        for (RuleType t : RuleType.values()) {
            roundTrip = roundTrip && RuleType.fromForm(t.form()) == t;
        }
        check("RuleType form()/fromForm() round-trip for every type", roundTrip);

        check("RuleType fromForm unknown form rejected (IAE)", expectIae(() -> RuleType.fromForm("bogus")));
        check("RuleType fromForm(null) rejected (IAE)", expectIae(() -> RuleType.fromForm(null)));

        check("RuleType.accepts BOOLEAN: true/false accepted, \"2\" rejected",
                RuleType.BOOLEAN.accepts("true")
                        && RuleType.BOOLEAN.accepts("false")
                        && !RuleType.BOOLEAN.accepts("2"));
        check("RuleType.accepts INT: decimal long accepted, non-numeric rejected",
                RuleType.INT.accepts("42")
                        && RuleType.INT.accepts("-17")
                        && !RuleType.INT.accepts("4.2")
                        && !RuleType.INT.accepts("abc"));
        check("RuleType.accepts FLOAT: double accepted, non-numeric rejected",
                RuleType.FLOAT.accepts("3.5")
                        && RuleType.FLOAT.accepts("-1e3")
                        && !RuleType.FLOAT.accepts("x"));
        check("RuleType.accepts STRING: any non-null string accepted",
                RuleType.STRING.accepts("") && RuleType.STRING.accepts("any text") && RuleType.STRING.accepts("42"));
        check("RuleType.accepts TABLE: string raw rejected", !RuleType.TABLE.accepts("x"));

        boolean nullRejected = true;
        for (RuleType t : RuleType.values()) {
            nullRejected = nullRejected && !t.accepts(null);
        }
        check("RuleType.accepts(null) rejected for every type", nullRejected);
    }

    // ---------- (2) registration order & duplicate rejection ----------

    private static void registrationOrder() {
        RuleSpec a = new RuleSpec(new RuleKey("a"), RuleType.INT, "1", "int a");
        RuleSpec b = new RuleSpec(new RuleKey("b"), RuleType.BOOLEAN, "true", "bool b");
        RuleSpec c = new RuleSpec(new RuleKey("c"), RuleType.STRING, "x", "string c");

        RuleStore store1 = new RuleStore(List.of(a, b, c));
        RuleStore store2 = new RuleStore(List.of(c, a, b)); // same spec set, different registration order
        check("RuleStore.register keeps registration order (store1 == [a,b,c])",
                specKeys(store1).equals(List.of("a", "b", "c")));
        check("RuleStore.register keeps registration order (store2 == [c,a,b])",
                specKeys(store2).equals(List.of("c", "a", "b")));
        check("RuleStore.specs() same key set across registration orders",
                specKeys(store1).size() == 3
                        && specKeys(store1).containsAll(specKeys(store2))
                        && specKeys(store2).containsAll(specKeys(store1)));

        RuleStore inc = new RuleStore(List.of());
        inc.register(a);
        inc.register(b);
        inc.register(c);
        check("RuleStore incremental register appends to the tail ([a,b,c])",
                specKeys(inc).equals(List.of("a", "b", "c")));

        boolean dupRejected = expectIae(() ->
                inc.register(new RuleSpec(new RuleKey("a"), RuleType.INT, "9", "dup a")));
        check("RuleStore duplicate-key register rejected (IAE)", dupRejected);
        check("RuleStore rejected register leaves specs unchanged", specKeys(inc).equals(List.of("a", "b", "c")));
    }

    // ---------- (3) two-tier override vs RuleRegistry / DatapackRules ----------

    private static RuleStore twoTierOverride() {
        List<RuleSpec> specs = List.of(
                new RuleSpec(new RuleKey("enable_structures"), RuleType.BOOLEAN, "true", "world structures"),
                new RuleSpec(new RuleKey("max_radius"), RuleType.INT, "128", "scan radius"),
                new RuleSpec(new RuleKey("mob_cap"), RuleType.STRING, "auto", "mob cap"));
        RuleStore store = new RuleStore(specs);
        store.loadGlobalText(doc("enable_structures=true", "max_radius=256", "mob_cap=auto"));
        store.loadOverridesText(doc("max_radius=512"));

        Map<String, String> effective = store.resolve();
        check("RuleStore two-tier resolve: override wins same key, global keys preserved",
                effective.equals(Map.of("enable_structures", "true", "max_radius", "512", "mob_cap", "auto")));
        check("RuleStore resolve() preserves insertion order (global order then new override keys)",
                List.copyOf(effective.keySet()).equals(List.of("enable_structures", "max_radius", "mob_cap")));

        // api RuleRegistry.resolveTiered (string version, same inputs)
        RuleSet apiGlobal = RuleSet.of(List.of(
                new Rule("enable_structures", "true"), new Rule("max_radius", "256"), new Rule("mob_cap", "auto")));
        RuleSet apiOverrides = RuleSet.of(List.of(new Rule("max_radius", "512")));
        Map<String, String> apiTiered = RuleRegistry.resolveTiered(apiGlobal, apiOverrides);
        check("RuleStore.resolve() == api RuleRegistry.resolveTiered (same inputs)", effective.equals(apiTiered));

        // engine DatapackRules.resolveTiered (TdValue conversion version)
        Map<String, TdValue> gTd = new LinkedHashMap<>();
        gTd.put("enable_structures", TdValue.str("true"));
        gTd.put("max_radius", TdValue.str("256"));
        gTd.put("mob_cap", TdValue.str("auto"));
        Map<String, TdValue> oTd = new LinkedHashMap<>();
        oTd.put("max_radius", TdValue.str("512"));
        Map<String, TdValue> engineTiered = DatapackRules.resolveTiered(gTd, oTd);
        check("RuleStore.resolve() key set == DatapackRules.resolveTiered (TdValue version)",
                effective.keySet().equals(engineTiered.keySet()));
        boolean valsMatch = effective.size() == engineTiered.size();
        for (Map.Entry<String, String> e : effective.entrySet()) {
            TdValue v = engineTiered.get(e.getKey());
            valsMatch = valsMatch && v != null && v.toString().equals(e.getValue());
        }
        check("RuleStore.resolve() values == DatapackRules.resolveTiered values (string rendering)", valsMatch);

        return store;
    }

    // ---------- (4) validation violations + load atomicity ----------

    private static void validationViolations() {
        List<RuleSpec> specs = List.of(
                new RuleSpec(new RuleKey("alpha"), RuleType.INT, null, "required int"),
                new RuleSpec(new RuleKey("beta"), RuleType.BOOLEAN, "true", "bool"),
                new RuleSpec(new RuleKey("gamma"), RuleType.STRING, "g", "string"));
        List<Rule> provided = List.of(
                new Rule("beta", "notabool"),   // TYPE_VIOLATION
                new Rule("gamma", "x"),         // CONFLICT (provided twice)
                new Rule("gamma", "y"),
                new Rule("unknown_key", "1"));  // UNKNOWN_KEY; alpha missing -> OTHER
        RuleReport report = RuleValidator.validate(specs, provided);
        check("RuleValidator reports all four violation kinds (count == 4)",
                report.violationCount() == 4);

        Set<String> kinds = new HashSet<>();
        for (RuleViolation v : report.violations()) {
            kinds.add(v.kind());
        }
        check("RuleValidator violation kinds hit per class (UNKNOWN_KEY/TYPE_VIOLATION/CONFLICT/OTHER)",
                kinds.equals(Set.of(RuleViolation.UNKNOWN_KEY, RuleViolation.TYPE_VIOLATION,
                        RuleViolation.CONFLICT, RuleViolation.OTHER)));

        List<String> kindsInOrder = new ArrayList<>();
        for (RuleViolation v : report.violations()) {
            kindsInOrder.add(v.kind());
        }
        check("RuleValidator fixed kind order [TYPE_VIOLATION, CONFLICT, UNKNOWN_KEY, OTHER]",
                kindsInOrder.equals(List.of(RuleViolation.TYPE_VIOLATION, RuleViolation.CONFLICT,
                        RuleViolation.UNKNOWN_KEY, RuleViolation.OTHER)));

        boolean betaType = false, gammaConflict = false, unknownKey = false, alphaMissing = false;
        for (RuleViolation v : report.violations()) {
            if (v.ruleName().equals("beta") && v.kind().equals(RuleViolation.TYPE_VIOLATION)) {
                betaType = true;
            }
            if (v.ruleName().equals("gamma") && v.kind().equals(RuleViolation.CONFLICT)) {
                gammaConflict = true;
            }
            if (v.ruleName().equals("unknown_key") && v.kind().equals(RuleViolation.UNKNOWN_KEY)) {
                unknownKey = true;
            }
            if (v.ruleName().equals("alpha") && v.kind().equals(RuleViolation.OTHER)) {
                alphaMissing = true;
            }
        }
        check("RuleValidator per-key violation hits (beta=type, gamma=conflict, unknown_key=unknown, alpha=missing)",
                betaType && gammaConflict && unknownKey && alphaMissing);

        // load atomicity: a violating document throws RuleViolationException and writes nothing
        RuleStore store = new RuleStore(specs);
        store.loadGlobalText(doc("alpha=1", "beta=true", "gamma=g"));
        Map<String, String> before = store.global();
        RuleViolationException globalCaught = null;
        try {
            store.loadGlobalText(doc("alpha=2", "mystery=9")); // unknown key -> violation
        } catch (RuleViolationException e) {
            globalCaught = e;
        }
        check("RuleStore.loadGlobalText violating doc throws RuleViolationException (UNKNOWN_KEY)",
                globalCaught != null && globalCaught.report().violationCount() == 1
                        && globalCaught.report().violations().get(0).kind().equals(RuleViolation.UNKNOWN_KEY));
        check("RuleStore load atomicity: violating doc writes nothing (global unchanged)",
                store.global().equals(before));

        RuleViolationException overridesCaught = null;
        try {
            // alpha provided (required key satisfied), beta value violates BOOLEAN -> TYPE_VIOLATION
            store.loadOverridesText(doc("alpha=1", "beta=oops"));
        } catch (RuleViolationException e) {
            overridesCaught = e;
        }
        check("RuleStore.loadOverridesText violating doc throws RuleViolationException (TYPE_VIOLATION)",
                overridesCaught != null && overridesCaught.report().violationCount() == 1
                        && overridesCaught.report().violations().get(0).kind().equals(RuleViolation.TYPE_VIOLATION));
        check("RuleStore load atomicity: violating overrides write nothing", store.overrides().isEmpty());
    }

    // ---------- (5) render alignment ----------

    private static void renderAlignment(RuleStore store) {
        Map<String, String> effective = store.resolve();
        String storeRender = store.render();
        String apiRender = RuleRegistry.render(effective);
        Map<String, TdValue> td = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : effective.entrySet()) {
            td.put(e.getKey(), TdValue.str(e.getValue()));
        }
        String datapackRender = DatapackRules.render(td);
        String expected = "enable_structures=true; max_radius=512; mob_cap=auto";
        check("RuleStore.render() == RuleRegistry.render == DatapackRules.render char-by-char (scalar)",
                storeRender.equals(apiRender) && storeRender.equals(datapackRender) && storeRender.equals(expected));
        check("RuleStore.render() empty == \"\"", new RuleStore(List.of()).render().equals(""));
        check("RuleRegistry.render(empty) == DatapackRules.render(empty) == \"\"",
                RuleRegistry.render(Map.of()).equals("") && DatapackRules.render(Map.of()).equals(""));
    }

    // ---------- (6) hot reload ----------

    private static void hotReload() {
        List<RuleSpec> specs = List.of(
                new RuleSpec(new RuleKey("a"), RuleType.INT, "1", "int a"),
                new RuleSpec(new RuleKey("b"), RuleType.BOOLEAN, "true", "bool b"));
        String doc1 = doc("a=1", "b=true");
        String doc2 = doc("a=5", "b=false");
        String docBad = doc("x=1"); // unknown key -> violation

        RuleStore storeA = new RuleStore(specs);
        RuleReloader reloaderA = new RuleReloader(storeA);

        RuleReloader.ReloadResult same = reloaderA.reloadGlobalText(doc1, doc1);
        check("RuleReloader byte-identical texts -> unchanged (no revision bump)",
                !same.changed() && !same.applied() && reloaderA.revision() == 0);

        RuleReloader.ReloadResult applied = reloaderA.reloadGlobalText(doc1, doc2);
        check("RuleReloader changed text applies (revision 0->1, store updated)",
                applied.changed() && applied.applied()
                        && reloaderA.revision() == 1
                        && storeA.global().equals(Map.of("a", "5", "b", "false")));

        RuleReloader.ReloadResult idem = reloaderA.reloadGlobalText(doc1, doc2);
        check("RuleReloader re-entrant same target -> unchanged (revision stays 1)",
                !idem.changed() && !idem.applied() && reloaderA.revision() == 1);

        Map<String, String> beforeFail = storeA.global();
        RuleReloader.ReloadResult failed = reloaderA.reloadGlobalText(doc2, docBad);
        check("RuleReloader violating doc -> failed, store keeps old state, revision stays 1",
                failed.changed() && !failed.applied() && failed.failureReason() != null
                        && reloaderA.revision() == 1 && storeA.global().equals(beforeFail));

        RuleReloader.ReloadResult ovApplied = reloaderA.reloadOverridesText(doc("a=9"), doc("a=7"));
        check("RuleReloader overrides-layer reload applies (revision 1->2)",
                ovApplied.changed() && ovApplied.applied() && reloaderA.revision() == 2
                        && storeA.overrides().equals(Map.of("a", "7")));
        RuleReloader.ReloadResult ovIdem = reloaderA.reloadOverridesText(doc("a=9"), doc("a=7"));
        check("RuleReloader overrides-layer re-entrant -> unchanged (revision stays 2)",
                !ovIdem.changed() && reloaderA.revision() == 2);

        // replay: the same sequence on a fresh store yields the same final state and revision
        RuleStore storeB = new RuleStore(specs);
        RuleReloader reloaderB = new RuleReloader(storeB);
        reloaderB.reloadGlobalText(doc1, doc1);
        reloaderB.reloadGlobalText(doc1, doc2);
        reloaderB.reloadGlobalText(doc1, doc2);
        reloaderB.reloadGlobalText(doc2, docBad);
        reloaderB.reloadOverridesText(doc("a=9"), doc("a=7"));
        reloaderB.reloadOverridesText(doc("a=9"), doc("a=7"));
        check("RuleReloader same sequence replayed -> identical final state (re-entry replay consistency)",
                storeB.resolve().equals(storeA.resolve())
                        && storeB.render().equals(storeA.render())
                        && reloaderB.revision() == reloaderA.revision());
    }

    // ---------- (7) rule document round-trip ----------

    private static void docRoundTrip() {
        List<RuleSpec> specs = List.of(
                new RuleSpec(new RuleKey("enable_structures"), RuleType.BOOLEAN, "true", "world structures"),
                new RuleSpec(new RuleKey("max_radius"), RuleType.INT, "128", "scan radius"),
                new RuleSpec(new RuleKey("mob_cap"), RuleType.STRING, "auto", "mob cap"));
        Map<String, String> values = new LinkedHashMap<>();
        values.put("enable_structures", "true");
        values.put("max_radius", "256");
        values.put("mob_cap", "auto");
        values.put("extra_key", "hello"); // not declared in any spec

        String tdText = RuleDocument.toTd(specs, values);
        TdTable parsed = Td.parse(tdText);
        List<Rule> roundTrip = RuleDocument.fromTd(parsed);
        Map<String, String> got = new LinkedHashMap<>();
        for (Rule rule : roundTrip) {
            got.put(rule.key(), rule.value());
        }
        check("RuleDocument fromTd(Td.parse(toTd(...))) round-trips identically (same key/value set)",
                got.equals(values));
        check("RuleDocument round-trip row order = spec registration order then lexicographic",
                List.copyOf(got.keySet()).equals(List.of("enable_structures", "max_radius", "mob_cap", "extra_key")));
        check("RuleDocument toTd output deterministic (re-write of round-trip map == original text)",
                RuleDocument.toTd(specs, got).equals(tdText));
        check("RuleDocument empty values -> empty rules table -> empty list",
                RuleDocument.fromTd(Td.parse(RuleDocument.toTd(specs, Map.of()))).isEmpty());
    }

    // ---------- helpers ----------

    /** Key form list of a store's specs (registration order). */
    private static List<String> specKeys(RuleStore store) {
        List<String> keys = new ArrayList<>();
        for (RuleSpec spec : store.specs()) {
            keys.add(spec.key().form());
        }
        return keys;
    }

    /** Builds a deterministic td rule-document text from {@code k=v} entries. */
    private static String doc(String... kv) {
        StringBuilder sb = new StringBuilder("[ rules = [ ");
        for (int i = 0; i < kv.length; i++) {
            if (i > 0) {
                sb.append(", ");
            }
            String[] parts = kv[i].split("=", 2);
            sb.append("[ k = \"").append(parts[0]).append("\", v = \"").append(parts[1]).append("\" ]");
        }
        sb.append(" ] ]");
        return sb.toString();
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
