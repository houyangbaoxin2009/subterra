package io.toterra.subterra.engine.config;

import org.tielang.zd.ZdDocWriter;
import org.tielang.zd.ZdRow;
import org.tielang.zd.ZdTrees;
import org.tielang.zd.ZdNode;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * td 树桥（p.2.3.8）：把 Subterra 的 {@link TdTable}/{@link TdValue} 树接进独立库
 * {@code org.tielang.zd}（zd-java）的树级读写。字节层编解码全部下沉 zd-java——本桥只做
 * 「Subterra 树 ⇄ 库行」的形态转换（DFS 先序平铺语义与原 engine.zd 逐字节一致，字节面
 * 不变）。库的行级/多段/列式/schema/图容器 API 直接用 {@code org.tielang.zd.*}。
 * <p>
 * The td tree bridge (p.2.3.8): adapts the Subterra {@link TdTable}/{@link TdValue} tree
 * into the standalone library {@code org.tielang.zd} (zd-java) tree-level read/write.
 * All byte-level codec work now lives in zd-java — this bridge only converts between the
 * Subterra tree shape and the library rows (the DFS preorder flatten semantics are
 * byte-identical to the former engine.zd). The library's row-level / multi-segment /
 * columnar / schema / graph APIs are consumed directly as {@code org.tielang.zd.*}.
 */
public final class TdZd {

    private TdZd() {
    }

    /**
     * 以 DFS 先序把 {@code root} 树平铺为库行再按 v3 写出（Subterra 缺省写路径）。
     * Flattens the {@code root} tree in DFS preorder into library rows and writes them
     * as v3 (the Subterra default write path).
     */
    public static byte[] writeTree(int flags, TdTable root) {
        return ZdDocWriter.write(flags, flatten(root));
    }

    /**
     * 按 v2 写出（v2 兼容路径）。Writes as v2 (the compatibility path).
     */
    public static byte[] writeTreeV2(int flags, TdTable root) {
        return ZdDocWriter.writeV2(flags, flatten(root));
    }

    /**
     * 按带索引 footer 的 v3 写出。Writes as an index-footered v3 document.
     */
    public static byte[] writeTreeIndexed(int flags, TdTable root) {
        return ZdDocWriter.writeIndexed(flags, flatten(root));
    }

    /**
     * 逆推 zd 载荷为一棵 {@link TdTable}（根必须是表；经库行层往返）。
     * Reconstructs a {@link TdTable} from a zd payload (the root must be a table; via
     * the library row layer).
     */
    public static TdTable readTree(byte[] data) {
        ZdNode root = ZdTrees.buildNode(org.tielang.zd.ZdVolume.readRows(data));
        if (root instanceof ZdNode.Table t) {
            return fromLib(t);
        }
        throw new IllegalArgumentException("zd root is not a table (scalar root)");
    }

    /**
     * Subterra 树 → 库行（DFS 先序：先具名后裸元素，bool 折叠 0/1）。
     * Subterra tree → library rows (DFS preorder: named first then bare; bool folded 0/1).
     */
    public static List<ZdRow> flatten(TdTable root) {
        List<ZdRow> rows = new ArrayList<>();
        Deque<TdValue> nodes = new ArrayDeque<>();
        Deque<String> keys = new ArrayDeque<>();
        nodes.push(root);
        keys.push("");
        while (!nodes.isEmpty()) {
            TdValue node = nodes.pop();
            String key = keys.pop();
            if (node instanceof TdTable t) {
                List<String> named = t.keys();
                List<TdValue> elems = t.elements();
                long child = (long) named.size() + elems.size();
                rows.add(new ZdRow(0, key, 0L, 0.0, "", child));
                // Push children in reverse so the first desired child pops first.
                for (int i = (int) child - 1; i >= 0; i--) {
                    if (i < named.size()) {
                        String k = named.get(i);
                        nodes.push(t.get(k));
                        keys.push(k);
                    } else {
                        nodes.push(elems.get(i - named.size()));
                        keys.push("");
                    }
                }
            } else {
                rows.add(scalarRow(key, (TdValue.Scalar) node));
            }
        }
        return rows;
    }

    private static ZdRow scalarRow(String key, TdValue.Scalar s) {
        return switch (s.kind()) {
            case STRING -> new ZdRow(1, key, 0L, 0.0, s.str(), 0);
            case INT -> new ZdRow(2, key, s.i(), 0.0, "", 0);
            case FLOAT -> new ZdRow(3, key, 0L, s.f(), "", 0);
            case BOOL -> new ZdRow(2, key, s.b() ? 1L : 0L, 0.0, "", 0);
        };
    }

    /** 库表节点 → Subterra {@link TdTable}（具名 + 裸元素序保持）。 /
     *  Library table node → Subterra {@link TdTable} (named + bare order preserved). */
    private static TdTable fromLib(ZdNode.Table t) {
        TdTable.Builder b = TdTable.builder();
        for (String k : t.keys()) {
            b.put(k, fromLibValue(t.get(k)));
        }
        for (ZdNode e : t.elements()) {
            b.element(fromLibValue(e));
        }
        return b.build();
    }

    private static TdValue fromLibValue(ZdNode v) {
        if (v instanceof ZdNode.Table t) {
            return fromLib(t);
        }
        ZdNode.Scalar s = (ZdNode.Scalar) v;
        return switch (s.kind()) {
            case STRING -> TdValue.str(s.str());
            case INT -> TdValue.of(s.i());
            case FLOAT -> TdValue.of(s.f());
            case BOOL -> TdValue.of(s.b());
        };
    }
}
