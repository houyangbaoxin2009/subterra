package io.toterra.subterra.api.worldgen;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A complete nine-dimension profile: exactly one value per registered
 * {@link EcoDim}. Immutable once built; the builder enforces totality so a
 * profile can never carry a half-specified biome. Pure and deterministic.
 */
public final class EcoProfile {

    private final Map<EcoDim, EcoDimValue> values;

    private EcoProfile(Map<EcoDim, EcoDimValue> values) {
        this.values = Map.copyOf(values);
    }

    /** Value for a dimension; null when the dimension is not registered. */
    public EcoDimValue get(EcoDim dim) {
        return values.get(dim);
    }

    /** All dimension values in dimension order. */
    public List<EcoDimValue> values() {
        return List.copyOf(values.values());
    }

    /** True when every dimension value pair (different dims) is allowed. */
    public boolean allows(EcoRelations relations) {
        return relations.allows(this);
    }

    /** Builder enforcing that all registered dimensions are provided. */
    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private final Map<EcoDim, EcoDimValue> set = new LinkedHashMap<>();

        public Builder set(EcoDimValue value) {
            set.put(value.dim(), value);
            return this;
        }

        public EcoProfile build() {
            List<String> missing = new ArrayList<>();
            for (EcoDim d : EcoDim.ALL) {
                if (!set.containsKey(d)) {
                    missing.add(d.id());
                }
            }
            if (!missing.isEmpty()) {
                throw new IllegalArgumentException("incomplete profile, missing: " + missing);
            }
            return new EcoProfile(set);
        }
    }

    @Override
    public String toString() {
        return "EcoProfile" + values;
    }
}