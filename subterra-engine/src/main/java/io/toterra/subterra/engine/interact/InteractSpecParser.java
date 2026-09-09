package io.toterra.subterra.engine.interact;

import io.toterra.subterra.engine.config.Td;
import io.toterra.subterra.engine.config.TdTable;
import io.toterra.subterra.engine.config.TdValue;
import io.toterra.subterra.engine.schema.Schema;
import io.toterra.subterra.engine.schema.SchemaCodec;
import io.toterra.subterra.engine.schema.SchemaReport;
import io.toterra.subterra.engine.schema.SchemaViolationException;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.TreeSet;

/**
 * 交互规范与 td 的编解码（p.2.14.1）—— 以 td 文档为交互规范的单一事实源，复用 p.2.12
 * {@code engine.schema} 的 schema 定型 + validate 语义。解析先以 {@link InteractSpec#schemaTd()}
 * （固定的权威 spec schema）经 {@link SchemaCodec} 定型，再按 schema validate 根形状，随后按固定
 * 文档序逐表面提取。
 * <p>
 * <b>spec td 文档契约定死（权威形态）</b>（{@code //} 为注释，行序即序）：
 * <pre>
 *   // 根表固定两键：name = string，surfaces = list（surfaces 是嵌套表数组）
 *   name  = "&lt;spec-name&gt;",
 *   surfaces = [
 *       [   // 每个表面子表，固定键：target / kind / actions / attitude
 *           target  = "&lt;target-text&gt;",          ; 原样照抄，必填
 *           kind    = "&lt;kind&gt;",               ; InteractKind.tdName，必填
 *           actions = [ "&lt;action&gt;", ... ],    ; InteractAction.tdName，必填，行序无意义（TreeSet 序）
 *           attitude= "&lt;attitude&gt;",           ; Attitude.tdName，必填
 *       ],
 *       [ ... ],
 *   ],
 * </pre>
 * 违约逐级归一到受检 {@link InteractViolationException}（固定原因）：{@code MISSING_NAME}（name 缺失或空）、
 * {@code SHAPE}（根字段 shape 违约 / 表面字段缺失或不含嵌套表 / 非 td）、{@code UNKNOWN_KIND}（kind 文本未知）、
 * {@code UNKNOWN_ACTION}（action 文本未知）、{@code MISSING_ATTITUDE}（attitude 缺失）、
 * {@code UNKNOWN_ATTITUDE}（attitude 文本未知）。{@code null} 输入属程序错误抛
 * {@link IllegalArgumentException}。
 * <p>
 * 确定性往返是验收判据：{@code parse(toTd(spec))} 与 spec 恒等（actions 恒等即 TreeSet 序确定），且同一
 * spec 两次 {@code toTd} 逐字节一致（编解码皆经由 {@link Td}，键序与表面序固定、actions 按枚举序）。
 * <p>
 * Interaction-spec &amp;lt;-&amp;gt; td codec (p.2.14.1) — the td document is the single source of truth for an
 * interaction spec, reusing the p.2.12 {@code engine.schema} schema-typing + validate semantics. Parsing
 * first types with {@link InteractSpec#schemaTd()} (the fixed authoritative spec schema) via
 * {@link SchemaCodec}, validates the root shape against that schema, then extracts each surface in fixed
 * document order.
 * <p>
 * <b>Spec-td document contract pinned (authoritative shape)</b> ({@code //} is a comment; line order is
 * the order):
 * <pre>
 *   // the root table has exactly two keys: name = string, surfaces = list (an array of nested tables)
 *   name  = "&lt;spec-name&gt;",
 *   surfaces = [
 *       [   // each surface sub-table, fixed keys: target / kind / actions / attitude
 *           target  = "&lt;target-text&gt;",          ; verbatim, required
 *           kind    = "&lt;kind&gt;",               ; an InteractKind.tdName, required
 *           actions = [ "&lt;action&gt;", ... ],    ; InteractAction.tdName, required; line order is irrelevant (TreeSet order)
 *           attitude= "&lt;attitude&gt;",           ; an Attitude.tdName, required
 *       ],
 *       [ ... ],
 *   ],
 * </pre>
 * Violations normalise level by level to the checked {@link InteractViolationException} (fixed reason):
 * {@code MISSING_NAME} (name missing or blank), {@code SHAPE} (root-shape violation / a surface field
 * missing or not a nested table / not parseable td), {@code UNKNOWN_KIND} (unknown kind text),
 * {@code UNKNOWN_ACTION} (unknown action text), {@code MISSING_ATTITUDE} (attitude missing),
 * {@code UNKNOWN_ATTITUDE} (unknown attitude text). A {@code null} input is a program error and raises
 * {@link IllegalArgumentException}.
 * <p>
 * A deterministic round-trip is the acceptance criterion: {@code parse(toTd(spec))} equals the spec
 * (equal actions means the TreeSet order is deterministic), and two {@code toTd} calls for the same spec
 * are byte-identical (both codecs run through {@link Td}, with fixed key and surface order, and actions
 * in enum order).
 */
public final class InteractSpecParser {

    private InteractSpecParser() {
    }

    /**
     * 将一份交互规范 td 文档解析为 {@link InteractSpec}。解析层违约抛出；建模不变量由
     * {@link InteractSurface}/{@link InteractSpec} 紧凑构造器强制。Parses an interaction-spec td document
     * into an {@link InteractSpec}. Document-level violations throw; modeling invariants are enforced by the
     * compact constructors of {@link InteractSurface}/{@link InteractSpec}.
     *
     * @param tdSource 交互规范 td 文本（null → {@link IllegalArgumentException}）/ the interaction-spec td text (null → {@link IllegalArgumentException}).
     * @return 解析出的交互规范 / the parsed interaction spec.
     * @throws InteractViolationException 文档违约 / a document-level violation.
     */
    public static InteractSpec parse(String tdSource) throws InteractViolationException {
        Objects.requireNonNull(tdSource, "interaction-spec td source must not be null");

        final TdTable data;
        try {
            data = Td.parse(tdSource);
        } catch (IllegalArgumentException e) {
            throw new InteractViolationException(InteractViolationException.SHAPE,
                    "not parseable td: " + e.getMessage());
        }

        final Schema schema;
        try {
            schema = SchemaCodec.parse(InteractSpecParser.schemaTd());
        } catch (SchemaViolationException e) {
            throw new IllegalStateException("fixed spec schema is always valid", e);
        }

        SchemaReport report = schema.validate(data);
        if (!report.valid()) {
            throw new InteractViolationException(InteractViolationException.SHAPE,
                    report.reason() + "@" + report.fieldPath());
        }

        String name = data.get("name").asString().trim();
        if (name.isEmpty()) {
            throw new InteractViolationException(InteractViolationException.MISSING_NAME,
                    "blank name");
        }

        TdTable surfacesTbl = (TdTable) data.get("surfaces");
        List<TdValue> elements = surfacesTbl.elements();
        List<InteractSurface> surfaces = new ArrayList<>(elements.size());
        for (int i = 0; i < elements.size(); i++) {
            surfaces.add(parseSurface(elements.get(i), i));
        }
        return new InteractSpec(name, surfaces);
    }

    /** 解析单个表面（按固定文档序）。Parses a single surface (in fixed document order). */
    private static InteractSurface parseSurface(TdValue raw, int index) throws InteractViolationException {
        if (!(raw instanceof TdTable s)) {
            throw new InteractViolationException(InteractViolationException.SHAPE,
                    "surface[" + index + "] is not a nested table");
        }
        String target = s.get("target") == null ? "" : s.get("target").asString().trim();
        if (target.isEmpty()) {
            throw new InteractViolationException(InteractViolationException.SHAPE,
                    "surface[" + index + "] missing target");
        }
        InteractKind kind;
        if (s.get("kind") == null) {
            throw new InteractViolationException(InteractViolationException.SHAPE,
                    "surface[" + index + "] missing kind");
        }

        TdValue kindVal = s.get("kind");
        if (!(kindVal instanceof TdValue.Scalar)) {
            throw new InteractViolationException(InteractViolationException.SHAPE,
                    "surface[" + index + "] kind is not a scalar");
        }
        String kindText = kindVal.asString();
        kind = InteractKind.fromTd(kindText);
        if (kind == null) {
            throw new InteractViolationException(InteractViolationException.UNKNOWN_KIND,
                    "surface[" + index + "] kind=" + kindText);
        }

        TdValue actionsVal = s.get("actions");
        if (actionsVal == null) {
            throw new InteractViolationException(InteractViolationException.SHAPE,
                    "surface[" + index + "] missing actions");
        }
        TreeSet<InteractAction> actions = new TreeSet<>();
        for (TdValue a : actionsVal.asList()) {
            String aText = a.asString();
            InteractAction action = InteractAction.fromTd(aText);
            if (action == null) {
                throw new InteractViolationException(InteractViolationException.UNKNOWN_ACTION,
                        "surface[" + index + "] action=" + aText);
            }
            actions.add(action);
        }

        TdValue attVal = s.get("attitude");
        if (attVal == null) {
            throw new InteractViolationException(InteractViolationException.MISSING_ATTITUDE,
                    "surface[" + index + "]");
        }
        String attText = attVal.asString();
        Attitude attitude = Attitude.fromTd(attText);
        if (attitude == null) {
            throw new InteractViolationException(InteractViolationException.UNKNOWN_ATTITUDE,
                    "surface[" + index + "] attitude=" + attText);
        }

        return new InteractSurface(kind, target, actions, attitude);
    }

    /**
     * 将 {@link InteractSpec} 序列化为 td 文本（2 空格缩进，经 {@link Td#write}），与
     * {@link #parse(String)} 逐字节往返恒定，同一 spec 两次调用逐字节一致。Serializes an
     * {@link InteractSpec} to td text (2-space indent, via {@link Td#write}), byte-stable round-tripping
     * with {@link #parse(String)}; two calls for the same spec are byte-identical.
     *
     * @param spec 待序列化的交互规范 / the interaction spec to serialize.
     * @return td 文本 / the td text.
     */
    public static String toTd(InteractSpec spec) {
        Objects.requireNonNull(spec, "spec must not be null");
        TdTable.Builder b = TdTable.builder();
        b.put("name", spec.name());
        TdTable.Builder surfaces = TdTable.builder();
        for (InteractSurface s : spec.surfaces()) {
            TdTable.Builder sb = TdTable.builder();
            sb.put("target", s.target());
            sb.put("kind", s.kind().tdName());
            TdTable.Builder actions = TdTable.builder();
            for (InteractAction a : s.actions()) {
                actions.element(TdValue.str(a.tdName()));
            }
            sb.put("actions", actions.build());
            sb.put("attitude", s.attitude().tdName());
            surfaces.element(sb.build());
        }
        b.put("surfaces", surfaces.build());
        return Td.write(b.build());
    }

    /**
     * 权威 spec schema 的 td 文本（等价 {@code InteractSpec.schemaTd()}，单一规范来源，每次返回逐字节一致）。
     * The authoritative spec-schema td text (equivalent to {@code InteractSpec.schemaTd()}, the single
     * canonical source, returning byte-identical text each call).
     *
     * @return 权威 spec schema 的 td 文本 / the authoritative spec-schema td text.
     */
    public static String schemaTd() {
        return "root = \"table\",\n"
                + "fields = [\n"
                + "    name = \"string\",\n"
                + "    surfaces = \"list\",\n"
                + "],\n";
    }
}


