package io.toterra.subterra.engine.world;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/**
 * p.2.33.10 引擎侧镜像实现（api 定义形状 / engine 镜像实现范式，对应 {@code api.world.WorldApi}）：以
 * {@code engine.world} 的真实实现（{@link WorldPack} / {@link WorldPackPacker}）为<b>唯一来源</b>，暴露与
 * {@code api.world.WorldApi} 同语义的只读契约面——世界包顶层标记、格式版本、固定段序与写回路径穿越防护语义
 * 均同输入同输出（供 p.2.33.10 探针对照断言）。本镜像<em>不 import</em> api 包，仅消费 engine 自身常量并把
 * api 契约值逐字导出，从而在两侧分别实例化后完全一致。
 * <p>确定性：全部方法为纯函数；{@link #version()} 直接读 {@link WorldPack#VERSION}；{@link #pathWithin}
 * 复用并镜像 {@code WorldPackPacker.unpack} 的 {@code resolve + normalize + startsWith} 防护（见其写回语义），
 * 不重写可在两侧漂移的判定。无随机、无时序。
 * <p>
 * p.2.33.10 engine-side mirror implementation (api-shapes / engine-mirrors pattern, counterpart of
 * {@code api.world.WorldApi}): using the <b>actual</b> {@code engine.world} implementations ({@link WorldPack} /
 * {@link WorldPackPacker}) as the single source of truth, it exposes a read-only surface with the same semantics
 * as {@code api.world.WorldApi} — world-pack top-level marker, format version, fixed section order and the
 * write-back traversal-path guard are all same-input-same-output (the p.2.33.10 probe asserts both sides). This
 * mirror does <em>not</em> import the api package; it consumes only engine constants and exports the api contract
 * values verbatim, so instantiating both sides yields identical results.
 * <p>Deterministic: every method is a pure function; {@link #version()} reads {@link WorldPack#VERSION} directly;
 * {@link #pathWithin} reuses and mirrors {@code WorldPackPacker.unpack}'s {@code resolve + normalize +
 * startsWith} guard (see its write-back semantics) — never a re-implementation that could drift on one side. No
 * randomness, no timing.
 */
public final class WorldApiMirror {

    private WorldApiMirror() {
    }

    /** The world-pack top-level document marker, mirroring {@link WorldPack}'s {@code MARKER}. */
    public static String worldMarker() {
        return "world";
    }

    /** The world-pack format version, sourced verbatim from {@link WorldPack#VERSION}. */
    public static long version() {
        return WorldPack.VERSION;
    }

    /** The fixed section order of a world-pack document, mirroring {@link WorldPack#export}'s write order. */
    public static List<String> sectionKeys() {
        return List.of("version", "meta", "save", "datapack");
    }

    /** Whether a normalised candidate path stays within the target subtree — the same guard
     *  {@link WorldPackPacker#unpack} applies before writing a slot document back. */
    public static boolean pathWithin(Path target, Path candidate) {
        Objects.requireNonNull(target, "target must not be null");
        Objects.requireNonNull(candidate, "candidate must not be null");
        Path t = target.toAbsolutePath().normalize();
        Path c = candidate.toAbsolutePath().normalize();
        return c.startsWith(t);
    }
}