package io.toterra.subterra.engine.pcg;

/**
 * p.2.13.1 DCG（Deterministic Content Generation）确定性生成核心的受检异常——
 * 当确定性选择操作无法按契约完成时抛出。携带固定原因常量之一作为确定性分类，
 * 可与代码精确比对；消息可附人类可读细节但原因字段是契约锚点。
 * 不引入时间戳/随机/时序，纯单向失败信号。
 * <p>
 * p.2.13.1 checked exception of the deterministic content-generation core,
 * raised when a deterministic selection cannot be completed per the contract.
 * It carries one of the fixed reason constants as a deterministic classification
 * comparable in code; the message may add human-readable detail but the reason
 * field is a contract anchor. No timestamp / random / timing is introduced —
 * a pure one-way failure signal.
 */
public class PcgException extends Exception {

    /** 供给池为空（{@link PcgPick#pick} / {@link PcgPick#pickWeighted} 的空池）/ the supplied pool is empty. */
    public static final String EMPTY_POOL = "EMPTY_POOL";
    /** pickWeighted 遇非正权重 / a non-positive weight passed to pickWeighted. */
    public static final String NEGATIVE_WEIGHT = "NEGATIVE_WEIGHT";
    /** 权重序列长度与条目序列不一致 / the weights sequence length does not match the items sequence. */
    public static final String BOUND = "BOUND";

    private final String reason;

    /**
     * 以固定原因常量构造。Constructs with a fixed reason constant.
     *
     * @param reason 原因常量之一 / one of the fixed reason constants.
     */
    public PcgException(String reason) {
        super(reason);
        this.reason = reason;
    }

    /**
     * 以固定原因常量 + 细节构造。Constructs with a fixed reason constant and detail.
     *
     * @param reason 原因常量 / a fixed reason constant.
     * @param detail 人类可读细节 / human-readable detail.
     */
    public PcgException(String reason, String detail) {
        super(reason + ": " + detail);
        this.reason = reason;
    }

    /** 确定性原因分类。The deterministic reason classification. */
    public String reason() {
        return reason;
    }
}
