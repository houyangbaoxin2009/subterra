// Async I/O queue design derives from C2ME (MIT, Copyright (c) ishland, https://github.com/ishland/C2ME);
// implementation is original Subterra code.
package io.toterra.subterra.engine.worldgen.async.io;

import java.util.Arrays;
import java.util.Objects;

/**
 * 一次延后写单元（p.2.6.4）：确定性身份 = 有序 {code key}，负载 = {code bytes}。主题把"键"视作
 * 目标文件/槽的确定性标识（哪个键先入队，就会在刷新时排在前面）；负载即要落盘的字节。消费者（真正
 * 写到 zd/save 栈的 sink）由 {@link AsyncIoQueue.of} 单独注入，故此核心保持存储无关。记录不可变；
 * payload 防御性拷贝，避免并发制造者后来改写已入队字节。
 * <p>
 * A deferred write unit (p.2.6.4): deterministic identity = the ordered {@code key},
 * payload = {@code bytes}. The key is the deterministic identity of the target file /
 * slot (the earlier a key is enqueued, the earlier it sorts in a flush); bytes are the
 * payload to persist. The consumer (the actual writer to the zd/save stack) is injected
 * separately via {@link AsyncIoQueue.of}, so this core stays storage-agnostic. The record
 * is immutable; the payload is defensively copied so a concurrent producer cannot mutate
 * already-enqueued bytes.
 *
 * @param key   the deterministic, ordered target key.
 * @param bytes the payload bytes (defensively copied on construction and access).
 */
public record AsyncWrite(String key, byte[] bytes) {

    /**
     * 紧凑构造：key 非 null，payload 越界拷贝。Compact constructor: key non-null, payload
     * defensively copied so the stored array is never the caller's live buffer.
     */
    public AsyncWrite {
        Objects.requireNonNull(key, "key");
        bytes = bytes == null ? new byte[0] : bytes.clone();
    }

    @Override
    public byte[] bytes() {
        return bytes.clone();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof AsyncWrite other)) {
            return false;
        }
        return key.equals(other.key) && Arrays.equals(bytes, other.bytes);
    }

    @Override
    public int hashCode() {
        return 31 * key.hashCode() + Arrays.hashCode(bytes);
    }
}