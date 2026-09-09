package io.toterra.subterra.engine.save.migrate;

import java.util.List;
import java.util.Map;

/**
 * 通用 NBT 树的节点（p.2.3.2）。类型 {@code tag} 为 NBT tag id（1..12），{@code payload} 按 tag
 * 归类为纯 JDK 类型：BYTE→{@link Byte}、SHORT→{@link Short}、INT→{@link Integer}、LONG→{@link Long}、
 * FLOAT→{@link Float}、DOUBLE→{@link Double}、BYTE_ARRAY→{@code byte[]}、STRING→{@link String}、
 * LIST→{@code List<NbtNode>}、COMPOUND→{@code Map<String,NbtNode>}（保序）、INT_ARRAY→{@code int[]}、
 * LONG_ARRAY→{@code long[]}。只读视图，由 {@link NbtTreeReader} 构建。
 * <p>
 * A generic NBT-tree node (p.2.3.2). {@code tag} is the NBT tag id (1..12) and {@code payload} maps
 * to a plain JDK type per tag: BYTE→{@link Byte}, SHORT→{@link Short}, INT→{@link Integer}, LONG→
 * {@link Long}, FLOAT→{@link Float}, DOUBLE→{@link Double}, BYTE_ARRAY→{@code byte[]}, STRING→
 * {@link String}, LIST→{@code List<NbtNode>}, COMPOUND→{@code Map<String,NbtNode>} (ordered), INT_ARRAY→
 * {@code int[]}, LONG_ARRAY→{@code long[]}. Read-only view, built by {@link NbtTreeReader}.
 *
 * @param tag     NBT tag id (1..12).
 * @param payload 归类后的值，按 {@code tag} 定类型 / the normalised value, typed by {@code tag}.
 */
public record NbtNode(byte tag, Object payload) {
}