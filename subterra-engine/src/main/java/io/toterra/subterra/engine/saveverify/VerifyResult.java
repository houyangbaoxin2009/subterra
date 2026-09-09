package io.toterra.subterra.engine.saveverify;

/**
 * 一次「可验证存档案」校验的结果（p.2.10.3 五路径验签/自检/重放的判定载体）。不可变记录：
 * {@code valid}=通过，{@code reason}=失败原因分类（shape / signature / chunk / replay；"" 表通过），
 * {@code seq}=档案中的序号（无法解析时 -1）。
 * <p>
 * The verdict of one verifiable-save verification (p.2.10.3, the carrier of the five-path
 * signature/self-check/replay ruling). An immutable record: {@code valid}=passed, {@code reason} is
 * the failure classification ({@code shape / signature / chunk / replay}; {@code ""} when valid),
 * {@code seq}=the archive's sequence (or {@code -1} when unparseable).
 */
public record VerifyResult(boolean valid, String reason, long seq) {

    /** 通过工厂。PASS factory. */
    public static VerifyResult PASS(long seq) {
        return new VerifyResult(true, "", seq);
    }

    /** 失败工厂。FAIL factory. */
    public static VerifyResult FAIL(String reason, long seq) {
        return new VerifyResult(false, reason, seq);
    }
}