package io.toterra.subterra.probes;

import io.toterra.subterra.api.worldgen.GeneratorOption;

/**
 * Deterministic acceptance probe for the Subterra-generator td switch + API
 * (p.1.8.22). Pure JVM — no Minecraft runtime; exercises only the pure-JDK
 * {@link GeneratorOption} surface (the MC-side {@code WorldgenConfig} loader is
 * covered by the boot-gate marker, not here).
 * <p>
 * Exit 0 = PASS, exit 1 = FAIL (gates acceptance; never shipped in the mod jar).
 */
public final class GeneratorOptionProbe {

    private static int failures = 0;

    private GeneratorOptionProbe() {
    }

    public static void main(String[] args) {
        // 1. Default: feature OFF.
        check("defaults off",
                !GeneratorOption.defaults().useSubterraAsDefault());

        // 2. td()/fromTd round-trip for the default variant.
        GeneratorOption def = GeneratorOption.defaults();
        check("td default roundtrip",
                GeneratorOption.fromTd(def.td()).equals(def));
        check("td default says false",
                def.td().contains("use_subterra_generator = false"));

        // 3. td()/fromTd round-trip for the enabled variant.
        GeneratorOption en = def.withUseSubterraAsDefault(true);
        check("td enabled roundtrip",
                GeneratorOption.fromTd(en.td()).equals(en));
        check("td enabled says true",
                en.td().contains("use_subterra_generator = true"));
        check("td enabled use flag",
                en.useSubterraAsDefault());

        // 4. Explicit literal parse (both-ways).
        check("fromTd literal true",
                GeneratorOption.fromTd("[ use_subterra_generator = true ]").useSubterraAsDefault());
        check("fromTd literal false",
                !GeneratorOption.fromTd("[ use_subterra_generator = false ]").useSubterraAsDefault());

        // 5. fromTd rejects an unknown key.
        check("reject unknown key",
                rejects("[ use_other_generator = true ]"));
        check("reject unknown key mixed",
                rejects("[\n  use_subterra_generator = true,\n  bogus = 1,\n]"));

        // 6. fromTd rejects a malformed value / garbage.
        check("reject bad bool",
                rejects("[ use_subterra_generator = maybe ]"));
        check("reject empty value",
                rejects("[ use_subterra_generator = ]"));
        check("reject garbage",
                rejects("not a td document at all"));
        check("reject unboxed",
                rejects("use_subterra_generator = true"));
        check("reject null",
                rejects(null));

        // 7. withUseSubterraAsDefault mutates only that field (immutability).
        check("immutable receiver unchanged",
                !def.useSubterraAsDefault());
        check("immutable new instance",
                en != def);
        check("disable restores default",
                GeneratorOption.defaults().withUseSubterraAsDefault(false)
                        .equals(GeneratorOption.defaults()));

        // 8. equals / hashCode consistency.
        check("equals duplicate",
                new GeneratorOption(true).equals(en));
        check("equals diff value",
                !new GeneratorOption(true).equals(def));
        check("hashCode stable",
                def.hashCode() == GeneratorOption.fromTd(def.td()).hashCode());

        // 9. Tolerant input forms (empty table, trailing comma, header, comments, 0/1).
        check("empty table defaults",
                GeneratorOption.fromTd("[]").equals(GeneratorOption.defaults()));
        check("trailing comma tolerated",
                GeneratorOption.fromTd("[ use_subterra_generator = true, ]").useSubterraAsDefault());
        check("header + comments + 1 as bool",
                GeneratorOption.fromTd("type tie<data>\n// demo\n[\n  use_subterra_generator = 1,\n]")
                        .useSubterraAsDefault());
        check("0 as bool off",
                !GeneratorOption.fromTd("[ use_subterra_generator = 0 ]").useSubterraAsDefault());

        // 10. Multi-entry with the recognised key still works; ordering agnostic.
        check("multi-entry true last",
                GeneratorOption.fromTd(
                        "[\n  use_subterra_generator = false,\n  use_subterra_generator = true,\n]")
                        .useSubterraAsDefault());

        if (failures == 0) {
            System.out.println("[GeneratorOptionProbe] PASS (16+ td/API checks)");
            System.exit(0);
        } else {
            System.out.println("[GeneratorOptionProbe] FAIL: " + failures + " assertion(s)");
            System.exit(1);
        }
    }

    private static boolean rejects(String source) {
        try {
            GeneratorOption.fromTd(source);
            return false;
        } catch (IllegalArgumentException expected) {
            return true;
        }
    }

    private static void check(String what, boolean ok) {
        if (!ok) {
            failures++;
            System.out.println("[GeneratorOptionProbe] FAIL " + what);
        }
    }
}