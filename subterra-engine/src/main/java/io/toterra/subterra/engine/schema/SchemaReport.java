package io.toterra.subterra.engine.schema;

/**
 * 确定性校验结果 —— {@link Schema#validate(TdTable)} 的判定载体。不可变记录：
 * {@code valid}=通过，{@code reason}=固定失败词汇（{@code field type mismatch} /
 * {@code missing field} / {@code unknown field}；"" 表通过），{@code fieldPath}=定位失败的字段名或
 * 元素路径（如 {@code element[0]}；通过时为 ""）。非法数据不抛异常，仅以 {@code valid=false} 报告。
 * <p>
 * Deterministic validation verdict — the carrier produced by {@link Schema#validate(TdTable)}. An
 * immutable record: {@code valid}=passed, {@code reason} is a fixed failure vocabulary
 * ({@code field type mismatch} / {@code missing field} / {@code unknown field}; {@code ""} when valid),
 * {@code fieldPath} locates the offending field name or element path (e.g. {@code element[0]};
 * {@code ""} when valid). Invalid data is never raised; it is only reported as {@code valid=false}.
 *
 * @param valid     是否通过 / whether it passed.
 * @param reason    固定失败词汇（通过为 ""）/ the fixed failure reason ({@code ""} when valid).
 * @param fieldPath 失败定位（通过为 ""）/ the failing field path ({@code ""} when valid).
 */
public record SchemaReport(boolean valid, String reason, String fieldPath) {

    /** 通过工厂。PASS factory. */
    public static SchemaReport PASS() {
        return new SchemaReport(true, "", "");
    }

    /** 失败工厂。FAIL factory. */
    public static SchemaReport FAIL(String reason, String fieldPath) {
        return new SchemaReport(false, reason, fieldPath);
    }
}
