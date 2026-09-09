package io.toterra.subterra.engine.network.bandwidth;

/**
 * 单个技术的量化得分（p.2.4.4）：用于策略命中标记。分数是统计数据的纯函数
 * （无随机），理性文本描述该得分的选择依据。
 * <p>
 * Quantified score of a single technique (p.2.4.4): used for strategy-hit markers. The score is a
 * pure function of the input statistics (no randomness); the rationale text describes why the
 * technique earned this score.
 *
 * @param technique 技术类型。The technique.
 * @param score     量化得分。Quantified score.
 * @param rationale 选择依据（中/英文）。Selection rationale (Chinese/English).
 */
public record ScaledScore(BandwidthTechnique technique, double score, String rationale) {

    /** 校验：score 须为非负有限值。Validate: score must be non-negative and finite. */
    public ScaledScore {
        if (Double.isNaN(score) || Double.isInfinite(score) || score < 0.0) {
            throw new IllegalArgumentException("score must be a finite non-negative value: " + score);
        }
    }
}