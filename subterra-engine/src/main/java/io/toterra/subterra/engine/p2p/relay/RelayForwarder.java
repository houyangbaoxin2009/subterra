package io.toterra.subterra.engine.p2p.relay;

import io.toterra.subterra.engine.p2p.NodeId;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * p.2.5.8 志愿 relay 兜底转发器核心（纯 JDK、无 socket、内存端语义）。一个 relay 节点持有「端点
 * 登记表」并对源节点的转发请求做兜底判定：
 * <ul>
 *   <li>顶点化直连不可达（打洞失败）时，源节点经 {@link #forward(ForwardRequest)} 提交请求，relay
 *       依据目标 token 判定；</li>
 *   <li>命中登记 → 把载荷投递给 {@link RelayEndpoint}（区域内存缓冲，模拟送达）→ {@code FORWARDED}；</li>
 *   <li>目标 relay 与自身不符 → {@code REJECTED}（token/relay 错配，不静默）；token 未登记 →
 *       {@code REJECTED}（不静默）；hop 超限（{@code > MAX_HOPS}）→ {@code DROPPED}(loop) 防环
 *       （同输入两次结果一致，确定性）；</li>
 *   <li>{@link #shutdown()} 清空登记表（关停钩子）；游戏内不实际持有连接时清空恒安全（no-op 语义）。</li>
 * </ul>
 * 登记与遍历确定有序：以 {@link TreeMap} 按 token 字典序存储，{@link #registeredTokens()} /
 * {@link #snapshot()} 为确定性遍历序（禁 O(n²)）。
 * <p>
 * p.2.5.8 Voluntary-relay fallback forwarder core (pure JDK, no socket, memory-endpoint semantics).
 * A relay node holds an "endpoint registry" and makes the fallback decision for an origin's forward
 * request:
 * <ul>
 *   <li>when direct delivery is unreachable (hole-punch failed), the origin submits a request via
 *       {@link #forward(ForwardRequest)} and the relay decides on the target token;</li>
 *   <li>a registered-token hit → the payload is delivered to the {@link RelayEndpoint} (a region
 *       buffer simulating delivery) → {@code FORWARDED};</li>
 *   <li>a target relay ≠ this relay → {@code REJECTED} (token/relay mismatch, not silent); an
 *       unregistered token → {@code REJECTED} (not silent); an over-limit hop ({@code > MAX_HOPS})
 *       → {@code DROPPED}(loop) loop protection (deterministic, same input → same result);</li>
 *   <li>{@link #shutdown()} clears the registry (the off/teardown hook); with no live in-game
 *       connection this clearing is always safe (no-op).</li>
 * </ul>
 * Registration and traversal are deterministically ordered: a {@link TreeMap} stores tokens in
 * lexicographic order, so {@link #registeredTokens()} / {@link #snapshot()} give a deterministic
 * traversal order (no O(n²)).
 */
public final class RelayForwarder {

    /** 尽力而为的最大 hop 计数（与 {@link ForwardRequest#MAX_HOPS} 一致）。 */
    public static final int DEFAULT_MAX_HOPS = 3;

    private final NodeId relayId;
    private final int maxHops;
    private final TreeMap<String, RelayEndpoint> table = new TreeMap<>();

    public RelayForwarder(NodeId relayId) {
        this(relayId, DEFAULT_MAX_HOPS);
    }

    public RelayForwarder(NodeId relayId, int maxHops) {
        if (relayId == null) {
            throw new NullPointerException("relayId");
        }
        this.relayId = relayId;
        this.maxHops = maxHops > 0 ? maxHops : DEFAULT_MAX_HOPS;
    }

    /** 本 relay 节点的可寻址标识。This relay's addressable node id. */
    public NodeId relayId() {
        return relayId;
    }

    /** 把某个 token 登记到一内存端点（重复登记覆盖，保持确定性）。Registers a token → endpoint. */
    public void register(String token, RelayEndpoint endpoint) {
        if (token == null || endpoint == null) {
            throw new NullPointerException("token and endpoint must not be null");
        }
        table.put(token, endpoint);
    }

    /** 注销 token；未登记的 token 注销无副作用（安全幂等）。Unregisters a token (idempotent). */
    public void unregister(String token) {
        if (token != null) {
            table.remove(token);
        }
    }

    /** 已登记 token 的确定性遍历序（字典序，TreeMap）。Deterministic lexicographic traversal. */
    public List<String> registeredTokens() {
        return List.copyOf(table.keySet());
    }

    /** 已登记条目数。Number of registered tokens. */
    public int registeredCount() {
        return table.size();
    }

    /**
     * 兜底转发核心：命中登记 → 投递并返回 {@code FORWARDED}；目标 relay 错配 → {@code REJECTED}
     * (mismatch)；token 未登记 → {@code REJECTED} (unregistered)；hop 超限 → {@code DROPPED}(loop)。
     * 失败从不静默，且判定对同一输入确定可复现。
     */
    public ForwardResult forward(ForwardRequest req) {
        if (req == null) {
            return ForwardResult.rejected("null request");
        }
        if (req.hop() > maxHops) {
            return ForwardResult.dropped("hop-limit loop > " + maxHops);
        }
        // token/relay 错配：目标 relay 与该 relay 不一致 → 明确拒绝（不静默）。
        if (!relayId.equals(req.relayId())) {
            return ForwardResult.rejected("token mismatch: relay "
                    + req.relayId().hex() + " != this " + relayId.hex());
        }
        RelayEndpoint ep = table.get(req.token());
        if (ep == null) {
            return ForwardResult.rejected("unregistered token " + req.token());
        }
        ep.deliver(req.payload());
        return ForwardResult.forwarded();
    }

    /**
     * 关停钩子：清空登记表并返回被清除的登记数。内存端点不持有真实连接，恒安全（游戏内无连接也安全）。
     * Teardown hook: clears the registry and returns how many registrations were cleared. Memory
     * endpoints hold no real connection, so this is always safe (also when nothing is registered).
     */
    public int shutdown() {
        int n = table.size();
        table.clear();
        return n;
    }

    /** 只读快照（共 token，确定性字典序）。Read-only snapshot (deterministic lexicographic order). */
    public Map<String, RelayEndpoint> snapshot() {
        return Collections.unmodifiableMap(new TreeMap<>(table));
    }
}