package io.toterra.subterra.engine.p2p.relay;

/**
 * p.2.5.8 端点登记表的值侧：一个「内存端点句柄」，relay 把目标 token 命中后的转发载荷投递给它。
 * 纯内存语义（无 socket / 无线程 / 无网络 IO——真实投递是 runtime 壳的职责）：端点把到达的载荷
 * 字节接收进自己的内存缓冲（区域缓冲 / 模拟送达），上层据此做逐字节校验。转发目标以
 * {@code token} 在 {@link RelayForwarder} 的登记表中标识。
 * <p>
 * p.2.5.8 the value side of the endpoint registry: an "in-memory endpoint handle" to which a relay
 * delivers the forwarded payload after a token registration is hit. Pure in-memory semantics (no
 * socket / thread / network IO — real delivery is the runtime shell's job): the endpoint receives the
 * arriving payload bytes into its own buffer (a region buffer simulating delivery) for byte-for-byte
 * validation upstream. The delivery target is identified by {@code token} in the
 * {@link RelayForwarder} registry.
 */
@FunctionalInterface
public interface RelayEndpoint {

    /**
     * 把一份转发载荷投递给该内存端点。接收端按自身语义缓冲/消费字节，可同步保持逐字节一致性。
     * Delivers one forwarded payload to this in-memory endpoint. The receiver buffers/consumes the
     * bytes per its own semantics, keeping byte-for-byte consistency.
     *
     * @param payload the forwarded payload bytes (never {@code null}, may be empty)
     */
    void deliver(byte[] payload);
}