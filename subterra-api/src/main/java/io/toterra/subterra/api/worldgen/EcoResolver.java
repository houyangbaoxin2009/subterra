package io.toterra.subterra.api.worldgen;

import java.util.List;

/**
 * Conflict resolver: given a requested profile, finds the nearest legal profile
 * by relaxing dimensions lowest-priority-first (architecture §5.2). One
 * dimension is changed per step, the lowest-priority dimension that has any
 * legal replacement wins, and the result is always deterministic. A request
 * that is already legal is returned unchanged; on a rule set that is internally
 * legal a solution always exists (the vanilla defaults prove satisfiability).
 */
public final class EcoResolver {

    private EcoResolver() {
    }

    /**
     * Resolves a request to the nearest legal profile.
     *
     * @param request   the requested (possibly conflicting) profile
     * @param relations the coexistence rule set
     * @param fromLow   dimensions in relaxation order, lowest priority first
     *                  ({@link DimPriority#FROM_LOW})
     * @param pool      the full value pool to pick replacements from
     */
    public static EcoProfile resolve(EcoProfile request, EcoRelations relations,
                                     List<EcoDim> fromLow, List<EcoDimValue> pool) {
        if (relations.allows(request)) {
            return request;
        }
        // Level 1: relax a single dimension, lowest priority first. Terrain is
        // last in `fromLow`, so a terrain concession is the last single resort.
        for (EcoDim dim : fromLow) {
            EcoDimValue original = request.get(dim);
            for (EcoDimValue candidate : pool) {
                if (!candidate.dim().equals(dim) || candidate.equals(original)) {
                    continue;
                }
                EcoProfile swapped = swap(request, dim, candidate);
                if (relations.allows(swapped)) {
                    return swapped;
                }
            }
        }
        // Level 2: relax two dimensions at once (keeps higher-priority dims
        // intact when a single concession is impossible — e.g. river + stone
        // must both change on a hunted mountain-desert request).
        for (int i = 0; i < fromLow.size() - 1; i++) {
            EcoDim di = fromLow.get(i);
            EcoDimValue oi = request.get(di);
            for (int j = i + 1; j < fromLow.size(); j++) {
                EcoDim dj = fromLow.get(j);
                EcoDimValue oj = request.get(dj);
                for (EcoDimValue vi : pool) {
                    if (!vi.dim().equals(di) || vi.equals(oi)) {
                        continue;
                    }
                    for (EcoDimValue vj : pool) {
                        if (!vj.dim().equals(dj) || vj.equals(oj)) {
                            continue;
                        }
                        EcoProfile swapped = swap(request, di, vi);
                        swapped = swap(swapped, dj, vj);
                        if (relations.allows(swapped)) {
                            return swapped;
                        }
                    }
                }
            }
        }
        // Unreachable on a legal rule set (defaults prove satisfiability);
        // return the request untouched rather than fabricating a far-away profile.
        return request;
    }

    private static EcoProfile swap(EcoProfile request, EcoDim dim, EcoDimValue replacement) {
        EcoProfile.Builder b = EcoProfile.builder();
        for (EcoDimValue v : request.values()) {
            b.set(v.dim().equals(dim) ? replacement : v);
        }
        return b.build();
    }
}