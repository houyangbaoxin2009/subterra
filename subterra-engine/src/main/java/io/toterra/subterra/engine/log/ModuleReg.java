package io.toterra.subterra.engine.log;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Registry of loaded modules and their dependency edges — the data source for
 * the crash-dump dependency diagnosis block (mirrors the "loaded mods" section
 * of a Minecraft crash report). Also performs dependency-cycle detection
 * (DFS with three-colour marking) so a circular dependency is reported with the
 * actual path instead of a silent failure.
 */
public final class ModuleReg {

    /**
     * One registered module.
     *
     * @param name         module id
     * @param version      module version (p-track, e.g. p.1.6.0)
     * @param dependencies dependency module ids, in declaration order
     */
    public record Entry(String name, String version, List<String> dependencies) {
    }

    private static final ModuleReg INSTANCE = new ModuleReg();

    private final Map<String, Entry> modules = new LinkedHashMap<>();
    private final Map<String, List<String>> edges = new LinkedHashMap<>();

    private ModuleReg() {
    }

    public static ModuleReg registry() {
        return INSTANCE;
    }

    /** Registers (or replaces) a module and its dependency edges. */
    public synchronized void register(String name, String version, String... dependencies) {
        List<String> deps = List.copyOf(Arrays.asList(dependencies));
        modules.put(name, new Entry(name, version, deps));
        edges.put(name, new ArrayList<>(deps));
    }

    public synchronized List<Entry> modules() {
        return List.copyOf(modules.values());
    }

    /**
     * Detects dependency cycles. Each reported cycle is the dependency path
     * (e.g. {@code [a, b]} for a→b→a; {@code [a]} for a self-loop); duplicate
     * rotations are de-duplicated.
     */
    public synchronized List<List<String>> dependencyCycles() {
        Set<String> visited = new LinkedHashSet<>();       // black
        Set<String> onStack = new LinkedHashSet<>();       // gray
        Deque<String> stack = new ArrayDeque<>();
        Set<String> seen = new LinkedHashSet<>();          // normalized cycle keys
        List<List<String>> cycles = new ArrayList<>();

        for (String node : edges.keySet()) {
            if (!visited.contains(node)) {
                dfs(node, visited, onStack, stack, seen, cycles);
            }
        }
        return cycles;
    }

    private void dfs(String node, Set<String> visited, Set<String> onStack,
                     Deque<String> stack, Set<String> seen, List<List<String>> cycles) {
        visited.add(node);
        onStack.add(node);
        stack.push(node);
        for (String dep : edges.getOrDefault(node, List.of())) {
            if (onStack.contains(dep)) {
                List<String> cycle = new ArrayList<>();
                for (String n : stack) {
                    cycle.add(n);
                    if (n.equals(dep)) {
                        break;
                    }
                }
                java.util.Collections.reverse(cycle);
                String key = rotationKey(cycle);
                if (seen.add(key)) {
                    cycles.add(cycle);
                }
            } else if (!visited.contains(dep)) {
                dfs(dep, visited, onStack, stack, seen, cycles);
            }
        }
        stack.pop();
        onStack.remove(node);
    }

    /**
     * Canonical key for cycle de-duplication: the lexicographically-smallest
     * rotation of the cycle, joined by arrows. The DFS path contains each node
     * at most once apart from the closing edge, so the rotation is lossless.
     */
    private static String rotationKey(List<String> cycle) {
        int best = 0;
        for (int i = 1; i < cycle.size(); i++) {
            if (cycle.get(i).compareTo(cycle.get(best)) < 0) {
                best = i;
            }
        }
        StringBuilder key = new StringBuilder();
        for (int i = 0; i < cycle.size(); i++) {
            if (i > 0) {
                key.append("->");
            }
            key.append(cycle.get((best + i) % cycle.size()));
        }
        return key.toString();
    }

    /**
     * Human-readable dependency block for crash dumps (crash-report style):
     * <pre>Modules:
     *   subterra-launch p.1.1.0
     *   subterra-compat p.1.2.0 -&gt; subterra-launch</pre>
     */
    public synchronized String describe() {
        StringBuilder sb = new StringBuilder();
        sb.append("Modules:");
        if (modules.isEmpty()) {
            sb.append(" (none)");
            return sb.toString();
        }
        for (Entry e : modules.values()) {
            sb.append("\n  ").append(e.name()).append(' ').append(e.version());
            if (!e.dependencies().isEmpty()) {
                sb.append(" -> ").append(String.join(", ", e.dependencies()));
            }
        }
        return sb.toString();
    }
}