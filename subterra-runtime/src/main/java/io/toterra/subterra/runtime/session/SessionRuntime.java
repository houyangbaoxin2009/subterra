package io.toterra.subterra.runtime.session;

import java.nio.charset.StandardCharsets;

import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

import io.toterra.subterra.Subterra;
import io.toterra.subterra.engine.session.SessionCommand;
import io.toterra.subterra.engine.session.SessionCommandKind;
import io.toterra.subterra.engine.session.SessionFrame;
import io.toterra.subterra.engine.session.SessionFrameCodec;
import io.toterra.subterra.engine.session.SessionMachine;
import io.toterra.subterra.engine.session.SessionMachineException;
import io.toterra.subterra.engine.session.SessionMachineResult;
import io.toterra.subterra.engine.session.SessionMachineResult.Outcome;
import io.toterra.subterra.engine.session.SessionState;

/**
 * p.2.11.5 runtime 壳：把 p.2.11 engine.session「会话可编程」状态机桥到真实游戏生命周期（默认
 * {@code no-op}，渐进增强）。与 {@code runtime.saveverify.SaveVerifyRuntime}（p.2.10.5）完全同构：
 * 在 {@code ServerStartedEvent} 上于真实游戏 JVM 内做一次确定性「open → 错令牌拒绝 → 鉴权 →
 * EXEC/QUERY/PING 派发 → 帧往返 → BYE」纯数据闭环，证明会话状态机 / 帧协议入口在游戏 JVM 装载且
 * 合同保持。安全起见**只做纯数据闭环，不碰 MC 世界数据、不写任何文件、无 staging**：固定 sessionId
 * + 固定令牌构造 {@link SessionMachine}，全部数据来自引擎纯 JDK 类（{@code engine.session.*}）。
 * 门控 {@code -Dsubterra.probe.session}：不设则完全 no-op，对原版存档/世界生命周期零影响；也不与
 * 任何既有 marker 交互（async/sim/world/saveverify marker 与原路径保持字节原样）。门控开启时向
 * stdout 打确定性 marker（探针按前缀 {@code [Subterra session]} 匹配）。经
 * {@code @EventBusSubscriber} 自注册到 NeoForge 游戏总线（同 WorldRuntime/SimRuntime/SaveRuntime，
 * 无需改 Subterra.java）；默认纯 no-op 壳——任意异常被兜底为 {@code FAILED: ...} marker 并带异常栈
 * （供探针断言失败路径，失败不吞，绝不断言中断服务启动）。依赖铁律：runtime 可依赖 engine
 * （{@code engine.session}）与 MC 事件，禁依赖 migrate/devkit。
 * <p>
 * 本壳刻意不含会话接管、不连 MC 网络事件循环、不写文件；确定性与隔离性由纯数据闭环证明。marker
 * 内容固定格式（无时间戳无随机），供 {@code AsyncE2EProbe} 断言。
 * <p>
 * p.2.11.5 runtime shell: bridges the p.2.11 {@code engine.session} programmable-session state
 * machine to the real game lifecycle (off by default — vanilla save/world lifecycle untouched,
 * progressive enhancement). It mirrors {@code runtime.saveverify.SaveVerifyRuntime} (p.2.10.5): on
 * {@code ServerStartedEvent} it runs one deterministic pure-data closed loop inside the real game
 * JVM — {@code open → wrong-token reject → authenticate → EXEC/QUERY/PING dispatch → frame
 * round-trip → BYE} — proving the session state machine and its frame-protocol entry load in the
 * game JVM and their contracts hold. For safety this is a pure-data loop only — no MC world data is
 * touched, no file is written, no staging: a {@link SessionMachine} is built from a fixed sessionId
 * and the fixed token, and every datum comes from the engine pure-JDK classes ({@code engine.session.*}).
 * Gated by {@code -Dsubterra.probe.session}: absent → fully no-op (never touches existing markers —
 * the async/sim/world/saveverify markers plus their original-path lines stay byte-identical). When
 * gated on it prints deterministic markers to stdout (probes match by prefix
 * {@code [Subterra session]}). It self-registers on the NeoForge game bus via
 * {@code @EventBusSubscriber} (same style as WorldRuntime/SimRuntime/SaveRuntime — no Subterra.java
 * edit); a pure no-op shell by default — any throwable is caught and reported as a
 * {@code FAILED: ...} marker with the stack (so the probe can assert the failure path — the failure
 * is not swallowed, but it never breaks the boot gate). Dependency rule: the runtime may depend on
 * engine ({@code engine.session}) and MC events, never on migrate/devkit.
 * <p>
 * This shell deliberately owns no session takeover, wires no MC network event loop and writes no
 * file; the closure is proven by a pure data loop. The marker body is a fixed format (no timestamps,
 * no randomness), asserted by {@code AsyncE2EProbe}.
 */
@EventBusSubscriber(modid = Subterra.MODID, bus = EventBusSubscriber.Bus.GAME)
public final class SessionRuntime {

    /** 探针 marker 前缀 / probe marker prefix. */
    public static final String MARKER = "[Subterra session]";

    /** 固定会话标识：纯数据闭环的确定性身份（不连任何真实会话）。Fixed session id: the deterministic identity of the pure-data loop. */
    private static final String SESSION_ID = "toterra-demo";

    /** EXEC 的固定 UTF-8 载荷（p.2.11.2「key=value 行」契约）：tick=1\n day=1200。 */
    private static final String EXEC_PAYLOAD = "tick=1\nday=1200";

    private SessionRuntime() {
    }

    /**
     * 探针门控：{@code -Dsubterra.probe.session} 存在且非空且非 {@code 0}/{@code false} 即视为开启
     * （E2E 走 {@code -Psubterra.probe.session=1}，经根 build.gradle server run 块转发到游戏 JVM）。
     * 缺失 / 空白 → no-op。Probe gate: enabled when {@code -Dsubterra.probe.session} is present,
     * non-blank and not {@code 0}/{@code false} (E2E uses {@code -Psubterra.probe.session=1},
     * forwarded to the game JVM by the root server run block). Absent/blank → no-op.
     */
    private static boolean sessionProbeGated() {
        String v = System.getProperty("subterra.probe.session");
        if (v == null || v.isBlank()) {
            return false;
        }
        String t = v.trim();
        return !"0".equals(t) && !"false".equalsIgnoreCase(t);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onServerStarted(ServerStartedEvent event) {
        if (!sessionProbeGated()) {
            return; // no probe gate -> zero impact; vanilla save/world lifecycle untouched
        }
        try {
            print("session-shell-gate=on");
            runClosedLoop(event);
        } catch (Throwable t) {
            // never propagate past the event dispatch; the boot gate stays green.
            // The failure is NOT swallowed: report it with a stack for probe assertion.
            print("FAILED: " + t);
            t.printStackTrace();
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        // 无持有状态 -> 纯 no-op；对称保留空钩子（同 WorldRuntime/SaveVerifyRuntime）。
        // No held state -> pure no-op; empty hook kept for symmetry (as WorldRuntime/SaveVerifyRuntime).
        if (!sessionProbeGated()) {
            return;
        }
    }

    /**
     * The deterministic pure-data closed loop (no MC world data, no files, no staging): a fixed
     * sessionId + the fixed {@code DEFAULT_AUTH_TOKEN} build a {@link SessionMachine}; wrong-token
     * {@code authenticate} must reject with {@code AUTH_REJECT} (state unchanged) and the fixed token
     * must lead to {@code ACTIVE}; then {@code EXEC}(0) → OK, {@code QUERY}(1) → READBACK snapshot
     * carrying tick/day, {@code PING}(2) → OK pong are dispatched; one command is round-tripped
     * through {@code SessionFrameCodec#encodeFrame}/{@code decodeFrame} (proving the frame entry is
     * usable in the game JVM); finally {@code BYE}(3) → CLOSED. Prints the fixed PASS marker,
     * otherwise throws → FAILED marker.
     *
     * 确定性纯数据闭环（不碰 MC 世界数据、无文件、无 staging）：固定 sessionId + 固定
     * {@code DEFAULT_AUTH_TOKEN} 构造 {@link SessionMachine}；错令牌 {@code authenticate} 必须拒绝
     * （{@code AUTH_REJECT}，状态不变）而固定令牌必须进入 {@code ACTIVE}；随后依序派发
     * {@code EXEC}(0)→OK、{@code QUERY}(1)→READBACK 快照携带 tick/day、{@code PING}(2)→OK pong；
     * 其中一条命令经 {@code SessionFrameCodec#encodeFrame}/{@code decodeFrame} 往返（证明帧协议入口
     * 在游戏 JVM 内可用）；最后 {@code BYE}(3)→CLOSED。通过打固定 PASS marker，否则抛出让 FAILED marker。
     */
    private static void runClosedLoop(ServerStartedEvent event) throws Exception {
        final SessionMachine machine = new SessionMachine(SESSION_ID, SessionMachine.DEFAULT_AUTH_TOKEN);
        final int[] dispatched = {0};

        // open(): CLOSED -> HELLO
        SessionMachineResult r = machine.open(SESSION_ID);
        require(r.outcome() == Outcome.ACCEPT, "open must be ACCEPT");
        require(r.state() == SessionState.HELLO, "open must land in HELLO");

        // wrong token -> deterministic AUTH_REJECT, state unchanged
        r = machine.authenticate("wrong-token");
        require(r.outcome() == Outcome.REJECT, "wrong token must deterministically reject");
        require(SessionMachineException.AUTH_REJECT.equals(r.reason()), "wrong token reason must be AUTH_REJECT");
        require(r.state() == SessionState.HELLO, "AUTH_REJECT must leave state in HELLO");

        // fixed token -> HELLO -> ACTIVE
        r = machine.authenticate(SessionMachine.DEFAULT_AUTH_TOKEN);
        require(r.outcome() == Outcome.ACCEPT, "fixed token must be ACCEPT");
        require(r.state() == SessionState.ACTIVE, "auth must land in ACTIVE");

        // EXEC(seq=0): fixed UTF-8 payload "tick=1\nday=1200"
        SessionCommand exec = new SessionCommand(SessionCommandKind.EXEC, 0, null,
                EXEC_PAYLOAD.getBytes(StandardCharsets.UTF_8));
        r = machine.dispatch(exec);
        require(r.outcome() == Outcome.OK, "EXEC must be OK");
        dispatched[0]++;

        // QUERY(seq=1): READBACK snapshot must carry tick/day (the programmable runtime delta).
        SessionCommand query = new SessionCommand(SessionCommandKind.QUERY, 1, "readback", new byte[0]);
        r = machine.dispatch(query);
        require(r.outcome() == Outcome.READBACK, "QUERY must be READBACK");
        require(r.snapshot() != null, "QUERY must carry a snapshot");
        require(r.snapshot().containsKey("tick") && r.snapshot().containsKey("day"),
                "snapshot must contain tick/day after EXEC");
        dispatched[0]++;

        // PING(seq=2): OK echo "pong".
        SessionCommand ping = new SessionCommand(SessionCommandKind.PING, 2, null, new byte[0]);
        r = machine.dispatch(ping);
        require(r.outcome() == Outcome.OK, "PING must be OK");
        require("pong".equals(r.message()), "PING must echo pong");
        dispatched[0]++;

        // Frame round-trip on one command (fixed sessionId) — proves the frame-protocol entry is
        // usable inside the game JVM. Pure codec path, independent of the machine dispatch seq gate.
        byte[] frame = SessionFrameCodec.encodeFrame(query, SESSION_ID);
        SessionFrame restored = SessionFrameCodec.decodeFrame(frame);
        require(restored.command().equals(query), "decodeFrame must restore the command (value-equal)");
        require(restored.sessionTag() == SessionFrameCodec.sessionTag(SESSION_ID),
                "decodeFrame session tag must match the fixed sessionId mapping");

        // BYE(seq=3): ACTIVE -> CLOSED.
        SessionCommand bye = new SessionCommand(SessionCommandKind.BYE, 3, null, new byte[0]);
        r = machine.dispatch(bye);
        require(r.outcome() == Outcome.CLOSED, "BYE must close the session");
        require(r.state() == SessionState.CLOSED, "state must be CLOSED after BYE");
        dispatched[0]++;

        // Fixed format, deterministic, no timestamp / no randomness.
        print("session-demo cmds=" + dispatched[0] + " frames=1 ok");
        print("PASS session closed loop OK");
    }

    /** 失败断言：条件不成立则抛 {@link AssertionError}（被上层兜底为 FAILED marker）。 */
    private static void require(boolean condition, String msg) {
        if (!condition) {
            throw new AssertionError(msg);
        }
    }

    private static void print(String body) {
        System.out.println(MARKER + " " + body);
    }
}