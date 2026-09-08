package io.toterra.subterra.profiler.report;

import io.toterra.subterra.api.worldgen.profiler.ProfileReport;
import io.toterra.subterra.engine.config.Td;
import io.toterra.subterra.engine.config.TdTable;

/**
 * Writes a {@link ProfileReport} as td text (p.1.8.30). The td rendering is a
 * sibling of {@link ZdWriter}: both serialize the same {@link ProfileTree}, so
 * the two formats carry identical content. The output is readable back by {@code
 * subterra-config} {@link Td#parse} and by tiec's {@code parse_data}.
 * <p>
 * 把 {@link ProfileReport} 写为 td 文本（p.1.8.30）。td 渲染是 {@link ZdWriter} 的孪生：
 * 两者序列化同一棵 {@link ProfileTree}，因此两种格式内容一致。输出可被 {@code
 * subterra-config} 的 {@link Td#parse} 以及 tiec 的 {@code parse_data} 读回。
 */
public final class TdWriter {

    private TdWriter() {
    }

    /**
     * Renders the report tree to td text (2-space indentation).
     * <p>
     * 把报告树渲染为 td 文本（2 空格缩进）。
     *
     * @param r the completed report.
     * @return the td serialization of the report.
     */
    public static String write(ProfileReport r) {
        TdTable tree = ProfileTree.toTd(r);
        return Td.write(tree);
    }
}