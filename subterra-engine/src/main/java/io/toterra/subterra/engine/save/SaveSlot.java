package io.toterra.subterra.engine.save;

/**
 * 存档槽枚举（p.2.3.1 / p.2.3.4）：{@link SaveContainer} 统一持有的六类类型化槽。语义来自
 * ROAD p.2.3：世界 WORLD / 配置 CONFIG / 藏录 LEDGER / 领域档案 DOMAIN / 遗物条目 RELIC /
 * 台账 REGISTER。每个槽带一个小写目录名 {@code dir()}，供其数据落盘存放；键级约束（
 * {@code allowedKeys}）留给后续子项（p.2.3.4），本子项不做约束。
 * <p>
 * Save-slot enum (p.2.3.1 / p.2.3.4): the six typed slots a {@link SaveContainer}
 * holds uniformly, per ROAD p.2.3 — world WORLD / config CONFIG / ledger LEDGER /
 * domain archive DOMAIN / relic entries RELIC / register REGISTER. Each slot carries a
 * lowercase directory name {@code dir()} for its data on disk; key-level constraints
 * ({@code allowedKeys}) are left to a later sub-item (p.2.3.4) and are not constrained
 * here.
 */
public enum SaveSlot {

    /** 世界存档 World save. */
    WORLD("world"),
    /** 配置（双层配置 · 存档侧）Config (double-layer config, save side). */
    CONFIG("config"),
    /** 藏录丝 Ledger. */
    LEDGER("ledger"),
    /** 领域档案 Domain archive. */
    DOMAIN("domain"),
    /** 遗物条目 Relic entries. */
    RELIC("relic"),
    /** 台账 Register. */
    REGISTER("register");

    private final String dir;

    SaveSlot(String dir) {
        this.dir = dir;
    }

    /**
     * 槽的小写目录名，供数据落盘存放。The slot's lowercase directory name for on-disk
     * storage.
     */
    public String dir() {
        return dir;
    }
}