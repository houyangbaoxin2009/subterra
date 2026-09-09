// Deterministic corruption/tamper probe for p.2.10.4: the verifiable-save archive's
// five corruption classes (byte-flip / truncate / reorder / wrong-key / replay) must
// be rejected with the pinned reason, while the intact archive and the re-encode
// round-trip must be accepted. Pure JVM — no MC, no wall-clock, no timestamps, no
// random seeds. NOT shipped in the mod jar.
package io.toterra.subterra.probes;

import io.toterra.subterra.engine.config.Td;
import io.toterra.subterra.engine.config.TdTable;
import io.toterra.subterra.engine.config.TdValue;
import io.toterra.subterra.engine.save.SaveContainer;
import io.toterra.subterra.engine.save.SaveExportArchive;
import io.toterra.subterra.engine.save.SaveSlot;
import io.toterra.subterra.engine.saveverify.SaveVerifyArchive;
import io.toterra.subterra.engine.saveverify.SaveVerifyArchive.ParsedArchive;
import io.toterra.subterra.engine.saveverify.SaveVerifyLedger;
import io.toterra.subterra.engine.saveverify.VerifyResult;
import io.toterra.subterra.engine.saveverify.sig.Ed25519Keys;

import java.util.Arrays;
import java.util.Base64;
import java.util.List;

/**
 * p.2.10.4 可验证存档「损坏/篡改确定性探针」—— 覆盖五类违规：单字节翻转（chunk）、截断（chunk）、
 * 换序重排（chunk/signature）、错钥（signature）、重放（replay），外加完好档接受、编码确定性、
 * 解析态重编码不变量（逐字节恒等）与同输入恒同签名。密钥以固定 base64 编码注入（p.2.10.2
 * 「确定性密钥注入」契约），断言全确定性、字节相等为最强判据。退出码 0 = PASS。
 *
 * <p>p.2.10.4 verifiable-save corruption/tamper deterministic probe — five corruption classes
 * (single-byte flip → chunk, truncate → chunk, reorder → chunk/signature, wrong key → signature,
 * replay → replay) all rejected, intact archive accepted, encoding deterministic, re-encode
 * round-trip byte-identical and same-input-same-signature. Keys are injected as fixed base64
 * encoded bytes (the p.2.10.2 deterministic-keypair-injection contract); all assertions are
 * deterministic. Exit 0 = PASS.
 */
public final class SaveVerifyProbe {

    private SaveVerifyProbe() {
    }

    // ---- fixed injected keys (p.2.10.2 deterministic-keypair-injection contract) ----
    // Generated once via Ed25519Keys.provision(); PKCS#8 private encoding (48B) + RFC 8032
    // public point (32B), both base64. Pair 1 signs/verifies the archives; Pair 2 is the
    // wrong-key control. Kept bare so the probe is self-contained and reproducible.
    private static final String KEY1_PKCS8 = "MC4CAQAwBQYDK2VwBCIEIKdQ9/NsXKkgSo0xd4xcdD+nftR7/sD8D5l0h3xS8twS";
    private static final String KEY1_POINT = "r0zwwEvbROGDiTlj8CRpgiJPg5eupBavwvcoZ5kF2sM=";
    private static final String KEY2_PKCS8 = "MC4CAQAwBQYDK2VwBCIEIJ12g+6txyE2Tom/NZBVb7xxqfmFqexmXnrig+DD5GEP";
    private static final String KEY2_POINT = "sL+qT9dxASt1kozLynfingx1use0ksUDckGwKxXbH4k=";

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
        try {
            run();
        } catch (Exception e) {
            failures++;
            System.out.println("[FAIL] 探针异常: " + e);
            e.printStackTrace(System.out);
        }

        if (failures == 0) {
            System.out.println("SaveVerifyProbe: " + checks + " checks, 0 failures");
            System.exit(0);
        } else {
            System.out.println("SaveVerifyProbe: " + checks + " checks, " + failures + " failures");
            System.exit(1);
        }
    }

    private static void run() {
        Ed25519Keys keys = keys();
        Ed25519Keys wrong = wrongKeys();

        byte[] payload = buildPayload();
        check("载荷: payload 非空", payload.length > 0);

        String seq1 = SaveVerifyArchive.encode(keys, payload, 1);
        String seq2 = SaveVerifyArchive.encode(keys, payload, 2);

        // 1. encoding determinism
        check("完整性初值: encode(seq=1) 两次逐字节同一文本",
                SaveVerifyArchive.encode(keys, payload, 1).equals(seq1));

        ParsedArchive v1 = SaveVerifyArchive.decode(seq1);
        check("不变量: decode 后 chunks 数 >= 1", v1.chunks().size() >= 1);

        // 2. intact archive accepted
        VerifyResult intact = SaveVerifyLedger.verify(seq1, keys, -1);
        check("完好档: verify(text, keys, -1) → valid", intact.valid() && intact.reason().isEmpty());

        // 3. re-encode round-trip invariant (byte-identity, same semantics as p.2.9)
        String text2 = SaveVerifyArchive.reencode(keys, SaveVerifyArchive.decode(seq1));
        check("解析态重编码不变量: reencode(decode(text)) 与 text 逐字节相等", text2.equals(seq1));
        check("解析态重编码不变量: verify(text2, keys, -1) → valid",
                SaveVerifyLedger.verify(text2, keys, -1).valid());

        // 4. single-byte flip → chunk
        byte[] flipped = payload.clone();
        flipped[flipped.length / 2] ^= 0x01;
        VerifyResult flip = SaveVerifyLedger.verify(withPayload(seq1, flipped), keys, -1);
        check("单字节翻转: 中间字节翻转 1 位 → invalid 且 reason=='chunk'",
                !flip.valid() && "chunk".equals(flip.reason()));

        // 5. truncate (drop last 8 bytes) → chunk
        byte[] truncated = Arrays.copyOf(payload, payload.length - 8);
        VerifyResult trunc = SaveVerifyLedger.verify(withPayload(seq1, truncated), keys, -1);
        check("截断: 去掉末 8 字节 → invalid 且 reason=='chunk'",
                !trunc.valid() && "chunk".equals(trunc.reason()));

        // 6. reorder: swap two chunks rows (row content unchanged, index travels), no re-sign
        VerifyResult reorder = SaveVerifyLedger.verify(reorderedChunks(seq1), keys, -1);
        check("换序重排: 互换两行 chunks → invalid 且 reason∈{chunk,signature}",
                !reorder.valid()
                        && ("chunk".equals(reorder.reason()) || "signature".equals(reorder.reason())));

        // 7. wrong key (second fixed pair) → signature
        VerifyResult wrongKey = SaveVerifyLedger.verify(seq1, wrong, -1);
        check("错钥: 用第二把固定密钥验完好文本 → invalid 且 reason=='signature'",
                !wrongKey.valid() && "signature".equals(wrongKey.reason()));

        // 8. replay guard
        VerifyResult rp1 = SaveVerifyLedger.verify(seq1, keys, 1);
        check("重放: seq=1 档以 lastAcceptedSeq=1 验 → invalid 且 reason=='replay'",
                !rp1.valid() && "replay".equals(rp1.reason()));
        check("重放: seq=2 档以 lastAcceptedSeq=1 验 → valid（新序号通过）",
                SaveVerifyLedger.verify(seq2, keys, 1).valid());
        VerifyResult rp2 = SaveVerifyLedger.verify(seq2, keys, 2);
        check("重放: seq=2 档以 lastAcceptedSeq=2 验 → invalid 且 reason=='replay'",
                !rp2.valid() && "replay".equals(rp2.reason()));

        // 9. same-input-same-signature (symbol level, complements #1)
        ParsedArchive d2 = SaveVerifyArchive.decode(SaveVerifyArchive.reencode(keys, SaveVerifyArchive.decode(seq1)));
        check("同输入恒同签名: decode(sigBytes) 与重编码后 sigBytes 逐字节一致",
                Arrays.equals(v1.sigBytes(), d2.sigBytes()));

        // 10. shape rejection: version 1 → 0
        VerifyResult shape = SaveVerifyLedger.verify(seq1.replace("version = 1", "version = 0"), keys, -1);
        check("形状拒绝: version=1 改 version=0 → invalid 且 reason=='shape'",
                !shape.valid() && "shape".equals(shape.reason()));
    }

    // ---------- fixture: p.2.3 save-container export as the protected payload ----------

    /** Builds a multi-slot SaveContainer and exports its SaveExportArchive td text as payload bytes. */
    private static byte[] buildPayload() {
        TdTable worldDoc = TdTable.builder()
                .put("gametime", TdValue.of(12000L))
                .put("dayTime", TdValue.of(6000L))
                .put("raining", TdValue.of(false))
                .put("thundering", TdValue.of(false))
                .put("spawnX", TdValue.of(0L))
                .put("spawnY", TdValue.of(64L))
                .put("spawnZ", TdValue.of(0L))
                .put("biome", TdValue.str("toterra_plains"))
                .put("dimension", TdValue.str("toterra:overworld"))
                .build();
        TdTable ledgerDoc = TdTable.builder()
                .put("runes.ancient", TdTable.builder()
                        .put("count", TdValue.of(1L)).put("rarity", TdValue.of(1L)).put("seed", TdValue.str("seed-ancient")).build())
                .put("totems.dusk", TdTable.builder()
                        .put("count", TdValue.of(3L)).put("rarity", TdValue.of(2L)).put("seed", TdValue.str("seed-dusk")).build())
                .put("relics.dusk-brooch", TdTable.builder()
                        .put("count", TdValue.of(1L)).put("rarity", TdValue.of(2L)).put("seed", TdValue.str("seed-brooch")).build())
                .build();
        TdTable relicDoc = TdTable.builder()
                .put("relics.amber-fang", TdTable.builder()
                        .put("stack", TdValue.of(1L)).put("enchant", TdValue.str("sharpness_2")).put("durability", TdValue.of(77L)).build())
                .put("relics.dusk-brooch", TdTable.builder()
                        .put("stack", TdValue.of(1L)).put("enchant", TdValue.str("protection_3")).put("durability", TdValue.of(120L)).build())
                .build();

        SaveContainer container = new SaveContainer()
                .attach(SaveSlot.WORLD, worldDoc)
                .attach(SaveSlot.LEDGER, ledgerDoc)
                .attach(SaveSlot.RELIC, relicDoc);
        return SaveExportArchive.export(container).getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }

    // ---------- key injection ----------

    private static Ed25519Keys keys() {
        return Ed25519Keys.fromEncoded(
                Base64.getDecoder().decode(KEY1_PKCS8),
                Base64.getDecoder().decode(KEY1_POINT));
    }

    private static Ed25519Keys wrongKeys() {
        return Ed25519Keys.fromEncoded(
                Base64.getDecoder().decode(KEY2_PKCS8),
                Base64.getDecoder().decode(KEY2_POINT));
    }

    // ---------- td-level tamper helpers (meta/config/chunks/fields otherwise frozen) ----------

    /** The top-level saveverify doc table of an archive text. */
    private static TdTable docOf(String text) {
        return (TdTable) Td.parse(text).get(SaveVerifyArchive.MARKER);
    }

    /** Rewraps a doc table back into archive text (type header + named top-level table). */
    private static String rewrap(TdTable doc) {
        return "type tie<data>\n" + Td.write(TdTable.builder().put(SaveVerifyArchive.MARKER, doc).build());
    }

    /** Same doc but with the `payload` field replaced by new bytes (rest byte-identical). */
    private static String withPayload(String text, byte[] newPayload) {
        TdTable doc = docOf(text);
        TdTable.Builder b = TdTable.builder();
        for (String k : doc.keys()) {
            TdValue v = "payload".equals(k)
                    ? TdValue.str(Base64.getEncoder().encodeToString(newPayload))
                    : doc.get(k);
            b.put(k, v);
        }
        return rewrap(b.build());
    }

    /** Same doc but with the first two chunk rows swapped (content unchanged, index travels). */
    private static String reorderedChunks(String text) {
        TdTable doc = docOf(text);
        TdTable chunksT = (TdTable) doc.get("chunks");
        List<TdValue> elems = chunksT.elements();
        if (elems.size() < 2) {
            throw new IllegalStateException("reorder requires at least 2 chunks, got " + elems.size());
        }
        TdTable.Builder cb = TdTable.builder();
        cb.element(elems.get(1));
        cb.element(elems.get(0));
        for (int i = 2; i < elems.size(); i++) {
            cb.element(elems.get(i));
        }
        TdTable.Builder b = TdTable.builder();
        for (String k : doc.keys()) {
            b.put(k, "chunks".equals(k) ? cb.build() : doc.get(k));
        }
        return rewrap(b.build());
    }
}