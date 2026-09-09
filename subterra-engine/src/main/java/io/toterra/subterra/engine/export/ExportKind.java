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
 * 在此之上真正的导出指令接线留到 p.2.18；本子项仅确定此枚举（占位切面）。
 */
public enum ExportKind {

    /** 世界包档案形态（WorldPack）World-pack artifact form ({@code WorldPack}). */
    WORLD_PACK("world"),
    /** 存档导出档案（SaveExportArchive）Save export archive form. */
    SAVE("save"),
    /** 数据包导出档案（DatapackExportArchive）Datapack export archive form. */
    DATAPACK("datapack");

    private final String form;

    ExportKind(String form) {
        this.form = form;
    }

    /** 小写登记名。Lowercase registration name. */
    public String form() {
        return form;
    }
}