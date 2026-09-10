// p.2.16.5: deterministic contract-surface probe — bridges the four p.2.16 api
// contract types against the engine mirrors and the frozen golden asset, without
// repeating the VanillaGoldenProbe full bit regression. Pure JVM: no MC runtime,
// no wall-clock, no timestamps.
package io.toterra.subterra.probes;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import io.toterra.subterra.api.config.Rule;
import io.toterra.subterra.api.config.RuleRegistry;
import io.toterra.subterra.api.config.RuleSet;
import io.toterra.subterra.api.event.EventEnvelope;
import io.toterra.subterra.api.event.EventKind;
import io.toterra.subterra.api.event.EventStream;
import io.toterra.subterra.api.export.ExportKindSpec;
import io.toterra.subterra.api.export.ExportPortSpec;
import io.toterra.subterra.api.export.ExporterRegistry;
import io.toterra.subterra.api.worldgen.NoiseRouterContract;
import io.toterra.subterra.engine.config.Td;
import io.toterra.subterra.engine.config.TdTable;
import io.toterra.subterra.engine.config.TdValue;
import io.toterra.subterra.engine.datapack.DatapackRules;
import io.toterra.subterra.engine.export.ExportKind;
import io.toterra.subterra.engine.worldgen.pipeline.router.NoiseRouter;

/**
 * p.2.16.5 确定性契约探针 —— 把 p.2.16 的四个 api 契约面（worldgen 黄金契约 / 规则注册 /
 * Exporter 注册表 / 事件契约）逐一桥接到 engine 镜像与冻结 golden 资产，锚点式校验，不重复
 * VanillaGoldenProbe 的全量 180 项位回归。四组断言：
 * <ol>
 *   <li><b>worldgen</b>：api {@code NoiseRouterContract.FIELD_NAMES} 与 engine
 *       {@code NoiseRouter.FIELD_NAMES} 逐字同序（15 项）；契约常量（GOLDEN_VERSION / SEED /
 *       MIN_Y / MAX_Y / POINT_COUNT / FIELD_COUNT）与 golden td 元数据（golden_version / seed /
 *       minY / maxY / pointCount / fieldCount）一致；契约 12 个采样点与 golden 的 12 行
 *       (x,y,z) 逐项一致（顺序敏感）；golden 文档 fields 列表与契约 FIELD_NAMES 逐字同序；
 *       桥接样例：对固定 3 个采样点，engine 镜像
 *       {@code overworld(GOLDEN_SEED, GOLDEN_MIN_Y, GOLDEN_MAX_Y).fieldAt(indexOfField("temperature"))}
 *       求值经 {@link NoiseRouterContract#bitPattern} 转位后与 golden 对应位相等。</li>
 *   <li><b>规则注册 API</b>：固定注册序（两次 register 后 {@code registered()}/{@code layers()}
 *       顺序不变）；同 key 二次注册 IAE 且注册表不变；{@code resolve} 双层语义（later layer 胜出、
 *       overrides 全胜）；{@code resolveTiered} 与 engine {@code DatapackRules.resolveTiered}
 *       （值转 {@code TdValue.str}）key 集合与覆盖关系一致；{@code render} 与
 *       {@code DatapackRules.render}（同输入 TdValue 版）逐字符一致（标量场景，含空映射）。</li>
 *   <li><b>Exporter 注册表</b>：注册 world/save/datapack 三内建后 {@code forms() ==
 *       [world, save, datapack]}（注册序，与 engine {@code ExportKind.form()} 逐一相等）；
 *       重复 form 二次注册 IAE；{@code port()} 命中/未命中（未注册 → null，null → null）。</li>
 *   <li><b>事件契约</b>：{@code EventStream} append 递增 seq 通过；seq ≤ lastSeq 拒绝
 *       （乱序/重放）且不推进状态；{@code events()} 回放序固定；{@code EventEnvelope}
 *       fromText → textPayload 往返一致；防御拷贝（改入数组不影响信封/流内）。</li>
 * </ol>
 * 每项失败计数 +1 并给出明确诊断；全过才输出 {@code [ContractProbe] PASS (n checks)} 并 exit 0，
 * 否则 FAIL 计数 exit 1。确定性纪律：固定序、无时序、无随机；全部线性遍历（禁 O(n²)）。
 * 纯 JVM——绝不触碰 Minecraft 类。
 *
 * <p>p.2.16.5 deterministic contract-surface probe — bridges the four p.2.16 api contract types
 * (worldgen golden contract / rule registry / exporter registry / event contract) one by one to
 * the engine mirrors and the frozen golden asset, anchor-style (no repeat of the VanillaGoldenProbe
 * full 180-bit regression). Four assertion groups:
 * <ol>
 *   <li><b>worldgen</b>: api {@code NoiseRouterContract.FIELD_NAMES} verbatim same order as engine
 *       {@code NoiseRouter.FIELD_NAMES} (15 items); contract constants (GOLDEN_VERSION / SEED /
 *       MIN_Y / MAX_Y / POINT_COUNT / FIELD_COUNT) equal the golden td metadata
 *       (golden_version / seed / minY / maxY / pointCount / fieldCount); the 12 contract sample
 *       points equal the golden 12 rows (x,y,z) item-by-item (order-sensitive); the golden doc
 *       fields list matches the contract FIELD_NAMES verbatim; anchor bridge: at three fixed sample
 *       points the engine mirror
 *       {@code overworld(GOLDEN_SEED, GOLDEN_MIN_Y, GOLDEN_MAX_Y).fieldAt(indexOfField("temperature"))}
 *       evaluated and converted via {@link NoiseRouterContract#bitPattern} equals the golden stored
 *       bits.</li>
 *   <li><b>rule registry</b>: fixed registration order (after two registers,
 *       {@code registered()}/{@code layers()} keep their order); a duplicate-key second register is
 *       an IAE leaving the registry untouched; {@code resolve} two-tier semantics (later layer wins,
 *       then overrides win everything); {@code resolveTiered} produces the same key set and override
 *       relations as engine {@code DatapackRules.resolveTiered} (values via {@code TdValue.str});
 *       {@code render} is char-identical to {@code DatapackRules.render} (same scalar inputs,
 *       empty map included).</li>
 *   <li><b>exporter registry</b>: after registering the world/save/datapack built-ins,
 *       {@code forms() == [world, save, datapack]} (registration order, each equal to engine
 *       {@code ExportKind.form()}); a duplicate-form second register is an IAE; {@code port()}
 *       hits and misses (unregistered form → null, null → null).</li>
 *   <li><b>event contract</b>: {@code EventStream} append accepts strictly increasing seqs; seq ≤
 *       lastSeq (reorder/replay) is rejected without advancing state; {@code events()} replay order
 *       is fixed; {@code EventEnvelope} fromText → textPayload round-trips; defensive copies keep
 *       input-array mutations out of the envelope and the stream.</li>
 * </ol>
 * Every failure is counted and diagnosed; PASS only when all checks pass, then exit 0, else FAIL
 * with counts and exit 1. Determinism discipline: fixed order, no timing, no randomness; all
 * traversals linear (no O(n²)). Pure JVM — never touches Minecraft classes.
 */
public final class ContractProbe {

    /** Classpath resource of the frozen golden asset (devkit resources on runtimeClasspath). */
    private static final String RESOURCE = "/golden/vanilla-router-seed44905237.td";

    /** Bridge sample points into the golden rows (fixed, spread over the 12 rows). */
    private static final int[] BRIDGE_POINTS = {0, 5, 11};

    /** Bridge field: the golden-anchored climate field. */
    private static final String BRIDGE_FIELD = "temperature";

    private static int checks = 0;
    private static int failures = 0;

    private ContractProbe() {
    }

    public static void main(String[] args) {
        try {
            String text = readResource(RESOURCE);
            TdTable doc = Td.parse(text);
            worldgenContract(doc);
            ruleContract();
            exporterContract();
            eventContract();
        } catch (Exception e) {
            failures++;
            System.out.println("[FAIL] probe exception: " + e);
            e.printStackTrace(System.out);
        }
        if (failures == 0) {
            System.out.println("[ContractProbe] PASS (" + checks + " checks)");
            System.exit(0);
        } else {
            System.out.println("[ContractProbe] FAIL (" + failures + " of " + checks + " checks)");
            System.exit(1);
        }
    }

    // ---------- (a) worldgen contract ----------

    private static void worldgenContract(TdTable doc) {
        // api FIELD_NAMES vs engine mirror, verbatim, length 15
        check("worldgen FIELD_NAMES: api == engine verbatim, length 15",
                NoiseRouterContract.FIELD_NAMES.length == 15
                        && NoiseRouter.FIELD_NAMES.length == 15
                        && Arrays.equals(NoiseRouterContract.FIELD_NAMES, NoiseRouter.FIELD_NAMES));

        // contract constants vs golden metadata
        check("worldgen GOLDEN_VERSION == golden metadata golden_version",
                int64(doc, "golden_version") == NoiseRouterContract.GOLDEN_VERSION);
        check("worldgen GOLDEN_SEED == golden metadata seed",
                int64(doc, "seed") == NoiseRouterContract.GOLDEN_SEED);
        check("worldgen GOLDEN_MIN_Y == golden metadata minY",
                int64(doc, "minY") == NoiseRouterContract.GOLDEN_MIN_Y);
        check("worldgen GOLDEN_MAX_Y == golden metadata maxY",
                int64(doc, "maxY") == NoiseRouterContract.GOLDEN_MAX_Y);
        check("worldgen POINT_COUNT == golden metadata pointCount",
                int64(doc, "pointCount") == NoiseRouterContract.POINT_COUNT);
        check("worldgen FIELD_COUNT == golden metadata fieldCount",
                int64(doc, "fieldCount") == NoiseRouterContract.FIELD_COUNT);

        // contract SAMPLE_POINTS vs golden rows (x,y,z), order-sensitive
        List<TdValue> rows = doc.elements();
        boolean pointsMatch = rows.size() == NoiseRouterContract.POINT_COUNT
                && rows.size() == NoiseRouterContract.SAMPLE_POINTS.size();
        for (int i = 0; pointsMatch && i < rows.size(); i++) {
            TdValue v = rows.get(i);
            if (!(v instanceof TdTable r)) {
                pointsMatch = false;
                break;
            }
            Long x = int64(r, "x");
            Long y = int64(r, "y");
            Long z = int64(r, "z");
            NoiseRouterContract.SamplePoint p = NoiseRouterContract.SAMPLE_POINTS.get(i);
            pointsMatch = x != null && y != null && z != null
                    && x == p.x() && y == p.y() && z == p.z();
        }
        check("worldgen SAMPLE_POINTS 12 points == golden rows (x,y,z, order-sensitive)", pointsMatch);

        // golden doc fields list vs contract FIELD_NAMES, verbatim
        TdValue fields = doc.get("fields");
        boolean fieldsMatch = false;
        if (fields instanceof TdTable ft) {
            List<TdValue> names = ft.elements();
            fieldsMatch = names.size() == NoiseRouterContract.FIELD_NAMES.length;
            for (int i = 0; fieldsMatch && i < names.size(); i++) {
                fieldsMatch = NoiseRouterContract.FIELD_NAMES[i].equals(names.get(i).asString());
            }
        }
        check("worldgen golden fields list == contract FIELD_NAMES (verbatim)", fieldsMatch);

        // anchor bridge: engine mirror eval -> bitPattern vs golden stored bits (fixed points)
        if (rows.size() == NoiseRouterContract.POINT_COUNT) {
            NoiseRouter router = NoiseRouter.overworld(NoiseRouterContract.GOLDEN_SEED,
                    NoiseRouterContract.GOLDEN_MIN_Y, NoiseRouterContract.GOLDEN_MAX_Y);
            int fieldIdx = NoiseRouterContract.indexOfField(BRIDGE_FIELD);
            for (int pi : BRIDGE_POINTS) {
                TdValue row = rows.get(pi);
                Long stored = row instanceof TdTable r ? int64(r, BRIDGE_FIELD) : null;
                NoiseRouterContract.SamplePoint p = NoiseRouterContract.SAMPLE_POINTS.get(pi);
                long actual = NoiseRouterContract.bitPattern(
                        router.fieldAt(fieldIdx).eval((double) p.x(), (double) p.y(), (double) p.z()));
                boolean ok = stored != null && stored == actual;
                check("worldgen bridge: golden point " + pi + " " + BRIDGE_FIELD + " bits == engine mirror", ok);
                if (!ok) {
                    System.out.println("  [ContractProbe] bridge point=" + pi + " ("
                            + p.x() + "," + p.y() + "," + p.z() + ") expectedBits=" + stored
                            + " actualBits=" + actual);
                }
            }
        }
    }

    // ---------- (b) rule registry contract ----------

    private static void ruleContract() {
        RuleRegistry.clear();
        RuleSet layer1 = RuleSet.of(List.of(new Rule("a", "1"), new Rule("b", "2")));
        RuleSet layer2 = RuleSet.of(List.of(new Rule("c", "3"), new Rule("d", "4")));
        RuleRegistry.register(layer1);
        RuleRegistry.register(layer2);

        // fixed registration order
        List<String> order1 = List.copyOf(RuleRegistry.registered().keySet());
        List<String> order2 = List.copyOf(RuleRegistry.registered().keySet());
        check("RuleRegistry registered() order fixed == [a,b,c,d]",
                order1.equals(order2) && order1.equals(List.of("a", "b", "c", "d")));
        List<RuleSet> layers = RuleRegistry.layers();
        check("RuleRegistry layers() registration order fixed (2 layers)",
                layers.size() == 2 && layers.get(0) == layer1 && layers.get(1) == layer2);
        check("RuleRegistry layers() repeated call identical", RuleRegistry.layers().equals(layers));

        // duplicate-key second registration rejected, registry unchanged
        boolean dupRejected = expectIae(() ->
                RuleRegistry.register(RuleSet.of(List.of(new Rule("a", "dup")))));
        check("RuleRegistry duplicate-key second register rejected (IAE)", dupRejected);
        check("RuleRegistry rejected register leaves layers unchanged",
                RuleRegistry.layers().size() == 2);

        // resolve: later layer wins, then overrides win everything
        RuleSet overrides = RuleSet.of(List.of(new Rule("b", "20"), new Rule("c", "30"), new Rule("e", "5")));
        Map<String, String> eff = RuleRegistry.resolve(List.of(layer1, layer2), overrides);
        Map<String, String> expected = new LinkedHashMap<>();
        expected.put("a", "1");
        expected.put("b", "20");
        expected.put("c", "30");
        expected.put("d", "4");
        expected.put("e", "5");
        check("RuleRegistry.resolve: later layer wins, overrides win (order + values)",
                eff.equals(expected) && List.copyOf(eff.keySet()).equals(List.of("a", "b", "c", "d", "e")));

        // resolveTiered vs DatapackRules.resolveTiered (values via TdValue.str)
        RuleSet global = RuleSet.of(List.of(new Rule("a", "1"), new Rule("b", "2")));
        RuleSet ov = RuleSet.of(List.of(new Rule("b", "20"), new Rule("c", "3")));
        Map<String, String> apiTiered = RuleRegistry.resolveTiered(global, ov);
        Map<String, TdValue> gTd = new LinkedHashMap<>();
        gTd.put("a", TdValue.str("1"));
        gTd.put("b", TdValue.str("2"));
        Map<String, TdValue> oTd = new LinkedHashMap<>();
        oTd.put("b", TdValue.str("20"));
        oTd.put("c", TdValue.str("3"));
        Map<String, TdValue> engineTiered = DatapackRules.resolveTiered(gTd, oTd);
        boolean sameKeys = apiTiered.keySet().equals(engineTiered.keySet());
        boolean sameVals = apiTiered.size() == engineTiered.size();
        for (Map.Entry<String, String> e : apiTiered.entrySet()) {
            TdValue v = engineTiered.get(e.getKey());
            sameVals = sameVals && v != null && v.toString().equals(e.getValue());
        }
        check("RuleRegistry.resolveTiered == DatapackRules.resolveTiered (same key set + override relation)",
                sameKeys && sameVals);
        check("RuleRegistry.resolveTiered: overrides win conflicts, global keys preserved",
                apiTiered.equals(Map.of("a", "1", "b", "20", "c", "3")));

        // render char-identical to DatapackRules.render (scalar scenario)
        Map<String, String> effScalar = new LinkedHashMap<>();
        effScalar.put("b", "20");
        effScalar.put("a", "1");
        effScalar.put("c", "3");
        String apiRender = RuleRegistry.render(effScalar);
        Map<String, TdValue> effTd = new LinkedHashMap<>();
        effTd.put("b", TdValue.str("20"));
        effTd.put("a", TdValue.str("1"));
        effTd.put("c", TdValue.str("3"));
        String engineRender = DatapackRules.render(effTd);
        check("RuleRegistry.render == DatapackRules.render char-by-char (scalar)",
                apiRender.equals(engineRender) && apiRender.equals("a=1; b=20; c=3"));
        check("RuleRegistry.render(empty) == DatapackRules.render(empty) == \"\"",
                RuleRegistry.render(Map.of()).equals("") && DatapackRules.render(Map.of()).equals(""));

        RuleRegistry.clear();
    }

    // ---------- (c) exporter registry contract ----------

    private static void exporterContract() {
        ExporterRegistry.clear();
        ExporterRegistry.register(ExportKindSpec.worldPack(), port(ExportKindSpec.worldPack()));
        ExporterRegistry.register(ExportKindSpec.save(), port(ExportKindSpec.save()));
        ExporterRegistry.register(ExportKindSpec.datapack(), port(ExportKindSpec.datapack()));

        // registration order == [world, save, datapack], one-by-one equal to engine forms
        List<String> forms = ExporterRegistry.forms();
        boolean formsMatch = forms.size() == ExportKind.values().length;
        for (int i = 0; formsMatch && i < forms.size(); i++) {
            formsMatch = forms.get(i).equals(ExportKind.values()[i].form());
        }
        check("ExporterRegistry forms() == [world, save, datapack] (registration order, == engine ExportKind.form())",
                formsMatch && forms.equals(List.of("world", "save", "datapack")));

        List<ExportKindSpec> kinds = ExporterRegistry.kinds();
        check("ExporterRegistry kinds() registration order (3 built-ins)",
                kinds.size() == 3
                        && kinds.get(0).form().equals("world")
                        && kinds.get(1).form().equals("save")
                        && kinds.get(2).form().equals("datapack"));

        // duplicate form second registration rejected
        boolean dupRejected = expectIae(() ->
                ExporterRegistry.register(ExportKindSpec.worldPack(), port(ExportKindSpec.worldPack())));
        check("ExporterRegistry duplicate form second register rejected (IAE)", dupRejected);

        // port() hit / miss
        ExportPortSpec wp = ExporterRegistry.port(ExportKindSpec.worldPack());
        check("ExporterRegistry port() hit for world", wp != null && wp.kind().form().equals("world"));
        check("ExporterRegistry port() unregistered form -> null",
                ExporterRegistry.port(new FakeKind("unknown")) == null);
        check("ExporterRegistry port(null) -> null", ExporterRegistry.port(null) == null);

        ExporterRegistry.clear();
    }

    // ---------- (d) event contract ----------

    private static void eventContract() {
        EventStream stream = new EventStream();
        stream.append(EventEnvelope.fromText(0, EventKind.LOG, "text/plain", "first"));
        stream.append(EventEnvelope.fromText(1, EventKind.LOG, "text/plain", "second"));
        stream.append(EventEnvelope.fromText(2, EventKind.LIFECYCLE, "text/plain", "third"));
        check("EventStream append increasing seq passes (size=3, lastSeq=2)",
                stream.size() == 3 && stream.lastSeq() == 2);

        boolean replayRejected = expectIae(() ->
                stream.append(EventEnvelope.fromText(1, EventKind.LOG, "text/plain", "replay")));
        check("EventStream seq<=lastSeq rejected (replay seq=1, IAE)", replayRejected);
        boolean sameSeqRejected = expectIae(() ->
                stream.append(EventEnvelope.fromText(2, EventKind.LOG, "text/plain", "same")));
        check("EventStream same-seq re-append rejected (IAE)", sameSeqRejected);
        boolean negRejected = expectIae(() ->
                stream.append(EventEnvelope.fromText(-1, EventKind.LOG, "text/plain", "neg")));
        check("EventStream negative seq rejected (IAE)", negRejected);
        check("EventStream rejected events never advance state",
                stream.size() == 3 && stream.lastSeq() == 2);

        // events() replay order fixed
        List<EventEnvelope> replay1 = stream.events();
        List<EventEnvelope> replay2 = stream.events();
        check("EventStream events() replay order fixed [0,1,2]",
                replay1.equals(replay2)
                        && replay1.get(0).seq() == 0
                        && replay1.get(1).seq() == 1
                        && replay1.get(2).seq() == 2);

        // envelope text payload round-trip
        EventEnvelope txt = EventEnvelope.fromText(10, EventKind.SESSION_STATE_CHANGED,
                "text/plain", "hello world");
        check("EventEnvelope fromText -> textPayload round-trip",
                txt.textPayload().equals("hello world")
                        && txt.seq() == 10
                        && txt.kind() == EventKind.SESSION_STATE_CHANGED
                        && txt.payloadType().equals("text/plain"));

        // defensive copy: mutating the input array must not leak into the envelope
        byte[] input = "streamed".getBytes(StandardCharsets.UTF_8);
        EventEnvelope env = EventEnvelope.from(3, EventKind.LOG, "text/plain", input);
        input[0] = 'X';
        check("EventEnvelope input-array mutation does not affect envelope",
                env.textPayload().equals("streamed"));

        // defensive copy: mutating the returned payload must not leak into the envelope
        byte[] out = env.payload();
        out[0] = 'Y';
        check("EventEnvelope returned-payload mutation does not affect envelope",
                env.textPayload().equals("streamed"));

        // defensive copy into the stream: mutating the handed-in array must not leak into the stream
        byte[] sInput = "in-stream".getBytes(StandardCharsets.UTF_8);
        EventEnvelope sEnv = EventEnvelope.from(4, EventKind.LOG, "text/plain", sInput);
        stream.append(sEnv);
        sInput[0] = 'Z';
        check("EventStream holds a defensive copy (input mutation does not affect stream)",
                stream.events().get(3).textPayload().equals("in-stream"));
    }

    // ---------- helpers ----------

    /** Reads the golden asset text from the classpath. */
    private static String readResource(String resource) {
        try (InputStream in = ContractProbe.class.getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalStateException("missing probe resource: " + resource);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read probe resource " + resource, e);
        }
    }

    /** Long value of a key, or null when absent. */
    private static Long int64(TdTable t, String key) {
        TdValue v = t.get(key);
        return v == null ? null : v.asInt();
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

    /** Minimal port fixture: serves a kind with a fixed description. */
    private record Port(ExportKindSpec kind, String description) implements ExportPortSpec {
    }

    /** Minimal unregistered kind fixture. */
    private record FakeKind(String form) implements ExportKindSpec {
    }

    private static ExportPortSpec port(ExportKindSpec kind) {
        return new Port(kind, "desc:" + kind.form());
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
