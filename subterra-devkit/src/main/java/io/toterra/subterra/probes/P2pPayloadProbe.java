package io.toterra.subterra.probes;

import io.toterra.subterra.engine.network.frame.FrameConst;
import io.toterra.subterra.engine.network.integrity.FrameV2;
import io.toterra.subterra.engine.network.integrity.FrameV2Result;
import io.toterra.subterra.engine.p2p.payload.P2pMessageType;
import io.toterra.subterra.engine.p2p.payload.RawRows;
import io.toterra.subterra.engine.p2p.payload.ZdChannelCodec;
import io.toterra.subterra.engine.p2p.transport.ChannelResult;
import io.toterra.subterra.engine.p2p.transport.P2pChannel;
import io.toterra.subterra.engine.zd.ZdRow;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

/**
 * p.2.5.5 zd 自定义载荷通道 ABI 门面确定性探针（纯 JVM，无 MC）：验证
 * {@link ZdChannelCodec} 三档典型载荷的 encode→decode 逐字段/逐字节一致、体积比上界断言、
 * 每个 {@link P2pMessageType} 类型槽往返、未知 ext 键跳过、篡改不静默、与 {@link FrameV2}
 * 强校验链路一致、与 {@link P2pChannel} 传输串联逐字节一致、以及同种子两次字节级确定性。
 * <p>
 * 确定性纪律：数据全部由固定种子伪随机生成（复用 p.2.5.1 生成法）；失败计数只在失败路径自增；
 * <b>无时序断言</b>。退出码 0 = PASS，1 = FAIL。
 * <p>
 * p.2.5.5 deterministic acceptance probe for the zd custom-payload-channel ABI facade (pure JVM,
 * no MC): three-tier payload round-trip, volume-ratio upper bound, per-{@link P2pMessageType}
 * type-slot round-trip, unknown-ext-key skip, non-silent tamper rejection, the strong-integrity
 * {@link FrameV2} link, the end-to-end {@link P2pChannel} byte-identical link, and same-seed
 * byte-level determinism. Fixed seeds; failure counter advances only on assertion failures; no
 * timing assertions. Exit 0 = PASS, 1 = FAIL.
 */
public final class P2pPayloadProbe {

    private P2pPayloadProbe() {
    }

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

    public static void main(String[] args) {
        List<ZdRow> small = sample(SampleKind.SMALL_META);
        List<ZdRow> medium = sample(SampleKind.MEDIUM_TABLES);
        List<ZdRow> large = sample(SampleKind.LARGE_CHUNKS);
        byte[] meta = "channel-metadata-A".getBytes(StandardCharsets.UTF_8);

        tierRoundTrips(small, medium, large, meta);
        volumeBounds(small, medium, large);
        typeSlotRoundTrip(medium, meta);
        unknownExtSkipped(medium, meta);
        tamperNotSilent(small, meta);
        frameV2Link(medium, meta);
        p2pChannelLink(large, meta);
        determinism();

        if (failures == 0) {
            System.out.println("[P2pPayloadProbe] PASS: p.2.5.5 zd payload channel ABI facade ("
                    + checks + " checks)");
            System.exit(0);
        } else {
            System.out.println("[P2pPayloadProbe] FAIL: " + failures + " failure(s) of " + checks);
            System.exit(1);
        }
    }

    // ================= 1 三档典型载荷 encode→decode 往返 =================

    private static void tierRoundTrips(List<ZdRow> small, List<ZdRow> medium, List<ZdRow> large,
                                       byte[] meta) {
        tier(small, "small-state-metadata", meta);
        tier(medium, "medium-table-data", meta);
        tier(large, "large-chunk-data", meta);
    }

    private static void tier(List<ZdRow> rows, String label, byte[] meta) {
        byte[] enc = ZdChannelCodec.encode(P2pMessageType.CHUNK, meta, rows);
        ZdChannelCodec.Decode dec = ZdChannelCodec.decode(enc);
        check("tier " + label + ": decode succeeds (ok=true, non-null result)", dec != null && dec.ok());
        check("tier " + label + ": decode reproduces every ZdRow field (kind/key/i64/f64/str/child)",
                rowsEqual(dec.rows(), rows));
        // 确定性往返：decode 出的行再 encode 应与原字节逐字节一致。
        byte[] reenc = ZdChannelCodec.encode(dec.type(), dec.channelMeta(), dec.rows());
        check("tier " + label + ": re-encode(decode(bytes)) is byte-identical to the original payload",
                Arrays.equals(reenc, enc));
    }

    // ================= 2 体积比上界断言（复用 p.2.5.1 口径） =================

    private static void volumeBounds(List<ZdRow> small, List<ZdRow> medium, List<ZdRow> large) {
        bounded(small, "small-state-metadata");
        bounded(medium, "medium-table-data");
        bounded(large, "large-chunk-data");
    }

    private static void bounded(List<ZdRow> rows, String label) {
        ZdChannelCodec.VolumeReport v = ZdChannelCodec.volumeRatio(rows);
        System.out.println("[BENCH] " + label + " raw-content-bytes=" + v.rawContentBytes()
                + " zd-doc-bytes=" + v.zdDocBytes() + " ratio=" + String.format("%.4f", v.ratio()));
        // p.2.5.1 实测三档 raw-vs-zd 比约 0.47~0.51；帧开销/字段 wire 只增不减，确定在 (0,1]。
        check("volume " + label + ": ratio in (0, 1] (zd carries content + frame/wire overhead)",
                v.ratio() > 0.0 && v.ratio() <= 1.0);
        // 信息量不丢失：文档字节 >= raw 内容字节。
        check("volume " + label + ": zd doc bytes >= raw content bytes (no information lost)",
                v.zdDocBytes() >= v.rawContentBytes());
        // 安全上界：确定性比远低于 0.95（p.2.5.1 实测 ~0.5），0.95 为稳定余量。
        check("volume " + label + ": ratio <= 0.95 safe upper bound (p.2.5.1 measured ~0.5)",
                v.ratio() <= 0.95);
    }

    // ================= 3 类型槽：每个枚举值编解码往返 =================

    private static void typeSlotRoundTrip(List<ZdRow> rows, byte[] meta) {
        for (P2pMessageType t : P2pMessageType.values()) {
            byte[] enc = ZdChannelCodec.encode(t, meta, rows);
            ZdChannelCodec.Decode dec = ZdChannelCodec.decode(enc);
            check("type slot " + t.name() + ": encode(decode) round-trips the message type",
                    dec.ok() && dec.type() == t);
        }
    }

    // ================= 4 未知 ext 键跳过 + key9 复用 =================

    private static void unknownExtSkipped(List<ZdRow> rows, byte[] meta) {
        byte[] key9meta = "meta-via-ext-key9".getBytes(StandardCharsets.UTF_8);
        // ext = 我们的 key9 TLV + 一个未知键（0x03E7）；携带未知键必须被跳过而不失败。
        byte[] ext = concat(ZdChannelCodec.channelMetaExt(key9meta),
                FrameV2.extTlv(0x03E7, "ignored".getBytes(StandardCharsets.UTF_8)));
        byte[] enc = ZdChannelCodec.encode(P2pMessageType.SYNC, meta, rows);
        ZdChannelCodec.Decode dec = ZdChannelCodec.decode(enc, ext);
        check("unknown ext key: decoding with an ext carrying an unknown TLV does not fail",
                dec != null && dec.ok());
        check("unknown ext key: rows still round-trip intact",
                rowsEqual(dec.rows(), rows));
        // key9 携带的通道元数据优先于信封内的（复用 tink v2 ext key9 ABI）。
        check("ext key9 channel meta (explicit empty meta) wins over the envelope meta",
                Arrays.equals(dec.channelMeta(), key9meta));
    }

    // ================= 5 篡改不静默 =================

    private static void tamperNotSilent(List<ZdRow> rows, byte[] meta) {
        byte[] enc = ZdChannelCodec.encode(P2pMessageType.STATUS, meta, rows);
        // 翻转 zd 头魔数字节 → 结构校验层拒绝（抛 IllegalArgumentException，不静默出错误数据）。
        byte[] bad = enc.clone();
        bad[0] ^= 0x40; // 'T'(0x54) -> 't'
        boolean threw = false;
        try {
            ZdChannelCodec.decode(bad);
        } catch (IllegalArgumentException e) {
            threw = true;
        }
        check("tamper: a flipped zd-header byte makes decode throw (zd structure layer rejects, never silent)",
                threw);

        // 帧级强校验拒绝篡改载荷（tsha1f 层）。
        byte[] frame = FrameV2.encode(enc, FrameConst.FLAG_STRONG, null);
        byte[] badFrame = frame.clone();
        int pstart = 10 + (((frame[8] & 0xFF) << 8) | (frame[9] & 0xFF));
        badFrame[pstart] ^= 0x01;
        FrameV2Result fr = FrameV2.parse(badFrame, 0);
        check("tamper: FrameV2(strong) single-byte payload flip is integrity-rejected (tsha1f layer)",
                fr.ok() && !fr.integrityOk());

        // P2pChannel 串联下的篡改同样被拒绝。
        P2pChannel tx = new P2pChannel(0);
        List<byte[]> frames = tx.send(enc);
        byte[] badChunk = frames.get(0).clone();
        badChunk[badChunk.length - 1] ^= 0x40; // flip a strong-integrity-slot byte
        ChannelResult r = new P2pChannel(0).receive(badChunk);
        check("tamper: P2pChannel rejects a single-byte integrity flip on the channel payload",
                r.status() == ChannelResult.Status.REJECT);
    }

    // ================= 6 与 FrameV2(强校验) 链路 =================

    private static void frameV2Link(List<ZdRow> rows, byte[] meta) {
        byte[] enc = ZdChannelCodec.encode(P2pMessageType.RELAY, meta, rows);
        byte[] frame = FrameV2.encode(enc, FrameConst.FLAG_STRONG, null);
        FrameV2Result fr = FrameV2.parse(frame, 0);
        check("FrameV2 chain: strong frame parses with integrityOk && exact nextPos",
                fr != null && fr.ok() && fr.integrityOk() && fr.nextPos() == frame.length);
        check("FrameV2 chain: frame payload is byte-identical to the original zd payload bytes",
                Arrays.equals(fr.payload(), enc));
        ZdChannelCodec.Decode dec = ZdChannelCodec.decode(fr.payload());
        check("FrameV2 chain: zd decode over the frame payload reproduces every row",
                dec.ok() && rowsEqual(dec.rows(), rows) && dec.type() == P2pMessageType.RELAY);
    }

    // ================= 7 与 P2pChannel 传输串联 =================

    private static void p2pChannelLink(List<ZdRow> rows, byte[] meta) {
        byte[] enc = ZdChannelCodec.encode(P2pMessageType.CHUNK, meta, rows);
        P2pChannel tx = new P2pChannel(0);
        P2pChannel rx = new P2pChannel(0);
        List<byte[]> frames = tx.send(enc);
        ChannelResult last = ChannelResult.pending();
        for (byte[] f : frames) {
            last = rx.receive(f);
        }
        check("P2pChannel chain: send→receive delivers the intact channel payload",
                last.delivers(enc));
        ZdChannelCodec.Decode dec = last.status() == ChannelResult.Status.DELIVERED
                ? ZdChannelCodec.decode(last.message()) : null;
        check("P2pChannel chain: zd decode over the delivered message reproduces every row + type",
                dec != null && dec.ok() && rowsEqual(dec.rows(), rows)
                        && dec.type() == P2pMessageType.CHUNK);
    }

    // ================= 8 确定性：同种子两次 → 字节级一致 =================

    private static void determinism() {
        List<ZdRow> rows = sample(SampleKind.MEDIUM_TABLES);
        byte[] meta = "determinism-meta".getBytes(StandardCharsets.UTF_8);
        byte[] a = ZdChannelCodec.encode(P2pMessageType.SYNC, meta, rows);
        byte[] b = ZdChannelCodec.encode(P2pMessageType.SYNC, meta, rows);
        check("determinism: two same-input encodes are byte-identical", Arrays.equals(a, b));
        // RawRows 载体与列表 API 同默认类型（STATUS）时字节一致。
        byte[] viaRaw = ZdChannelCodec.encode(new RawRows(rows, meta));
        byte[] viaList = ZdChannelCodec.encode(meta, rows);
        check("determinism: RawRows carrier encodes to the same bytes as the list API (default type)",
                Arrays.equals(viaRaw, viaList));
    }

    // ================= 固定种子样本（复用 p.2.5.1 生成法） =================

    private enum SampleKind { SMALL_META, MEDIUM_TABLES, LARGE_CHUNKS }

    private static List<ZdRow> sample(SampleKind kind) {
        Random r = new Random(0x5EEDL | kind.ordinal());
        List<ZdRow> out = new ArrayList<>();
        switch (kind) {
            case SMALL_META -> {
                out.add(new ZdRow(0, "world", 0L, 0.0, "", 6));
                out.add(new ZdRow(1, "name", 0L, 0.0, "toterra-w", 0));
                out.add(new ZdRow(2, "seed", 42L, 0.0, "", 0));
                out.add(new ZdRow(3, "time", 0L, 123450.5, "", 0));
                out.add(new ZdRow(2, "gamemode", 1L, 0.0, "", 0));
                out.add(new ZdRow(2, "difficulty", 2L, 0.0, "", 0));
                out.add(new ZdRow(2, "thunder", 0L, 0.0, "", 0));
            }
            case MEDIUM_TABLES -> {
                out.add(new ZdRow(0, "server", 0L, 0.0, "", 3));
                out.add(new ZdRow(0, "players", 0L, 0.0, "", 64));
                for (int i = 0; i < 64; i++) {
                    out.add(new ZdRow(2, "hp", i % 20L, 0.0, "", 0));
                    out.add(new ZdRow(2, "x", (long) r.nextInt(2000), 0.0, "", 0));
                    out.add(new ZdRow(2, "z", (long) r.nextInt(2000), 0.0, "", 0));
                    out.add(new ZdRow(3, "yaw", 0L, r.nextDouble() * 360, "", 0));
                    out.add(new ZdRow(3, "pitch", 0L, r.nextDouble() * 90 - 45, "", 0));
                }
                out.add(new ZdRow(0, "tables", 0L, 0.0, "", 12));
                for (int t = 0; t < 12; t++) {
                    out.add(new ZdRow(1, "name", 0L, 0.0, "region-" + t, 0));
                    out.add(new ZdRow(2, "count", (long) (100 + r.nextInt(4000)), 0.0, "", 0));
                    out.add(new ZdRow(3, "density", 0L, r.nextDouble(), "", 0));
                }
            }
            case LARGE_CHUNKS -> {
                out.add(new ZdRow(0, "chunks", 0L, 0.0, "", 4096));
                for (int i = 0; i < 4096; i++) {
                    out.add(new ZdRow(2, "cx", (long) i, 0.0, "", 0));
                    out.add(new ZdRow(2, "cy", (long) r.nextInt(16), 0.0, "", 0));
                    out.add(new ZdRow(2, "tick", (long) r.nextInt(60000), 0.0, "", 0));
                    out.add(new ZdRow(3, "activity", 0L, r.nextDouble(), "", 0));
                    out.add(new ZdRow(1, "owner", 0L, 0.0, "p" + (i % 50), 0));
                }
            }
        }
        return List.copyOf(out);
    }

    private static boolean rowsEqual(List<ZdRow> a, List<ZdRow> b) {
        if (a.size() != b.size()) {
            return false;
        }
        for (int i = 0; i < a.size(); i++) {
            ZdRow x = a.get(i);
            ZdRow y = b.get(i);
            if (x.kind() != y.kind() || !x.key().equals(y.key())
                    || x.valueI64() != y.valueI64()
                    || Double.doubleToLongBits(x.valueF64()) != Double.doubleToLongBits(y.valueF64())
                    || !x.valueStr().equals(y.valueStr()) || x.childCount() != y.childCount()) {
                return false;
            }
        }
        return true;
    }

    private static byte[] concat(byte[] a, byte[] b) {
        byte[] out = new byte[a.length + b.length];
        System.arraycopy(a, 0, out, 0, a.length);
        System.arraycopy(b, 0, out, a.length, b.length);
        return out;
    }
}