package io.toterra.subterra.engine.p2p.holepunch;

import io.toterra.subterra.engine.p2p.NodeAddr;

/**
 * p.2.30.1 打洞会话状态机（纯 JDK、确定性）：PUNCHING →（全部直连尝试失败且计划
 * 含兜底）RELAY → ESTABLISHED / FAILED；任一 PUNCH_OK / RELAY_OK 即 ESTABLISHED。
 * 终态后再收事件确定性拒绝（IllegalArgumentException）；同事件序同状态轨迹。
 *
 * <p>The punch session state machine (p.2.30.1, pure JDK, deterministic):
 * PUNCHING → (all direct attempts failed and the plan carries the fallback) RELAY
 * → ESTABLISHED / FAILED; any PUNCH_OK / RELAY_OK settles ESTABLISHED. Events after
 * a terminal state are rejected deterministically (IllegalArgumentException); the
 * same event sequence yields the same state trajectory.
 */
public final class HolePunchSession {

    /** 会话状态。 / The session state. */
    public enum State {
        PUNCHING,
        RELAY,
        ESTABLISHED,
        FAILED
    }

    /** 事件类别。 / The event kind. */
    public enum Kind {
        PUNCH_OK,
        PUNCH_FAIL,
        RELAY_OK,
        RELAY_FAIL
    }

    /** 确定性事件（addr 仅 OK 事件携带）。 / A deterministic event (addr only on OK events). */
    public record Event(Kind kind, NodeAddr addr) {
        public Event {
            if (kind == null) {
                throw new IllegalArgumentException("kind must not be null");
            }
            if (addr == null && (kind == Kind.PUNCH_OK || kind == Kind.RELAY_OK)) {
                throw new IllegalArgumentException("OK events must carry the established address");
            }
        }
    }

    private final int directAttempts;
    private final boolean hasRelay;
    private State state = State.PUNCHING;
    private int punchFails;
    private NodeAddr established;

    /** @param plan 会话绑定的计划（决定直连尝试数与兜底有无）。 / @param plan the bound plan (direct attempt count + fallback presence). */
    public HolePunchSession(PunchPlan plan) {
        if (plan == null) {
            throw new IllegalArgumentException("plan must not be null");
        }
        int direct = 0;
        boolean relay = false;
        for (PunchAttempt a : plan.attempts()) {
            if (a.kind() == PunchAttempt.Kind.RELAY) {
                relay = true;
            } else {
                direct++;
            }
        }
        this.directAttempts = direct;
        this.hasRelay = relay;
    }

    public State state() {
        return state;
    }

    public NodeAddr established() {
        return established;
    }

    /** 确定性推进：返回本事件是否终结会话。 / Advances deterministically; returns whether this event settled the session. */
    public boolean onEvent(Event event) {
        if (state == State.ESTABLISHED || state == State.FAILED) {
            throw new IllegalArgumentException("session already terminal (" + state + ")");
        }
        switch (event.kind()) {
            case PUNCH_OK -> {
                established = event.addr();
                state = State.ESTABLISHED;
                return true;
            }
            case PUNCH_FAIL -> {
                punchFails++;
                if (punchFails >= directAttempts) {
                    if (hasRelay) {
                        state = State.RELAY;
                    } else {
                        state = State.FAILED;
                        return true;
                    }
                }
                return false;
            }
            case RELAY_OK -> {
                if (state != State.RELAY) {
                    throw new IllegalArgumentException("RELAY_OK outside the RELAY state");
                }
                established = event.addr();
                state = State.ESTABLISHED;
                return true;
            }
            case RELAY_FAIL -> {
                if (state != State.RELAY) {
                    throw new IllegalArgumentException("RELAY_FAIL outside the RELAY state");
                }
                state = State.FAILED;
                return true;
            }
            default -> throw new IllegalArgumentException("unknown event kind");
        }
    }
}
