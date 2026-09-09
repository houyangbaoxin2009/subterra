package io.toterra.subterra.engine.interact;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 不可变交互规范 —— p.2.14.1 世界内交互规范的根模型：一份命名规范承载若干按<b>固定文档序</b>排列的
 * {@link InteractSurface}。提供 {@link #surfacesOf(InteractKind)}（按文档序遍历、只取指定种类的表面）、
 * {@link #schemaTd()}（本类自带的<b>固定 spec schema td 文本</b>，是权威 spec 形状）、
 * {@link #td()}（确定性序列化为 td 文本）与 {@link #toString()}（确定性，输出即 {@link #td()}）。
 * 词汇面仅限世界内表达，无任何系统层名词。
 * <p>
 * Immutable interaction spec — the root model of a p.2.14.1 in-world interaction spec: a named spec
 * carries several {@link InteractSurface} in <b>fixed document order</b>. It offers
 * {@link #surfacesOf(InteractKind)} (traversing in document order, keeping only surfaces of a given
 * kind), {@link #schemaTd()} (this class's own <b>fixed spec-schema td text</b>, the authoritative spec
 * shape), {@link #td()} (deterministic serialisation to td text), and {@link #toString()} (deterministic,
 * its output equals {@link #td()}). The vocabulary is confined to in-world expression with no
 * system-layer terms.
 *
 * @param name     规范名 / the spec name.
 * @param surfaces 交互表面（固定文档序，返回不可变列表）/ the interaction surfaces (fixed document order, returned as an immutable list).
 */
public record InteractSpec(String name, List<InteractSurface> surfaces) {

    /**
     * 紧凑构造：空值防御 + 表面列表防御拷贝（保持固定文档序）。Compact constructor: null-guards and a
     * defensive copy of the surface list (preserving the fixed document order).
     */
    public InteractSpec {
        Objects.requireNonNull(name, "spec name must not be null");
        Objects.requireNonNull(surfaces, "spec surfaces must not be null");
        surfaces = List.copyOf(surfaces);
    }

    /**
     * 固定序遍历：返回指定 {@code kind} 的交互表面，顺序即文档序（不重排）。Fixed-order traversal:
     * returns the interaction surfaces of the given {@code kind}, in document order (never reordered).
     *
     * @param kind 实体种类 / the entity kind.
     * @return 该种类的交互表面（文档序）/ the surfaces of that kind (in document order).
     */
    public List<InteractSurface> surfacesOf(InteractKind kind) {
        Objects.requireNonNull(kind, "kind must not be null");
        List<InteractSurface> out = new ArrayList<>();
        for (InteractSurface s : surfaces) {
            if (s.kind() == kind) {
                out.add(s);
            }
        }
        return List.copyOf(out);
    }

    /**
     * 固定的 spec schema td 文本 —— 权威 spec 形状。解析由 {@link InteractSpecParser} 先以本文本定型
     * （经 p.2.12 {@code SchemaCodec}）再校验+提取。本方法每次返回逐字节相同的规范文本（不引入随机/时序）。
     * <br>
     * 形状（固定）：
     * <pre>
     *   root   = "table"
     *   fields = name = "string", surfaces = "list"
     * </pre>
     * 其中 {@code surfaces} 元素为表面子表：{@code target = "&lt;text&gt;"}、{@code kind}、
     * {@code actions = [ "&lt;action&gt;", ... ]}、{@code attitude}（深层契约见
     * {@link InteractSpecParser} 类 Javadoc）。
     * <p>
     * The fixed spec-schema td text — the authoritative spec shape. {@link InteractSpecParser} first
     * types the document with this text (via the p.2.12 {@code SchemaCodec}), then validates and
     * extracts. This method returns byte-identical canonical text each call (no random / timing).
     */
    public String schemaTd() {
        return "root = \"table\",\n"
                + "fields = [\n"
                + "    name = \"string\",\n"
                + "    surfaces = \"list\",\n"
                + "],\n";
    }

    /**
     * 确定性序列化为 td 文本（等价 {@code InteractSpecParser.toTd(this)}）。Deterministic serialisation
     * to td text (equivalent to {@code InteractSpecParser.toTd(this)}).
     *
     * @return 该规范的 td 文本 / this spec's td text.
     */
    public String td() {
        return InteractSpecParser.toTd(this);
    }

    /**
     * 确定性字符串表达（等价 {@link #td()}）。Deterministic string representation (equivalent to
     * {@link #td()}).
     *
     * @return 该规范的 td 文本 / this spec's td text.
     */
    @Override
    public String toString() {
        return td();
    }
}


