package io.toterra.subterra.api.export;

/**
 * 导出种类契约（p.2.16.3）：稳定、可枚举的导出形态标识。每种携带一个小写登记名
 * {@code form()}，用于 {@code /subterra export <form>}。本接口是 engine 侧
 * {@code io.toterra.subterra.engine.export.ExportKind}（p.2.9.5 预留）的 api 版镜像——
 * api 不依赖 engine，故以自己的契约表达同一组形态；{@link #worldPack()} / {@link #save()} /
 * {@link #datapack()} 与 engine 枚举的 {@code form()} 字符串一一对应（{@code world} /
 * {@code save} / {@code datapack}）。
 *
 * <p>Export kind contract (p.2.16.3): the stable, enumerable identity of an export
 * form. Each kind carries a lowercase registration name {@code form()} used for
 * {@code /subterra export <form>}. This interface mirrors the engine-side
 * {@code io.toterra.subterra.engine.export.ExportKind} (p.2.9.5 placeholder) —
 * api does not depend on engine, so it expresses the same forms with its own
 * contract; {@link #worldPack()} / {@link #save()} / {@link #datapack()} map 1:1
 * onto the engine enum's {@code form()} strings ({@code world} / {@code save} /
 * {@code datapack}).
 */
public interface ExportKindSpec {

    /** 小写登记名。Lowercase registration name. */
    String form();

    /**
     * 世界包形态，form 为 {@code "world"}（对应 engine {@code ExportKind.WORLD_PACK}）。
     * World-pack form, {@code form() = "world"} (engine {@code ExportKind.WORLD_PACK}).
     */
    static ExportKindSpec worldPack() {
        return Builtin.WORLD_PACK;
    }

    /**
     * 存档导出形态，form 为 {@code "save"}（对应 engine {@code ExportKind.SAVE}）。
     * Save export form, {@code form() = "save"} (engine {@code ExportKind.SAVE}).
     */
    static ExportKindSpec save() {
        return Builtin.SAVE;
    }

    /**
     * 数据包导出形态，form 为 {@code "datapack"}（对应 engine {@code ExportKind.DATAPACK}）。
     * Datapack export form, {@code form() = "datapack"} (engine {@code ExportKind.DATAPACK}).
     */
    static ExportKindSpec datapack() {
        return Builtin.DATAPACK;
    }

    /** 内建种类的固定单例（确定性）。Fixed singleton instances of the built-in kinds. */
    final class Builtin implements ExportKindSpec {

        /** 世界包形态。World-pack form. */
        public static final ExportKindSpec WORLD_PACK = new Builtin("world");
        /** 存档导出形态。Save export form. */
        public static final ExportKindSpec SAVE = new Builtin("save");
        /** 数据包导出形态。Datapack export form. */
        public static final ExportKindSpec DATAPACK = new Builtin("datapack");

        private final String form;

        private Builtin(String form) {
            this.form = form;
        }

        @Override
        public String form() {
            return form;
        }
    }
}
