// Deterministic session probe for p.2.11.4: command envelope round-trips, tink v2 session-frame
// round-trips & bad-frame rejection, and scripted state-machine sequences (auth / order / replay /
// close / reopen). Pure JVM — no MC, no wall-clock, no timestamps, no random seeds. NOT shipped
// in the mod jar. Every assertion is deterministic; byte equality is the strongest judgement.
package io.toterra.subterra.probes;

import io.toterra.subterra.engine.network.frame.FrameConst;
import io.toterra.subterra.engine.network.integrity.FrameV2;
import io.toterra.subterra.engine.session.SessionCommand;
import io.toterra.subterra.engine.session.SessionCommandKind;
import io.toterra.subterra.engine.session.SessionEnvelopeCodec;
import io.toterra.subterra.engine.session.SessionEnvelopeException;
import io.toterra.subterra.engine.session.SessionFrame;
import io.toterra.subterra.engine.session.SessionFrameCodec;
import io.toterra.subterra.engine.session.SessionFrameException;
import io.toterra.subterra.engine.session.SessionMachine;
import io.toterra.subterra.engine.session.SessionMachineException;
import io.toterra.subterra.engine.session.SessionMachineResult;
import io.toterra.subterra.engine.session.SessionState;
import io.toterra.subterra.engine.zd.ZdDocWriter;
import io.toterra.subterra.engine.zd.ZdRow;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * p.2.11.4 确定性「会话探针」—— 覆盖 p.2.11.1–.3 的被测面全部确定性断言：命令信封（encode→decode
 * →再 encode 逐字节恒等；截断/越界 kind 序号/坏 Base64 拒绝并以固定原因精确标识）、tink v2 会话帧
 * （固定输入两次 encodeFrame 逐字节一致、decodeFrame 还原同命令、sessionTag 同 id 恒等/异 id 不等、
 * 坏帧八类逐一映射固定原因）、以及状态机脚本化确定性序列（错令牌鉴权拒绝→正确令牌→EXEC/QUERY/PING
 * 依序 →乱序/重放 REJECT→BYE→CLOSED→关闭后派发拒绝→重开新会话快照还原）。全为确定性断言、禁时间戳/
 * 随机/时序；退出码 0 = PASS。
 *
 * <p>p.2.11.4 deterministic session probe — covering every deterministic assertion of the p.2.11.1–.3
 * surface under test: the command envelope (encode→decode→re-encode byte-identical; truncate /
 * out-of-range kind ordinal / bad Base64 rejected with an exact fixed reason), the tink v2 session
 * frame (two encodeFrame calls on fixed input are byte-identical; decodeFrame restores the same
 * command; sessionTag equal for the same id / distinct for different ids; eight bad-frame classes
 * each pinned to a fixed reason), and a scripted state-machine sequence (wrong-token auth rejected →
 * correct token → EXEC/QUERY/PING in order → reorder/replay REJECT → BYE → CLOSED → post-close
 * dispatch rejected → reopen yields a fresh-snapshot new session). All assertions are deterministic,
 * no timestamp / random / timing; exit 0 = PASS.
 */
public final class SessionProbe {

    private SessionProbe() {
    }

    /** 固定样例会话 id。Fixed sample session ids. */
    private static final String SID_A = "alpha";
    private static final String SID_B = "beta";

    /** 状态机固定鉴权令牌。Fixed auth token for the state machine. */
    private static final String TOKEN = "tok-42";

    private static int failures = 0;
    private static int checks = 0;

    private static void check(String name, boolean ok) {
        checks++;
        if (ok) {
            System.out.println("[PASS] " + name);
        } else {
            failures++;
            System.out.println("[FAIL] " + name);
        }
    }

    /** 捕获信封解码的固定原因；可解码返回 null。Fixed envelope-decode reason, or null if it decodes. */
    private static String envelopeReasonOf(byte[] env) {
        try {
            SessionEnvelopeCodec.decode(env);
            return null;
        } catch (SessionEnvelopeException e) {
            return e.reason();
        }
    }

    /** 捕获帧解码的固定原因；可解码返回 null。Fixed frame-decode reason, or null if it decodes. */
    private static String frameReasonOf(byte[] frame) {
        try {
            SessionFrameCodec.decodeFrame(frame);
            return null;
        } catch (SessionFrameException e) {
            return e.reason();
        }
    }

    public static void main(String[] args) {
        try {
            run();
        } catch (Exception e) {
            failures++;
            System.out.println("[FAIL] 探针异常: " + e);
            e.printStackTrace(System.out);
        }

        if (failures == 0) {
            System.out.println("SessionProbe: " + checks + " checks, 0 failures");
            System.exit(0);
        } else {
            System.out.println("SessionProbe: " + checks + " checks, " + failures + " failures");
            System.exit(1);
        }
    }

    private static void run() {
        SessionCommand fixed = new SessionCommand(SessionCommandKind.EXEC, 7L, "meta-x",
                "a=1\nb=2".getBytes(StandardCharsets.UTF_8));

        // ================= 1. 命令信封往返 / command envelope round-trip =================
        byte[] env = SessionEnvelopeCodec.encode(fixed);
        SessionCommand dec;
        try {
            dec = SessionEnvelopeCodec.decode(env);
        } catch (SessionEnvelopeException e) {
            dec = null;
        }
        byte[] env2 = dec == null ? null : SessionEnvelopeCodec.encode(dec);
        check("信封往返: encode→decode→再 encode 逐字节恒等", Arrays.equals(env, env2));
        check("信封往返: decode(encode(cmd)) 逐字段还原同一命令", fixed.equals(dec));

        // ---- 同级畸形：固定原因精确拒绝 ----
        String truncated = envelopeReasonOf(shorterEnvelope());
        check("信封畸形: 截断(3 行) → LENGTH 且原因精确", SessionEnvelopeException.LENGTH.equals(truncated));

        String unknownKind = envelopeReasonOf(withKindOrdinal(99));
        check("信封畸形: kind 序号越界 → UNKNOWN_KIND 且原因精确",
                SessionEnvelopeException.UNKNOWN_KIND.equals(unknownKind));

        String badB64 = envelopeReasonOf(withBadBase64("!!!not-base64"));
        check("信封畸形: 载荷段坏 Base64 → PAYLOAD 且原因精确",
                SessionEnvelopeException.PAYLOAD.equals(badB64));

        String notZd = envelopeReasonOf(new byte[]{0x01, 0x02, 0x03, 0x04});
        check("信封畸形: 非 zd 文档 → MALFORMED 且原因精确",
                SessionEnvelopeException.MALFORMED.equals(notZd));

        // ================= 2. 帧往返与编码确定性 / frame round-trip & encoding determinism =================
        byte[] f1 = SessionFrameCodec.encodeFrame(fixed, SID_A);
        byte[] f2 = SessionFrameCodec.encodeFrame(fixed, SID_A);
        check("帧确定性: 同命令+同 id 两次 encodeFrame 逐字节一致", Arrays.equals(f1, f2));

        SessionFrame restored;
        try {
            restored = SessionFrameCodec.decodeFrame(f1);
        } catch (SessionFrameException e) {
            restored = null;
        }
        check("帧往返: decodeFrame(encodeFrame(cmd)) 还原同一命令",
                restored != null && fixed.equals(restored.command()));
        check("帧往返: 还原的会话归属 tag == sessionTag(id)",
                restored != null && restored.sessionTag() == SessionFrameCodec.sessionTag(SID_A));
        check("帧往返: sessionTag 同 id 恒等",
                SessionFrameCodec.sessionTag(SID_A) == SessionFrameCodec.sessionTag(SID_A));
        check("帧往返: sessionTag 异 id 不相等（固定两样例）",
                SessionFrameCodec.sessionTag(SID_A) != SessionFrameCodec.sessionTag(SID_B));

        // ================= 3. 坏帧确定性拒绝 / bad-frame deterministic rejection =================
        byte[] badMagic = f1.clone();
        badMagic[0] ^= 0xFF;
        check("坏帧: 魔数篡改 → BAD_MAGIC 且原因精确", SessionFrameException.BAD_MAGIC.equals(frameReasonOf(badMagic)));

        byte[] badVer = f1.clone();
        badVer[2] ^= 0xFF;
        check("坏帧: 版本字节篡改 → BAD_VERSION 且原因精确",
                SessionFrameException.BAD_VERSION.equals(frameReasonOf(badVer)));

        byte[] badRes = f1.clone();
        badRes[3] |= 0x20;
        check("坏帧: 保留位 bit5 置位 → RESERVED_BITS 且原因精确",
                SessionFrameException.RESERVED_BITS.equals(frameReasonOf(badRes)));

        byte[] badTrunc = Arrays.copyOf(f1, f1.length - 4);
        check("坏帧: 截断 → LENGTH 且原因精确",
                SessionFrameException.LENGTH.equals(frameReasonOf(badTrunc)));

        byte[] badInteg = f1.clone();
        badInteg[badInteg.length - FrameConst.INTEGRITY_STRONG_BYTES - 1] ^= 0x01;
        check("坏帧: 翻转载荷一字节 → INTEGRITY 且原因精确",
                SessionFrameException.INTEGRITY.equals(frameReasonOf(badInteg)));

        check("坏帧: 剔除 ext key6(seq) → EXT 且原因精确",
                SessionFrameException.EXT.equals(frameReasonOf(frameWithoutSeqTlv(fixed, SID_A))));
        check("坏帧: 剔除 ext key5(session) → EXT 且原因精确",
                SessionFrameException.EXT.equals(frameReasonOf(frameWithoutSessionTlv(fixed, SID_A))));
        check("坏帧: key9 meta 篡改 → META 且原因精确",
                SessionFrameException.META.equals(frameReasonOf(frameWithTamperedMeta(fixed, SID_A))));
        check("坏帧: 帧内信封损坏 → ENVELOPE 且原因精确",
                SessionFrameException.ENVELOPE.equals(frameReasonOf(frameWithGarbageEnvelope(fixed, SID_A))));

        // ================= 4. 状态机确定性序列（脚本化）/ scripted state-machine sequence =================
        SessionMachine m = new SessionMachine("sess-1", TOKEN);

        SessionMachineResult open = m.open("sess-1");
        check("状态机: open → ACCEPT 且状态 HELLO",
                open.outcome() == SessionMachineResult.Outcome.ACCEPT && open.state() == SessionState.HELLO);

        SessionMachineResult badAuth = m.authenticate("wrong-token");
        check("状态机: wrongToken 鉴权 → REJECT AUTH_REJECT 且状态仍 HELLO",
                badAuth.outcome() == SessionMachineResult.Outcome.REJECT
                        && SessionMachineException.AUTH_REJECT.equals(badAuth.reason())
                        && badAuth.state() == SessionState.HELLO);

        SessionMachineResult okAuth = m.authenticate(TOKEN);
        check("状态机: 正确令牌 → ACCEPT 且状态 ACTIVE",
                okAuth.outcome() == SessionMachineResult.Outcome.ACCEPT && okAuth.state() == SessionState.ACTIVE);

        SessionMachineResult exec = m.dispatch(new SessionCommand(SessionCommandKind.EXEC, 0, null,
                "a=1\nb=2".getBytes(StandardCharsets.UTF_8)));
        check("状态机: EXEC(seq=0, a=1,b=2) → OK",
                exec.outcome() == SessionMachineResult.Outcome.OK && exec.reason().isEmpty());

        SessionMachineResult query = m.dispatch(new SessionCommand(SessionCommandKind.QUERY, 1, null,
                new byte[0]));
        check("状态机: QUERY(seq=1) → READBACK 快照 == {a:1,b:2}",
                query.outcome() == SessionMachineResult.Outcome.READBACK
                        && mapOf("a", "1", "b", "2").equals(query.snapshot()));

        SessionMachineResult ping2 = m.dispatch(new SessionCommand(SessionCommandKind.PING, 2, null,
                new byte[0]));
        check("状态机: PING(seq=2) → OK 且 message=='pong'",
                ping2.outcome() == SessionMachineResult.Outcome.OK && "pong".equals(ping2.message()));

        SessionMachineResult ping3 = m.dispatch(new SessionCommand(SessionCommandKind.PING, 3, null,
                new byte[0]));
        check("状态机: PING(seq=3) → OK",
                ping3.outcome() == SessionMachineResult.Outcome.OK);

        SessionMachineResult reorder = m.dispatch(new SessionCommand(SessionCommandKind.EXEC, 2, null,
                "x=1".getBytes(StandardCharsets.UTF_8)));
        check("状态机: 乱序 dispatch seq=2(≤lastSeq=3) → REJECT REORDER",
                reorder.outcome() == SessionMachineResult.Outcome.REJECT
                        && SessionMachineException.REORDER.equals(reorder.reason()));

        SessionMachineResult replay = m.dispatch(new SessionCommand(SessionCommandKind.PING, 3, null,
                new byte[0]));
        check("状态机: 重放 dispatch seq=3(重复) → REJECT REORDER",
                replay.outcome() == SessionMachineResult.Outcome.REJECT
                        && SessionMachineException.REORDER.equals(replay.reason()));

        SessionMachineResult bye = m.dispatch(new SessionCommand(SessionCommandKind.BYE, 4, null,
                new byte[0]));
        check("状态机: BYE(seq=4) → CLOSED 且状态 CLOSED",
                bye.outcome() == SessionMachineResult.Outcome.CLOSED && bye.state() == SessionState.CLOSED);

        SessionMachineResult closedDisp = m.dispatch(new SessionCommand(SessionCommandKind.PING, 5, null,
                new byte[0]));
        check("状态机: CLOSED 后 dispatch → REJECT ILLEGAL_STATE 且 detail 固定",
                closedDisp.outcome() == SessionMachineResult.Outcome.REJECT
                        && SessionMachineException.ILLEGAL_STATE.equals(closedDisp.reason())
                        && "dispatch requires ACTIVE but state is CLOSED".equals(closedDisp.message()));

        SessionMachineResult reopen = m.open("sess-1");
        check("状态机: 重开 open → ACCEPT 且状态 HELLO",
                reopen.outcome() == SessionMachineResult.Outcome.ACCEPT && reopen.state() == SessionState.HELLO);

        SessionMachineResult reauth = m.authenticate(TOKEN);
        check("状态机: 重开后再 authenticate → ACCEPT 且状态 ACTIVE",
                reauth.outcome() == SessionMachineResult.Outcome.ACCEPT && reauth.state() == SessionState.ACTIVE);

        SessionMachineResult reexec = m.dispatch(new SessionCommand(SessionCommandKind.EXEC, 0, null,
                "x=9\ny=7".getBytes(StandardCharsets.UTF_8)));
        check("状态机: 新会话 EXEC(seq=0, x=9,y=7) → OK",
                reexec.outcome() == SessionMachineResult.Outcome.OK);

        SessionMachineResult requery = m.dispatch(new SessionCommand(SessionCommandKind.QUERY, 1, null,
                new byte[0]));
        check("状态机: 新会话 QUERY → 快照 == {x:9,y:7}（旧键已清空）",
                requery.outcome() == SessionMachineResult.Outcome.READBACK
                        && mapOf("x", "9", "y", "7").equals(requery.snapshot()));
    }

    // ================= fixture helpers / 固定构造辅助 =================

    /** 固定键序的有序地图。Fixed-key-order map fixture. */
    private static Map<String, String> mapOf(String... kv) {
        TreeMap<String, String> m = new TreeMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put(kv[i], kv[i + 1]);
        }
        return m;
    }

    /** 精确 3 行的 zd 信封（缺载荷段）→ readRows 只还原 3 行 → LENGTH。A 3-row zd envelope (missing the payload row). */
    private static byte[] shorterEnvelope() {
        List<ZdRow> rows = List.of(
                new ZdRow(2, "session.kind", SessionCommandKind.PING.ordinal(), 0.0, "", 0),
                new ZdRow(2, "session.seq", 5L, 0.0, "", 0),
                new ZdRow(1, "session.meta", 0L, 0.0, "", 0));
        return ZdDocWriter.write(0, rows);
    }

    /** 把 kind 序号改成越界值 → UNKNOWN_KIND。Envelope with an out-of-range kind ordinal. */
    private static byte[] withKindOrdinal(int ord) {
        byte[] pay = "x".getBytes(StandardCharsets.UTF_8);
        List<ZdRow> rows = List.of(
                new ZdRow(2, "session.kind", ord, 0.0, "", 0),
                new ZdRow(2, "session.seq", 5L, 0.0, "", 0),
                new ZdRow(1, "session.meta", 0L, 0.0, "", 0),
                new ZdRow(1, "session.payload", 0L, 0.0, Base64.getEncoder().encodeToString(pay), 0));
        return ZdDocWriter.write(0, rows);
    }

    /** 把载荷段 value 改成坏 Base64 → PAYLOAD。Envelope with a bad-Base64 payload segment. */
    private static byte[] withBadBase64(String badB64) {
        List<ZdRow> rows = List.of(
                new ZdRow(2, "session.kind", SessionCommandKind.PING.ordinal(), 0.0, "", 0),
                new ZdRow(2, "session.seq", 5L, 0.0, "", 0),
                new ZdRow(1, "session.meta", 0L, 0.0, "", 0),
                new ZdRow(1, "session.payload", 0L, 0.0, badB64, 0));
        return ZdDocWriter.write(0, rows);
    }

    // ---------- frame-level tamper (rebuild ext, recompute integrity via FrameV2) ----------

    /** 会话主标签 TLV。The session-tag TLV (ext key5). */
    private static byte[] sessionTlv(String sid) {
        return FrameV2.extTlv(FrameConst.EXT_STREAM_SESSION, be32(SessionFrameCodec.sessionTag(sid)));
    }

    /** 命令序号 TLV。The seq TLV (ext key6). */
    private static byte[] seqTlv(long seq) {
        return FrameV2.extTlv(FrameConst.EXT_STREAM_SEQ, be64(seq));
    }

    /** key9 元数据 TLV。The key9 meta TLV. */
    private static byte[] metaTlv(String meta) {
        return FrameV2.extTlv(FrameConst.EXT_EXT_META,
                meta == null ? new byte[0] : meta.getBytes(StandardCharsets.UTF_8));
    }

    /** 剔除 ext key6(seq) 的帧 → 缺 seq 键 → EXT。Frame with the ext key6 (seq) removed. */
    private static byte[] frameWithoutSeqTlv(SessionCommand cmd, String sid) {
        byte[] ext = concat(sessionTlv(sid), metaTlv(cmd.meta()));
        return FrameV2.encode(SessionEnvelopeCodec.encode(cmd), FrameConst.FLAG_STRONG, ext);
    }

    /** 剔除 ext key5(session) 的帧 → 缺 session 键 → EXT。Frame with the ext key5 (session) removed. */
    private static byte[] frameWithoutSessionTlv(SessionCommand cmd, String sid) {
        byte[] ext = concat(seqTlv(cmd.seq()), metaTlv(cmd.meta()));
        return FrameV2.encode(SessionEnvelopeCodec.encode(cmd), FrameConst.FLAG_STRONG, ext);
    }

    /** 替换 key9 meta 的帧 → 与信封 meta 不一致 → META。Frame with a tampered key9 meta. */
    private static byte[] frameWithTamperedMeta(SessionCommand cmd, String sid) {
        byte[] ext = concat(sessionTlv(sid), seqTlv(cmd.seq()), metaTlv("tampered-meta"));
        return FrameV2.encode(SessionEnvelopeCodec.encode(cmd), FrameConst.FLAG_STRONG, ext);
    }

    /** 帧载荷换成非 zd 字节 → 信封解码失败 → ENVELOPE。Frame whose payload is not a zd envelope. */
    private static byte[] frameWithGarbageEnvelope(SessionCommand cmd, String sid) {
        byte[] ext = concat(sessionTlv(sid), seqTlv(cmd.seq()), metaTlv(cmd.meta()));
        return FrameV2.encode(new byte[]{0x01, 0x02, 0x03}, FrameConst.FLAG_STRONG, ext);
    }

    /** 拼接字节数组。Concatenates byte arrays. */
    private static byte[] concat(byte[]... parts) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (byte[] p : parts) {
            out.write(p, 0, p.length);
        }
        return out.toByteArray();
    }

    /** 4 字节大端无符号整型。Big-endian unsigned 32-bit. */
    private static byte[] be32(int n) {
        return new byte[]{(byte) (n >>> 24), (byte) (n >>> 16), (byte) (n >>> 8), (byte) n};
    }

    /** 8 字节大端长整型。Big-endian 64-bit. */
    private static byte[] be64(long n) {
        byte[] out = new byte[8];
        for (int i = 7; i >= 0; i--) {
            out[i] = (byte) (n & 0xFF);
            n >>>= 8;
        }
        return out;
    }
}