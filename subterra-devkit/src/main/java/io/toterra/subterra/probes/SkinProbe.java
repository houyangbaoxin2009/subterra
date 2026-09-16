package io.toterra.subterra.probes;

import io.toterra.subterra.engine.skin.SkinCore;

import java.util.List;

/**
 * p.2.32.4 确定性探针（纯 JVM）：皮肤补丁数据面 —— 规格校验（UUID 小写归一、
 * NAME 精确、textureId 命名空间形）、解析序首个命中者胜出、DEFAULT 回落、内建
 * 缺省纹理、纹理尺寸校验、同输入同结果、壳接线盘点。禁时序断言。
 *
 * <p>The p.2.32.4 deterministic probe (pure JVM): the skin-patch data plane — spec
 * validation (UUID lowercase normalisation, exact NAME, namespaced textureId),
 * first-match-wins resolution, DEFAULT fallback, the built-in default texture,
 * texture-dimension checks, identical-input identity, and the shell wiring
 * inventory. No timing assertions.
 */
public final class SkinProbe {

    private static int checks;
    private static int failures;

    public static void main(String[] args) {
        specValidation();
        resolutionOrder();
        textureDims();
        wiringInventory();
        System.out.println("[SkinProbe] " + (failures == 0 ? "PASS (" + checks + " checks)"
                : "FAIL (" + failures + " of " + checks + " checks failed)"));
        if (failures > 0) {
            System.exit(1);
        }
    }

    private static void specValidation() {
        reject("blank match_value on UUID", () -> new SkinCore.SkinSpec(SkinCore.Kind.UUID, " ", "subterra:x"));
        reject("texture without namespace", () -> new SkinCore.SkinSpec(SkinCore.Kind.NAME, "Jiro", "no_colon"));
        reject("texture with leading colon", () -> new SkinCore.SkinSpec(SkinCore.Kind.NAME, "Jiro", ":x"));
        SkinCore.SkinSpec s = new SkinCore.SkinSpec(SkinCore.Kind.UUID, "ABC", "subterra:x");
        check("spec: uuid match_value normalised to lowercase", s.matchValue().equals("abc"));
        SkinCore.SkinSpec d = new SkinCore.SkinSpec(SkinCore.Kind.DEFAULT, null, "subterra:default");
        check("spec: DEFAULT carries empty match_value", d.matchValue().isEmpty());
    }

    private static void resolutionOrder() {
        List<SkinCore.SkinSpec> specs = List.of(
                new SkinCore.SkinSpec(SkinCore.Kind.UUID, "0f2a4b6c-8d9e-0f1a-2b3c-4d5e6f708192", "subterra:elder"),
                new SkinCore.SkinSpec(SkinCore.Kind.NAME, "Jiro", "subterra:founder"),
                new SkinCore.SkinSpec(SkinCore.Kind.DEFAULT, null, "subterra:default"));
        SkinCore.Resolution byUuid = SkinCore.resolve(specs,
                "0F2A4B6C-8D9E-0F1A-2B3C-4D5E6F708192", "Someone");
        check("resolve: uuid hit (case-insensitive)", byUuid.textureId().equals("subterra:elder") && byUuid.source().equals("uuid"));
        SkinCore.Resolution byName = SkinCore.resolve(specs,
                "11111111-2222-3333-4444-555555555555", "Jiro");
        check("resolve: name hit", byName.textureId().equals("subterra:founder") && byName.source().equals("name"));
        SkinCore.Resolution viaDefault = SkinCore.resolve(List.of(
                new SkinCore.SkinSpec(SkinCore.Kind.DEFAULT, null, "subterra:default")), "u1", "A");
        check("resolve: default-spec fallback", viaDefault.textureId().equals("subterra:default") && viaDefault.source().equals("default-spec"));
        SkinCore.Resolution builtin = SkinCore.resolve(List.of(), "u2", "B");
        check("resolve: built-in fallback", builtin.textureId().equals(SkinCore.DEFAULT_TEXTURE) && builtin.source().equals("built-in"));
        SkinCore.Resolution again = SkinCore.resolve(specs,
                "0f2a4b6c-8d9e-0f1a-2b3c-4d5e6f708192", "Someone");
        check("resolve: identical inputs identical results", byUuid.equals(again));
        reject("resolve: blank player identity", () -> SkinCore.resolve(specs, " ", "A"));
        reject("resolve: null specs", () -> SkinCore.resolve(null, "u", "A"));
    }

    private static void textureDims() {
        check("dims: 64x64 valid", SkinCore.validTextureDims(64, 64));
        check("dims: 64x32 legacy valid", SkinCore.validTextureDims(64, 32));
        check("dims: 128x128 rejected", !SkinCore.validTextureDims(128, 128));
    }

    private static void wiringInventory() {
        check("wiring: SkinRuntime present (load-only)",
                present("io.toterra.subterra.runtime.skin.SkinRuntime"));
        check("wiring: marker literal in runtime bytes",
                classBytesContain("io.toterra.subterra.runtime.skin.SkinRuntime", "[Subterra skin]"));
        check("wiring: gate literal in runtime bytes",
                classBytesContain("io.toterra.subterra.runtime.skin.SkinRuntime", "subterra.probe.skin"));
    }

    private static void reject(String name, Runnable r) {
        try {
            r.run();
            check("reject: " + name, false);
        } catch (IllegalArgumentException e) {
            check("reject: " + name, true);
        }
    }

    private static boolean present(String fqcn) {
        try {
            Class.forName(fqcn, false, SkinProbe.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException | LinkageError e) {
            return false;
        }
    }

    private static boolean classBytesContain(String fqcn, String literal) {
        try (java.io.InputStream in = SkinProbe.class.getResourceAsStream("/" + fqcn.replace('.', '/') + ".class")) {
            return in != null && new String(in.readAllBytes(), java.nio.charset.StandardCharsets.ISO_8859_1).contains(literal);
        } catch (java.io.IOException e) {
            return false;
        }
    }

    private static void check(String name, boolean ok) {
        checks++;
        if (ok) {
            System.out.println("[PASS] " + name);
        } else {
            failures++;
            System.out.println("[FAIL] " + name);
        }
    }
}
