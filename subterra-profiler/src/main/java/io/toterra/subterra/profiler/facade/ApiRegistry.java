package io.toterra.subterra.profiler.facade;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import io.toterra.subterra.api.worldgen.profiler.ProfilePlan;
import io.toterra.subterra.api.worldgen.profiler.ProfileReport;
import io.toterra.subterra.api.worldgen.profiler.ProfileSink;
import io.toterra.subterra.api.worldgen.profiler.WorldProfileApi;
import io.toterra.subterra.api.worldgen.profiler.WorldSampler;

/**
 * Pure-JDK, string-id-keyed implementation of {@link WorldProfileApi}
 * (p.1.8.30 "World Profiler"). The MC binding injects a {@link WorldSampler}
 * source and a sink root via {@link #setSamplerSource(Supplier)} / {@link
 * #setSinkRoot(Supplier)}, plus the current run context (seed / dimension /
 * app version) via {@link #setRunContext(long, String, String)}. All state is
 * thread-safe: entries live in a {@link ConcurrentHashMap}, mutable user-facing
 * fields are {@code volatile}/{@link AtomicReference}.
 * <p>
 * {@link WorldProfileApi} 的纯 JDK、字符串 id 键控实现（p.1.8.30 "World Profiler"）。MC
 * 绑定经 {@link #setSamplerSource(Supplier)} / {@link #setSinkRoot(Supplier)} 注入
 * {@link WorldSampler} 源与输出根目录，并经 {@link #setRunContext(long, String, String)}
 * 注入当前运行上下文（seed / dimension / app version）。全部状态线程安全：条目存放于
 * {@link ConcurrentHashMap}，可变用户字段为 {@code volatile} 或 {@link AtomicReference}。
 */
public final class ApiRegistry implements WorldProfileApi {

    private final Map<String, Entry> entries = new ConcurrentHashMap<>();
    private volatile Supplier<WorldSampler> samplerSource;
    private volatile Supplier<Path> sinkRoot;
    private volatile long seed;
    private volatile String dimension = "";
    private volatile String appVersion = "";

    /**
     * Entry holding one registered plan and its cached snapshot.
     * <p>
     * 保存一个已注册计划及其缓存快照的条目。
     */
    private static final class Entry {
        volatile ProfilePlan plan;
        volatile boolean enabled;
        final AtomicReference<ProfileReport> last = new AtomicReference<>();

        Entry(ProfilePlan plan, boolean enabled) {
            this.plan = Objects.requireNonNull(plan, "plan");
            this.enabled = enabled;
        }
    }

    /** Supplies the world sampler for a run; the binding installs this. */
    public void setSamplerSource(Supplier<WorldSampler> source) {
        this.samplerSource = source;
    }

    /** Supplies the base directory under which profile output is written. */
    public void setSinkRoot(Supplier<Path> root) {
        this.sinkRoot = root;
    }

    /** Sets the seed/dimension/app-version metadata stamped into reports. */
    public void setRunContext(long seed, String dimension, String appVersion) {
        this.seed = seed;
        this.dimension = dimension == null ? "" : dimension;
        this.appVersion = appVersion == null ? "" : appVersion;
    }

    @Override
    public void register(String id, ProfilePlan plan) {
        entries.put(id, new Entry(plan, true));
    }

    @Override
    public boolean run(String id) {
        Entry e = entries.get(id);
        if (e == null || !e.enabled) {
            return false;
        }
        Supplier<WorldSampler> ss = samplerSource;
        if (ss == null) {
            return false;
        }
        WorldSampler sampler = ss.get();
        if (sampler == null) {
            return false;
        }
        ProfilePlan plan = e.plan;
        ProfileReport report;
        try {
            if (plan.sink() == ProfileSink.NONE) {
                report = WorldProfileRunner.run(sampler, plan, seed, dimension, appVersion, null);
            } else {
                Path dir = sinkDirectory(plan);
                report = WorldProfileRunner.run(sampler, plan, seed, dimension, appVersion, dir);
            }
        } catch (IOException ex) {
            return false;
        }
        e.last.set(report);
        return true;
    }

    /**
     * Computes the target directory for a write sink: {@code <root>/profile/<seed>
     * _<dim>} under the sink root. Creation of the directory is left to the runner.
     * <p>
     * 为写盘落点计算目标目录：输出根下的 {@code <root>/profile/<seed>_<dim>}。目录的创建由
     * 运行器负责。
     */
    private Path sinkDirectory(ProfilePlan plan) {
        Supplier<Path> root = sinkRoot;
        String base = root != null ? String.valueOf(root.get()) : ".";
        String dirName = seed + "_" + dimension;
        return Path.of(base).resolve("profile").resolve(dirName);
    }

    @Override
    public boolean setEnabled(String id, boolean enabled) {
        Entry e = entries.get(id);
        if (e == null) {
            return false;
        }
        e.enabled = enabled;
        return enabled;
    }

    @Override
    public ProfileReport report(String id) {
        Entry e = entries.get(id);
        return e == null ? null : e.last.get();
    }

    @Override
    public void remove(String id) {
        entries.remove(id);
    }

    /** The number of currently registered ids. */
    public int size() {
        return entries.size();
    }
}