package io.toterra.subterra.engine.network.strategy;

import java.util.Objects;

/**
 * 不可变载荷策略规格（p.2.4.3）：一组参数化策略，决定某类载荷在增强通道上以哪种层级、
 * 哪个兴趣域、是否差分、是否加密、以及名义频率序下发。L3 策略以 {@code encrypted=true} 产出，
 * L1 策略以 {@code differential=true} 产出；本类本身只存放这些标志，由 {@link StrategySelector}
 * 按描述符填充。纯 JDK，确定性等值。
 * <p>
 * Immutable payload-strategy spec (p.2.4.3): a parameterised strategy governing on which level, in
 * which interest domain, differentially/encrypted, and at what nominal frequency class a payload is
 * delivered. L3 strategies carry {@code encrypted=true}, L1 strategies carry {@code differential=true};
 * this class merely holds those flags, populated by {@link StrategySelector}. Pure JDK, deterministic
 * equality.
 */
public final class PayloadStrategy {

    private final PayloadLevel level;
    private final String interestDomain;
    private final boolean differential;
    private final boolean encrypted;
    private final int rateRank;

    private PayloadStrategy(Builder b) {
        this.level = b.level;
        this.interestDomain = b.interestDomain;
        this.differential = b.differential;
        this.encrypted = b.encrypted;
        this.rateRank = b.rateRank;
    }

    /** 新建策略 builder（默认 rateRank = level 的 {@link PayloadLevel#rank()}）。New strategy builder. */
    public static Builder builder(PayloadLevel level) {
        Objects.requireNonNull(level, "payload level must not be null");
        return new Builder(level);
    }

    /** 层级。Payload level. */
    public PayloadLevel level() {
        return level;
    }

    /** 兴趣域（订阅过滤依据）。Interest domain (subscription filter key). */
    public String interestDomain() {
        return interestDomain;
    }

    /** 是否启用差分。Whether differential sync is enabled. */
    public boolean differential() {
        return differential;
    }

    /** 是否加密（L3）。Whether encrypted (L3). */
    public boolean encrypted() {
        return encrypted;
    }

    /** 名义频率序（越大越低频）。Nominal rate rank (larger = less frequent). */
    public int rateRank() {
        return rateRank;
    }

    /** 是否与某策略逐字段相同。Whether field-for-field equal to another strategy. */
    public boolean equivalentTo(PayloadStrategy other) {
        return this.equals(other);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof PayloadStrategy other)) {
            return false;
        }
        return level == other.level
                && differential == other.differential
                && encrypted == other.encrypted
                && rateRank == other.rateRank
                && interestDomain.equals(other.interestDomain);
    }

    @Override
    public int hashCode() {
        return Objects.hash(level, interestDomain, differential, encrypted, rateRank);
    }

    @Override
    public String toString() {
        return "PayloadStrategy{level=" + level
                + ", domain='" + interestDomain + '\''
                + ", differential=" + differential
                + ", encrypted=" + encrypted
                + ", rateRank=" + rateRank + '}';
    }

    /** 策略 builder。Strategy builder. */
    public static final class Builder {
        private final PayloadLevel level;
        private String interestDomain = "";
        private boolean differential;
        private boolean encrypted;
        private int rateRank;

        Builder(PayloadLevel level) {
            this.level = level;
            this.rateRank = level.rank();
        }

        /** 兴趣域。Set the interest domain. */
        public Builder interestDomain(String interestDomain) {
            this.interestDomain = Objects.requireNonNull(interestDomain, "interest domain must not be null");
            return this;
        }

        /** 差分开关。Set the differential flag. */
        public Builder differential(boolean differential) {
            this.differential = differential;
            return this;
        }

        /** 加密开关。Set the encrypted flag. */
        public Builder encrypted(boolean encrypted) {
            this.encrypted = encrypted;
            return this;
        }

        /** 名义频率序（覆盖默认）。Override the nominal rate rank. */
        public Builder rateRank(int rateRank) {
            this.rateRank = rateRank;
            return this;
        }

        /** 构建。Build. */
        public PayloadStrategy build() {
            return new PayloadStrategy(this);
        }
    }
}