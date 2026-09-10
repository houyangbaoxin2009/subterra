package io.toterra.subterra.engine.export;

/**
 * p.2.9.5 export kinds (p.2.18 landing point) — the stable enumerable identities
 * of the export ports an engine exposes to the p.2.18 command layer. Each kind
 * carries a lowercase registration name {@code form()} used for /
 * <code>subterra export &lt;form&gt;</code>. The actual export command wiring on
 * top of these ports is a p.2.18 concern; this sub-item only fixes the enum
 * (the placeholder cut surface).
 *
 * <p>p.2.9.5 导出种类（p.2.18 落点）——engine 面向 p.2.18 指令层开放的各导出端口的稳定
 * 可枚举标识。每种带一个小写登记名 {@code form()}，用于 {@code /subterra export <form>}。
 * p.2.18.1 在此追加 p.2.18 正式导出表面的四个新形态（{@code language_keys} / {@code config} /
 * {@code registries} / {@code migrate_maps}），追加在既有三枚举之后、既有枚举序不变。
 *
 * <p>p.2.9.5 export kinds (p.2.18 landing point) — the stable enumerable identities
 * of the export ports an engine exposes to the p.2.18 command layer. Each kind
 * carries a lowercase registration name {@code form()} used for /
 * <code>subterra export &lt;form&gt;</code>. p.2.18.1 appends the four new forms of
 * the p.2.18 formal export surface ({@code language_keys} / {@code config} /
 * {@code registries} / {@code migrate_maps}) after the existing three, keeping the
 * existing enum order intact.
 */
public enum ExportKind {

    /** 世界包档案形态（WorldPack）World-pack artifact form ({@code WorldPack}). */
    WORLD_PACK("world"),
    /** 存档导出档案（SaveExportArchive）Save export archive form. */
    SAVE("save"),
    /** 数据包导出档案（DatapackExportArchive）Datapack export archive form. */
    DATAPACK("datapack"),
    /** 语言键表单（p.2.18 正式导出表面）Language-keys document form (p.2.18 formal export surface). */
    LANGUAGE_KEYS("language_keys"),
    /** 配置表单（p.2.18 正式导出表面）Config document form (p.2.18 formal export surface). */
    CONFIG("config"),
    /** 注册表表单（p.2.18 正式导出表面）Registries document form (p.2.18 formal export surface). */
    REGISTRIES("registries"),
    /** 迁移映射表单（p.2.18 正式导出表面）Migrate-maps document form (p.2.18 formal export surface). */
    MIGRATE_MAPS("migrate_maps");

    private final String form;

    ExportKind(String form) {
        this.form = form;
    }

    /** 小写登记名。Lowercase registration name. */
    public String form() {
        return form;
    }
}