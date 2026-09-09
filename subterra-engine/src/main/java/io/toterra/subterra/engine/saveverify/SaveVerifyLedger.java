package io.toterra.subterra.engine.saveverify;

import io.toterra.subterra.engine.saveverify.chunk.ChunkPlan;
import io.toterra.subterra.engine.saveverify.chunk.ChunkVerifier;
import io.toterra.subterra.engine.saveverify.sig.Ed25519Keys;
import io.toterra.subterra.engine.saveverify.sig.SaveSigner;

/**
 * p.2.10.3 可验证存档案账本 —— 对 {@link SaveVerifyArchive} 文档执行五路径校验，返回
 * {@link VerifyResult}。纯静态工具，无内部状态、绝不抛异常（畸形输入以 {@code shape} 判负）。
 * <p>
 * The verifiable-save ledger (p.2.10.3): runs the five-path verification over a
 * {@link SaveVerifyArchive} document and returns a {@link VerifyResult}. Pure static utility with no
 * internal state; never throws (malformed input is ruled invalid with reason {@code shape}).
 *
 * <p>校验五路径（reason 区分）：Five-path verification (distinguished by {@code reason}):
 * <ol>
 *   <li>{@code shape} — 形状/版本/字段缺失/类型非法 / 坏 chunkSize（解析或校验失败）。</li>
 *   <li>{@code signature} — 签名验不过（错钥 / 篡改 chunks / meta / config）。</li>
 *   <li>{@code chunk} — payload 内容自检不过（字节翻转 / 截断 / 块表与内容不符）。</li>
 *   <li>{@code replay} — {@code meta.seq <= lastAcceptedSeq}（重放；调用方传 -1 表示无历史）。</li>
 *   <li>valid — 全部通过。</li>
 * </ol>
 */
public final class SaveVerifyLedger {

    private SaveVerifyLedger() {
    }

    /**
     * 校验一个可验证存档案。内部：decode → 重建 recorded {@link ChunkPlan}(payload, chunks, chunkSize)
     * → {@link ChunkVerifier#verify} 做内容自检；签名头从解析态构建后经 {@link SaveSigner#verify}。
     * 不抛异常；keys/tdText 为 null 或畸形文档 → invalid("shape")。
     *
     * @param tdText        存档案 td 文本（null → invalid）。
     * @param keys          验签密钥对（null → invalid）。
     * @param lastAcceptedSeq 账本上次接受的序号；-1 表示无历史（跳过重放检查）。
     * @return 校验结果。
     */
    public static VerifyResult verify(String tdText, Ed25519Keys keys, long lastAcceptedSeq) {
        if (tdText == null || keys == null) {
            return VerifyResult.FAIL("shape", -1);
        }
        SaveVerifyArchive.ParsedArchive view;
        try {
            view = SaveVerifyArchive.decode(tdText);
        } catch (Exception e) {
            return VerifyResult.FAIL("shape", -1);
        }
        long seq = view.seq();

        // Path 2: strong signature over the canonical head built from the parsed state.
        byte[] head = null;
        try {
            head = archiveHead(view);
        } catch (Exception e) {
            return VerifyResult.FAIL("shape", -1);
        }
        if (!SaveSigner.verify(keys, head, view.sigBytes())) {
            return VerifyResult.FAIL("signature", seq);
        }

        // Path 3: payload content self-check via the recorded chunk plan.
        ChunkPlan plan = new ChunkPlan(view.payloadBytes(), view.chunks(), view.chunkSize());
        String chunkErr = ChunkVerifier.verify(plan);
        if (chunkErr != null) {
            return VerifyResult.FAIL("chunk", seq);
        }

        // Path 4: replay guard (skip when no history is provided).
        if (lastAcceptedSeq != -1 && seq <= lastAcceptedSeq) {
            return VerifyResult.FAIL("replay", seq);
        }

        // Path 5: valid.
        return VerifyResult.PASS(seq);
    }

    /** 从解析视图重建签名头（复刻 archive 的解析态单源构建，保证与 encode 侧字节恒等）。 */
    private static byte[] archiveHead(SaveVerifyArchive.ParsedArchive view) {
        return SaveVerifyArchive.hashHeadFromParsed(
                view.version(), view.seq(), view.chunkSize(), view.chunks());
    }
}