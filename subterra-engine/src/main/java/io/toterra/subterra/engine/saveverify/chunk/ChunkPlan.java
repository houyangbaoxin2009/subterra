package io.toterra.subterra.engine.saveverify.chunk;

import java.util.List;

/**
 * 一份确定性 zd 区块划分计划（p.2.10.1）：受保护内容字节 {@code payload} + 按下标严格升序的
 * {@code chunks} 校验元数据表 + 固定切块大小 {@code chunkSize}。不可变：payload 在构造时被复制，
 * chunks 以不可变列表持有。
 * <p>
 * A deterministic zd-chunk partition plan (p.2.10.1): the protected payload bytes plus the
 * ascending-by-index chunk verification metadata table plus the fixed chunk size. Immutable:
 * {@code payload} is copied on construction and {@code chunks} is held as an unmodifiable list.
 *
 * <p>划分不变量（由 {@link ChunkVerifier#partition} 保证）：{@code chunks} 逐块 {@code index}
 * 严格升序且等于其在列表中的位置；每块 {@code offset = index * chunkSize}；除最后一块外
 * {@code length = chunkSize}，最后一块 {@code length = payload.len - offset}（可能 &lt; chunkSize）。
 * 空 payload → {@code chunks} 为空表。
 */
public final class ChunkPlan {

    private final byte[] payload;
    private final List<Chunk> chunks;
    private final int chunkSize;

    /**
     * 构造（payload 防御性复制；chunks 复制为不可变列表）。Constructs the plan (defensively
     * copies the payload; copies {@code chunks} into an unmodifiable list).
     */
    public ChunkPlan(byte[] payload, List<Chunk> chunks, int chunkSize) {
        byte[] src = payload == null ? new byte[0] : payload;
        this.payload = java.util.Arrays.copyOf(src, src.length);
        this.chunks = List.copyOf(chunks);
        this.chunkSize = chunkSize;
    }

    /** 受保护内容字节（自检时重新分块的输入）。Protected payload bytes (re-partitioned on self-check). */
    public byte[] payload() {
        return payload;
    }

    /** 校验元数据列表（按下标严格升序）。The chunk verification-metadata list (strictly index-ascending). */
    public List<Chunk> chunks() {
        return chunks;
    }

    /** 块数（= {@code chunks.size()}）。Number of chunks. */
    public int size() {
        return chunks.size();
    }

    /** 固定切块大小；最后一块可短于此。Fixed chunk size; the last chunk may be shorter. */
    public int chunkSize() {
        return chunkSize;
    }

    /**
     * 按下标取块；越界抛 {@link IndexOutOfBoundsException}。Returns the chunk at the given
     * index, throwing {@link IndexOutOfBoundsException} when out of range.
     */
    public Chunk chunkAt(int index) {
        return chunks.get(index);
    }
}