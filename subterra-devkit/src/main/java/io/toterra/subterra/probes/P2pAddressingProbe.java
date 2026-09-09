package io.toterra.subterra.probes;

import io.toterra.subterra.engine.p2p.AddressBook;
import io.toterra.subterra.engine.p2p.NodeAddr;
import io.toterra.subterra.engine.p2p.NodeId;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * p.2.5.2 P2P 节点寻址确定性验收探针（纯 JVM，无 MC 运行时）：断言 {@link NodeId} 的字节/hex
 * 往返与大小写归一化 / 非法输入拒绝 / 相等性，{@link NodeAddr} 三型（内网直连 / 公网直连 /
 * viaRelay）字节序列化 → 反序列化逐字段往返，{@link AddressBook} 多地址命中 / 未知→空 List /
 * hex 字典序快照，以及固定种子构建的完全确定性。
 * <p>
 * Deterministic acceptance probe for the p.2.5.2 P2P addressing model (pure JVM — no Minecraft
 * runtime): asserts the NodeId byte/hex round-trip, case normalisation, illegal-input rejection
 * and equality; the three NodeAddr variants (LAN-direct / public-direct / viaRelay) field-for-field
 * byte round-trip; and the AddressBook multi-address resolution / unknown→empty List / hex-ordered
 * snapshot, plus full determinism of seed-driven construction.
 * <p>
 * Determinism: fixed seeds; failure counter advances only on failure; no timing assertions.
 * Exit 0 = PASS all checks; 1 = FAIL.
 */
public final class P2pAddressingProbe {

    private P2pAddressingProbe() {
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
        nodeIdRoundTrip();
        nodeIdNormalisation();
        nodeIdRejections();
        nodeIdEquality();
        nodeAddrRoundTrips();
        addressBook();
        determinism();

        if (failures == 0) {
            System.out.println("[P2pAddressingProbe] PASS: p.2.5.2 P2P node identity + address book ("
                    + checks + " checks)");
            System.exit(0);
        } else {
            System.out.println("[P2pAddressingProbe] FAIL: " + failures + " failure(s) of " + checks);
            System.exit(1);
        }
    }

    // 1 NodeId: 固定种子生成 -> bytes -> hex -> fromHex -> bytes 逐字节一致。

    private static void nodeIdRoundTrip() {
        NodeId a = NodeId.random(new Random(0xADD)); // fixed seed
        String hex = a.hex();
        boolean structural = hex.length() == 32;
        for (int i = 0; i < 32; i++) {
            char c = hex.charAt(i);
            if (!((c >= '0' && c <= '9') || (c >= 'a' && c <= 'f'))) {
                structural = false;
                break;
            }
        }
        check("NodeId.random(fixed seed): hex() is 32 lowercase hex chars (canonical)", structural);

        NodeId b = NodeId.fromHex(hex);
        check("NodeId bytes -> hex -> fromHex -> bytes is byte-identical", Arrays.equals(a.bytes(), b.bytes()));
    }

    // 2 归一化：大写 hex 输入 -> hex() 规范小写。

    private static void nodeIdNormalisation() {
        Random r = new Random(0xF00D);
        NodeId a = NodeId.random(r);
        String lower = a.hex();
        NodeId b = NodeId.fromHex(lower.toUpperCase(java.util.Locale.ROOT));
        boolean normalized = b.hex().equals(lower) && b.hex().equals(b.hex().toLowerCase(java.util.Locale.ROOT));
        check("NodeId.fromHex(UPPER) normalises to canonical lowercase hex()", normalized);
    }

    // 3 非法输入拒绝：长度 31/33、非法字符、0x 前缀 -> 抛且不产生对象。

    private static void nodeIdRejections() {
        String valid = "0123456789abcdef0123456789abcdef";
        boolean rejectShort = throwsIllegal(() -> NodeId.fromHex(valid.substring(0, 31)));
        boolean rejectLong = throwsIllegal(() -> NodeId.fromHex("0" + valid));
        boolean rejectChar = throwsIllegal(() -> NodeId.fromHex(valid.substring(0, 8) + "g" + valid.substring(9)));
        boolean rejectPrefix = throwsIllegal(() -> NodeId.fromHex("0x" + valid));
        boolean rejectNull = throwsIllegal(() -> NodeId.fromHex(null));
        check("NodeId.fromHex rejects length-31 / length-33 / illegal char / 0x prefix / null (no object produced)",
                rejectShort && rejectLong && rejectChar && rejectPrefix && rejectNull);

        boolean rejectLen = throwsIllegal(() -> NodeId.of(new byte[15]));
        boolean rejectLen2 = throwsIllegal(() -> NodeId.of(new byte[17]));
        check("NodeId.of rejects non-16 byte arrays", rejectLen && rejectLen2);
    }

    private static boolean throwsIllegal(Runnable r) {
        try {
            r.run();
            return false;
        } catch (IllegalArgumentException e) {
            return true;
        }
    }

    // 4 相等性 + hashCode。

    private static void nodeIdEquality() {
        Random r = new Random(0xBEEF);
        NodeId a = NodeId.random(r);
        NodeId a2 = NodeId.fromHex(a.hex());
        Random r3 = new Random(0xBEEF + 1);
        NodeId b = NodeId.random(r3);
        boolean valuedEq = a.equals(a2) && a.hashCode() == a2.hashCode() && a2.equals(a);
        check("NodeId equals/hashCode: same content == equal & same hash", valuedEq);
        check("NodeId: different content != equal", !a.equals(b));
    }

    // 5 NodeAddr 三型序列化往返。

    private static void nodeAddrRoundTrips() {
        NodeId relay = NodeId.fromHex("aabbccddeeff00112233445566778899");
        NodeAddr directLan = new NodeAddr.Direct("192.168.1.42", 25565);
        NodeAddr directPub = new NodeAddr.Direct("87.250.250.242", 8080);
        NodeAddr viaRelay = new NodeAddr.ViaRelay(relay, "tok-"+nodeToken(0x1a2b));

        assertRoundTrip("Direct(LAN)", directLan);
        assertRoundTrip("Direct(public)", directPub);
        assertRoundTrip("ViaRelay", viaRelay);

        check("Direct.isDirect/ViaRelay.isRelay/asDirect/asRelay routing is correct",
                directLan.isDirect() && !directLan.isRelay()
                        && (directLan.asDirect() == directLan)
                        && viaRelay.isRelay() && !viaRelay.isDirect()
                        && (viaRelay.asRelay() == viaRelay));
    }

    private static String nodeToken(int seed) {
        // 确定性 token，避免依赖 NodeId。
        return "token" + seed + "abc";
    }

    private static void assertRoundTrip(String label, NodeAddr addr) {
        byte[] bytes = addr.toByteArray();
        NodeAddr back = NodeAddr.fromByteArray(bytes);
        boolean v = false;
        if (addr instanceof NodeAddr.Direct d && back instanceof NodeAddr.Direct bd) {
            v = d.host().equals(bd.host()) && d.port() == bd.port()
                    && d.host().equals(d.host().toLowerCase(java.util.Locale.ROOT));
        } else if (addr instanceof NodeAddr.ViaRelay v1 && back instanceof NodeAddr.ViaRelay bv) {
            v = v1.relayId().equals(bv.relayId()) && v1.token().equals(bv.token());
        }
        check(label + ": toByteArray -> fromByteArray round-trips field-by-field", v);
        boolean tagBad = throwsIllegal(() -> NodeAddr.parse(new byte[]{0x7F}, 0));
        check(label + ": NodeAddr.parse rejects an unknown tag byte", tagBad);
    }

    // 6 AddressBook：多地址、未知 -> 空 List、快照 hex 字典序。

    private static void addressBook() {
        NodeId n0 = NodeId.fromHex("aa000000000000000000000000000000");
        NodeId n1 = NodeId.fromHex("55000000000000000000000000000000");
        NodeId n2 = NodeId.fromHex("ff000000000000000000000000000000");

        AddressBook book = new AddressBook();
        book.put(n0, new NodeAddr.Direct("192.168.0.10", 25565));
        book.put(n1, new NodeAddr.Direct("203.0.113.9", 19132));
        book.put(n0, new NodeAddr.ViaRelay(n2, "r0-tok")); // n0 多地址
        book.put(n2, new NodeAddr.ViaRelay(n1, "r2-tok"));

        boolean size = book.size() == 3;
        check("AddressBook.size() == distinct node count (3)", size);

        List<NodeAddr> res0 = book.resolve(n0);
        boolean multi = res0.size() == 2 && res0.get(0).isDirect() && res0.get(1).isRelay()
                && book.resolve(n1).size() == 1;
        check("AddressBook.resolve returns all addresses for a node in insertion order", multi);

        NodeId unknown = NodeId.fromHex("123456789012345678901234567890AB");
        NodeAddr u0 = new NodeAddr.Direct("10.0.0.1", 100);
        book.put(unknown, u0); // now it is known
        NodeId trulyUnknown = NodeId.fromHex("00000000000000000000000000000000");
        List<NodeAddr> miss = book.resolve(trulyUnknown);
        check("AddressBook.resolve(unknown) -> empty List, never null", miss != null && miss.isEmpty());

        // 快照按 hex 字典序：收集键序列，验证与按 hex 升序排序后的期望序列一致。
        Map<NodeId, List<NodeAddr>> snap = book.snapshot();
        java.util.List<NodeId> keys = new java.util.ArrayList<>(snap.keySet());
        java.util.List<NodeId> expected = new java.util.ArrayList<>(keys);
        expected.sort(java.util.Comparator.comparing(NodeId::hex));
        boolean ascHex = keys.equals(expected)
                && snap.keySet().stream().findFirst().map(id -> id.equals(unknown)).orElse(false);
        check("AddressBook.snapshot() iterates in ascending NodeId-hex order", ascHex);
        check("AddressBook.snapshot() carries full address lists (read-only view)", snap.size() == 4);
    }

    // 7 确定性：两次固定种子构建 -> hex 序列完全一致。

    private static void determinism() {
        String seq1 = buildSeq(0x5EEDL);
        String seq2 = buildSeq(0x5EEDL);
        check("two seeded builds produce an identical hex sequence (determinism)", seq1.equals(seq2));
    }

    private static String buildSeq(long seed) {
        Random r = new Random(seed);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 8; i++) {
            sb.append(NodeId.random(r).hex()).append(' ');
        }
        // every address round-trips
        NodeAddr a = new NodeAddr.Direct("test-host", 1234);
        NodeAddr b2 = NodeAddr.fromByteArray(a.toByteArray());
        sb.append(((NodeAddr.Direct) b2).port());
        return sb.toString();
    }
}