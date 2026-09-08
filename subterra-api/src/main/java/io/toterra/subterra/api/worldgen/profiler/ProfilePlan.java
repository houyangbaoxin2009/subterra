package io.toterra.subterra.api.worldgen.profiler;

import java.util.List;
import java.util.Set;

/**
 * The full configuration of one world profile run (p.1.8.30): the target window,
 * enabled categories, sampling step, an optional slice, the output formats and the
 * sink. Collections are defensively copied to immutable snapshots on construction.
 * Pure and deterministic; no Minecraft runtime.
 * <p>
 * 一次世界档案运行的完整配置（p.1.8.30）：目标窗口、启用的类别、采样步长、可选的切片、
 * 输出格式与落点。构造时集合被防御性拷贝为不可变快照。纯且确定；不依赖 Minecraft 运行时。
 *
 * @param residency  Whether the profile lives on permanently or runs once.
 * @param window     The horizontal area to profile.
 * @param categories The enabled data categories.
 * @param step       The horizontal and vertical sampling stride.
 * @param slice      The optional slice to capture, or {@code null} for none.
 * @param formats    The output formats when a file output is requested.
 * @param sink       Where the completed report goes.
 */
public record ProfilePlan(boolean residency, ProfileWindow window,
                          Set<ProfileCategory> categories, ProfileStep step,
                          ProfileSlice slice, List<ProfileFormat> formats, ProfileSink sink) {

    /** Compact constructor with defensive copies and non-null validation. */
    public ProfilePlan {
        if (window == null) {
            throw new IllegalArgumentException("window must not be null");
        }
        if (step == null) {
            throw new IllegalArgumentException("step must not be null");
        }
        if (sink == null) {
            throw new IllegalArgumentException("sink must not be null");
        }
        if (categories == null || categories.isEmpty()) {
            throw new IllegalArgumentException("categories must not be null or empty");
        }
        if (formats == null || formats.isEmpty()) {
            throw new IllegalArgumentException("formats must not be null or empty");
        }
        categories = Set.copyOf(categories);
        formats = List.copyOf(formats);
    }
}