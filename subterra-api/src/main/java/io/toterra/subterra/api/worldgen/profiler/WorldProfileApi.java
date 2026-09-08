package io.toterra.subterra.api.worldgen.profiler;

/**
 * Facade keyed by string id for registering and running world profiles
 * (p.1.8.30). Implementations are provided by the pure-JDK runner and the MC
 * binding; this interface carries no Minecraft types, so it stays usable from
 * any module.
 * <p>
 * 以字符串 id 为键、用于注册与运行世界档案的门面（p.1.8.30）。实现由纯 JDK 运行器与 MC
 * 绑定提供；本接口不含任何 Minecraft 类型，因此可从任意模块使用。
 */
public interface WorldProfileApi {

    /**
     * Registers (or updates) a named plan. A subsequent {@link #run(String)}
     * collects according to this plan.
     */
    void register(String id, ProfilePlan plan);

    /**
     * Triggers collection/persist for a registered id. With a {@link
     * ProfileSink#NONE} sink the report is only cached.
     *
     * @return true when a fresh report was produced for the id.
     */
    boolean run(String id);

    /** Enables or disables scheduled collection for a registered id. */
    boolean setEnabled(String id, boolean enabled);

    /** Returns the most recent report for an id, or null when none exists. */
    ProfileReport report(String id);

    /** Removes the registration and cached report for an id. */
    void remove(String id);
}