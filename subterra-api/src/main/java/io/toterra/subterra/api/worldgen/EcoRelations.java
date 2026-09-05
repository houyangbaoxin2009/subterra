package io.toterra.subterra.api.worldgen;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Coexistence rule set for biosphere values — "default-disallow,
 * allow-exceptions" (white-list realism, architecture §5.2): a value pair from
 * two different dimensions is allowed only when explicitly declared.
 * <p>
 * Rules are undirected and stored resistance-free; {@link #allows} checks the
 * whole cross-dimension matrix of a profile. {@link #validate()} reports rule-
 * set health: every value must be able to coexist with at least one value of a
 * different dimension (no fully-forbidden value), and at least one globally
 * legal profile must exist (no unsatisfiable constraint cycle). Pure JDK.
 */
public final class EcoRelations {

    private final Set<String> allowedPairs = new HashSet<>();

    /** Declares that the two values (different dimensions) may coexist. */
    public void allow(EcoDimValue a, EcoDimValue b) {
        if (!a.dim().equals(b.dim())) {
            allowedPairs.add(pairKey(a, b));
        }
    }

    /** Convenience bulk allow for two dimension value sets. */
    public void allowAll(EcoDimValue a, List<EcoDimValue> bs) {
        for (EcoDimValue b : bs) {
            allow(a, b);
        }
    }

    /** True when two values may coexist (same dimension is always allowed). */
    public boolean isAllowed(EcoDimValue a, EcoDimValue b) {
        if (a.dim().equals(b.dim())) {
            return true;
        }
        return allowedPairs.contains(pairKey(a, b));
    }

    /** True when every cross-dimension pair of the profile is allowed. */
    public boolean allows(EcoProfile profile) {
        List<EcoDimValue> vs = profile.values();
        for (int i = 0; i < vs.size(); i++) {
            for (int j = i + 1; j < vs.size(); j++) {
                if (!isAllowed(vs.get(i), vs.get(j))) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * Rule-set health report; empty means healthy. Checks (a) no value is
     * fully forbidden (has zero allow edges to any other dimension) and
     * (b) at least one globally legal profile can be greedily constructed.
     */
    public List<String> validate(List<EcoDimValue> allValues) {
        List<String> issues = new ArrayList<>();
        Set<EcoDimValue> withEdge = new HashSet<>();
        for (String key : allowedPairs) {
            splitPairKey(key, withEdge);
        }
        for (EcoDimValue v : allValues) {
            boolean shareOtherDim = false;
            for (EcoDimValue u : allValues) {
                if (!u.dim().equals(v.dim()) && isAllowed(v, u)) {
                    shareOtherDim = true;
                    break;
                }
            }
            if (!shareOtherDim) {
                issues.add("fully forbidden value: " + v);
            }
        }
        if (greedyLegalProfile(allValues) == null) {
            issues.add("no globally legal profile exists (constraint cycle)");
        }
        return issues;
    }

    /** Greedily picks a legal profile from the value pool (high-to-low first). */
    public EcoProfile greedyLegalProfile(List<EcoDimValue> allValues) {
        List<EcoDimValue> chosen = new ArrayList<>();
        for (EcoDim dim : EcoDim.ALL) {
            EcoDimValue pick = null;
            for (EcoDimValue v : allValues) {
                if (!v.dim().equals(dim)) {
                    continue;
                }
                boolean ok = true;
                for (EcoDimValue u : chosen) {
                    if (!isAllowed(v, u)) {
                        ok = false;
                        break;
                    }
                }
                if (ok) {
                    pick = v;
                    break;
                }
            }
            if (pick == null) {
                return null;
            }
            chosen.add(pick);
        }
        EcoProfile.Builder b = EcoProfile.builder();
        for (EcoDimValue v : chosen) {
            b.set(v);
        }
        return b.build();
    }

    private static String pairKey(EcoDimValue a, EcoDimValue b) {
        // canonical order by dimension id then name for symmetry
        int cmp = a.dim().id().compareTo(b.dim().id());
        if (cmp == 0) {
            cmp = a.name().compareTo(b.name());
        }
        EcoDimValue lo = cmp <= 0 ? a : b;
        EcoDimValue hi = cmp <= 0 ? b : a;
        return lo + "|" + hi;
    }

    private static void splitPairKey(String key, Set<EcoDimValue> out) {
        int idx = key.indexOf('|');
        out.add(parseValue(key.substring(0, idx)));
        out.add(parseValue(key.substring(idx + 1)));
    }

    private static EcoDimValue parseValue(String s) {
        int eq = s.indexOf('=');
        return new EcoDimValue(EcoDim.of(s.substring(0, eq)), s.substring(eq + 1));
    }
}