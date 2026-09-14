package io.toterra.subterra.api.world;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/**
 * p.2.33.10 对外世界包域契约门面（final class、静态纯函数、纯 JDK）：确定性接口面，供上层/域模组按
 * 「世界包 WorldPack 段序常量 + export∘rehydrate 往返恒等 + 打包/还原路径穿越防护语义」使用
 * engine.world 域的确定性语义。语义与 {@code engine.world} 的 {@code WorldPack}/{@code WorldPackPacker}
 * （p.2.9.1/.3）一致——本处为契约与数据面注入，engine 为实现镜像（{@code engine.world.WorldApiMirror}），
 * api 不依赖 engine。所有常量/语义均从 engine 实际行为逐字对照落于此，禁止拍脑袋数值。
 * <p>确定性：{@link #worldMarker()} / {@link #version()} 返回世界包文档顶层标记与格式版本；
 * {@link #sectionKeys()} 返回固定段序 {@code version → meta → save → datapack}（镜像 {@code WorldPack.export}
 * 的写序）；{@link #pathWithin} 以 {@code resolve + normalize + startsWith} 判定子路径是否落入目标子树（镜像
 * {@code WorldPackPacker.unpack} 的写回路径穿越防护）。全部为纯函数：无随机、无墙钟；{@link #pathWithin}
 * 为常量时间内的纯路径语义判定。无状态、无副作用。<b>诚实界限</b>：export∘rehydrate 逐字节往返恒等涉及
 * td 编解码 + base64 + 数据包目录遍历的 I/O，由 engine 的 {@code WorldPack.export/rehydrate} 在运行时断言
 * （p.2.9.4 探针），本契约面只锁定其文档段序常量与路径防护语义，不复制 I/O 实现。
 * <p>
 * p.2.33.10 the external world-pack domain contract facade (final class, static pure functions, pure JDK): a
 * deterministic interface surface for upper layers / domain mods to use the deterministic semantics of
 * {@code engine.world}'s section-order constants + export∘rehydrate round-trip identity + pack/unpack path
 * traversal guard. Semantics match {@code WorldPack}/{@code WorldPackPacker} (p.2.9.1/.3) — this is the contract
 * and data-injection surface for the engine to mirror as its implementation
 * ({@code engine.world.WorldApiMirror}), and the api does not depend on the engine. Every constant/semantic here
 * is pinned verbatim from the engine's actual behaviour — nothing is guessed.
 * <p>Deterministic: {@link #worldMarker()} / {@link #version()} return the world-pack document's top-level marker
 * and format version; {@link #sectionKeys()} returns the fixed section order {@code version → meta → save →
 * datapack} (mirrors {@code WorldPack.export}'s write order); {@link #pathWithin} decides whether a child path
 * falls inside a target subtree via {@code resolve + normalize + startsWith} (mirrors {@code WorldPackPacker.unpack}'s
 * write-back traversal guard). All pure functions: no randomness, no wall-clock; {@link #pathWithin} is a pure
 * constant-time path-semantics check. Stateless, side-effect free. <b>Honest boundary</b>: the byte-for-byte
 * export∘rehydrate round-trip identity involves td codec + base64 + datapack-directory traversal I/O and is
 * asserted at runtime by the engine's {@code WorldPack.export/rehydrate} (p.2.9.4 probe); this contract surface
 * only pins the document section-order constants and the path-guard semantics, without reproducing the I/O.
 */
public final class WorldApi {

    private WorldApi() {
    }

    /** The world-pack top-level document marker ({@code "world"}, mirroring {@code engine.world.WorldPack}'s
     *  {@code MARKER}). / 世界包顶层文档标记（{@code "world"}，镜像 {@code engine.world.WorldPack} 的
     *  {@code MARKER}）。 */
    public static String worldMarker() {
        return "world";
    }

    /** The world-pack format version ({@code 1}, mirroring {@code engine.world.WorldPack#VERSION}). /
     *  世界包格式版本（{@code 1}，镜像 {@code engine.world.WorldPack#VERSION}）。 */
    public static long version() {
        return 1L;
    }

    /** The fixed section order of a world-pack document ({@code version → meta → save → datapack}), mirroring
     *  {@code engine.world.WorldPack#export} write order. / 世界包文档的固定段序
     *  （{@code version → meta → save → datapack}），镜像 {@code engine.world.WorldPack#export} 的写序。 */
    public static List<String> sectionKeys() {
        return List.of("version", "meta", "save", "datapack");
    }

    /**
     * Whether the {@code candidate} path, after {@code resolve + normalize}, stays within the {@code target}
     * subtree — the same {@code startsWith} guard {@code engine.world.WorldPackPacker#unpack} applies before
     * writing a slot document back (traversal-escape prevention). Pure and deterministic: absolute, normalised
     * paths compared by prefix; a candidate that escapes {@code target} (e.g. via {@code ..}) yields {@code false}.
     * / 判定 {@code candidate} 路径在 {@code resolve + normalize} 后是否落在 {@code target} 子树内——即
     * {@code engine.world.WorldPackPacker#unpack} 写回槽文档前施加的同一 {@code startsWith} 防护（路径穿越防护）。
     * 纯函数且确定性：比较绝对化、规范化后的路径前缀；逃逸出 {@code target} 的候选（如含 {@code ..}）返回
     * {@code false}。
     *
     * @param target    the traversal target root (non-null).
     * @param candidate the child path to test (non-null).
     * @return {@code true} if the normalised candidate lies within the target subtree.
     * @throws NullPointerException if either argument is null.
     */
    public static boolean pathWithin(Path target, Path candidate) {
        Objects.requireNonNull(target, "target must not be null");
        Objects.requireNonNull(candidate, "candidate must not be null");
        Path t = target.toAbsolutePath().normalize();
        Path c = candidate.toAbsolutePath().normalize();
        return c.startsWith(t);
    }
}