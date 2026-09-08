package io.toterra.subterra.probes;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * p.2.0.10 dependency iron-law probe (pure JDK, zero external dependencies —
 * no ASM, no ClassGraph). Scans compiled class files with a hand-rolled
 * constant-pool walk and enforces the p.2.0 layering:
 * <pre>
 *   api     : no engine / runtime / migrate / probes / net.minecraft / net.neoforged
 *   engine  : no runtime / migrate / probes / net.minecraft / net.neoforged
 *   runtime : no migrate / probes (may reference engine + api + MC)
 *   migrate : no engine / runtime / probes / net.minecraft / net.neoforged
 * </pre>
 * plus a cross-root duplicate-FQCN gate (replaces the silent
 * {@code duplicatesStrategy.EXCLUDE} risk in the aggregated mod jar).
 * <p>
 * Class references, descriptors and NameAndType strings all surface as Utf8
 * entries in the constant pool with internal names (e.g.
 * {@code io/toterra/subterra/engine/...}), so prefix-matching the collected
 * Utf8 strings is sufficient. Javadoc mentions never reach the constant pool
 * (compile-time stripped) and are intentionally not considered.
 * <p>
 * CLI: pairs of {@code (label, classesDirs)} for labels {@code api} /
 * {@code engine} / {@code runtime} / {@code migrate}; {@code classesDirs} is a
 * path-separator-delimited list of compiled class output dirs.
 * Exit 0 = PASS, exit 1 = FAIL (gates acceptance; never shipped in the mod jar).
 */
public final class IronLawProbe {

    private IronLawProbe() {
    }

    /** Per-root forbidden internal-name prefixes, keyed by label. */
    private static final Map<String, String[]> FORBIDDEN = new HashMap<>();

    static {
        FORBIDDEN.put("api", new String[]{
                "io/toterra/subterra/engine/", "io/toterra/subterra/runtime/",
                "io/toterra/subterra/migrate/", "io/toterra/subterra/probes/",
                "net/minecraft/", "net/neoforged/"});
        FORBIDDEN.put("engine", new String[]{
                "io/toterra/subterra/runtime/", "io/toterra/subterra/migrate/",
                "io/toterra/subterra/probes/", "net/minecraft/", "net/neoforged/"});
        FORBIDDEN.put("runtime", new String[]{
                "io/toterra/subterra/migrate/", "io/toterra/subterra/probes/"});
        FORBIDDEN.put("migrate", new String[]{
                "io/toterra/subterra/engine/", "io/toterra/subterra/runtime/",
                "io/toterra/subterra/probes/", "net/minecraft/", "net/neoforged/"});
    }

    public static void main(String[] args) {
        if (args.length == 0 || (args.length & 1) != 0) {
            System.out.println("[IronLawProbe] FAIL usage: <label> <classesDirs> [<label> <classesDirs>...]");
            System.exit(1);
        }
        Map<String, List<Path>> roots = new HashMap<>();
        for (int i = 0; i < args.length; i += 2) {
            String label = args[i];
            if (!FORBIDDEN.containsKey(label)) {
                System.out.println("[IronLawProbe] FAIL unknown label: " + label);
                System.exit(1);
            }
            List<Path> dirs = roots.computeIfAbsent(label, k -> new ArrayList<>());
            for (String d : args[i + 1].split(Pattern.quote(File.pathSeparator))) {
                if (d.isEmpty()) {
                    continue;
                }
                Path p = Path.of(d);
                if (!Files.isDirectory(p)) {
                    System.out.println("[IronLawProbe] FAIL classes dir not found: " + p);
                    System.exit(1);
                }
                dirs.add(p);
            }
        }

        Map<String, String> fqcnRoot = new HashMap<>();
        int failures = 0;
        for (Map.Entry<String, List<Path>> entry : roots.entrySet()) {
            String label = entry.getKey();
            int[] stat = new int[2]; // [0] classes scanned, [1] violations
            for (Path root : entry.getValue()) {
                scan(label, root, root, fqcnRoot, stat);
            }
            System.out.println("[IronLawProbe] " + label + ": " + stat[0] + " class(es), "
                    + stat[1] + " violation(s)");
            failures += stat[1];
        }
        if (failures == 0) {
            System.out.println("[IronLawProbe] PASS (api <- engine <- runtime / migrate iron law holds, no duplicate FQCN)");
            System.exit(0);
        } else {
            System.out.println("[IronLawProbe] FAIL: " + failures + " violation(s)");
            System.exit(1);
        }
    }

    private static void scan(String label, Path base, Path dir, Map<String, String> fqcnRoot, int[] stat) {
        try (Stream<Path> children = Files.list(dir)) {
            children.forEach(child -> {
                if (Files.isDirectory(child)) {
                    scan(label, base, child, fqcnRoot, stat);
                } else if (child.getFileName().toString().endsWith(".class")) {
                    stat[0]++;
                    checkClass(label, base, child, fqcnRoot, stat);
                }
            });
        } catch (IOException e) {
            throw new UncheckedIOException("cannot list " + dir, e);
        }
    }

    private static void checkClass(String label, Path base, Path file, Map<String, String> fqcnRoot, int[] stat) {
        String rel = base.relativize(file).toString().replace('\\', '/');
        String fqcn = rel.substring(0, rel.length() - ".class".length()).replace('/', '.');
        String prev = fqcnRoot.putIfAbsent(fqcn, label);
        if (prev != null && !prev.equals(label)) {
            System.out.println("[IronLawProbe] FAIL duplicate FQCN " + fqcn
                    + " in roots [" + prev + "] and [" + label + "]");
            stat[1]++;
        }
        byte[] buf;
        try {
            buf = Files.readAllBytes(file);
        } catch (IOException e) {
            System.out.println("[IronLawProbe] FAIL " + label + " class " + fqcn + ": unreadable (" + e + ")");
            stat[1]++;
            return;
        }
        List<String> utf8;
        try {
            utf8 = parseUtf8(fqcn, buf);
        } catch (FormatException e) {
            System.out.println("[IronLawProbe] FAIL " + label + " class " + fqcn + ": " + e.getMessage());
            stat[1]++;
            return;
        }
        String[] forbidden = FORBIDDEN.get(label);
        for (String u : utf8) {
            for (String prefix : forbidden) {
                if (u.contains(prefix)) {
                    System.out.println("[IronLawProbe] FAIL " + label + " class " + fqcn
                            + ": forbidden reference to '" + prefix + "' in Utf8 \"" + u + "\"");
                    stat[1]++;
                }
            }
        }
    }

    /** Constant-pool walk; collects every Utf8 entry. Throws on malformed input. */
    private static List<String> parseUtf8(String fqcn, byte[] buf) throws FormatException {
        int p = 0;
        int magic;
        try {
            magic = readU4(buf, p);
            p += 4;
            if (magic != 0xCAFEBABE) {
                throw new FormatException("bad magic 0x" + Integer.toHexString(magic));
            }
            p += 4; // minor + major
            int cpCount = readU2(buf, p);
            p += 2;
            List<String> utf8 = new ArrayList<>();
            for (int idx = 1; idx < cpCount; idx++) {
                int tag = buf[p] & 0xFF;
                p += 1;
                switch (tag) {
                    case 1: { // Utf8
                        int len = readU2(buf, p);
                        p += 2;
                        utf8.add(new String(buf, p, len, StandardCharsets.UTF_8));
                        p += len;
                        break;
                    }
                    case 3: // Integer
                    case 4: // Float
                        p += 4;
                        break;
                    case 5: // Long (two slots)
                    case 6: // Double (two slots)
                        p += 8;
                        idx++;
                        break;
                    case 7: // Class
                    case 8: // String
                    case 16: // MethodType
                    case 19: // Module
                    case 20: // Package
                        p += 2;
                        break;
                    case 9: // Fieldref
                    case 10: // Methodref
                    case 11: // InterfaceMethodref
                    case 12: // NameAndType
                        p += 4;
                        break;
                    case 15: // MethodHandle (u1 + u2)
                        p += 3;
                        break;
                    case 17: // Dynamic
                    case 18: // InvokeDynamic
                        p += 4;
                        break;
                    default:
                        throw new FormatException("unknown constant-pool tag " + tag + " at index " + idx);
                }
            }
            return utf8;
        } catch (ArrayIndexOutOfBoundsException e) {
            throw new FormatException("truncated class file (parse ran off the end of " + fqcn + ")");
        }
    }

    private static int readU2(byte[] buf, int p) {
        return ((buf[p] & 0xFF) << 8) | (buf[p + 1] & 0xFF);
    }

    private static int readU4(byte[] buf, int p) {
        return ((buf[p] & 0xFF) << 24) | ((buf[p + 1] & 0xFF) << 16)
                | ((buf[p + 2] & 0xFF) << 8) | (buf[p + 3] & 0xFF);
    }

    private static final class FormatException extends Exception {
        FormatException(String msg) {
            super(msg);
        }
    }
}
