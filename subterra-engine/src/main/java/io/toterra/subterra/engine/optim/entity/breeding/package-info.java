/**
 * 实体繁殖上限层：繁殖来源次数/产仔数量上限的核心策略。
 * Entity breeding-cap layer: the core policy capping how many attempts/offspring
 * a breeding source may produce.
 * <p>
 * 含婚恋形态繁殖守卫 {@link io.toterra.subterra.engine.optim.entity.breeding.BreedingCap}。
 * 纯 JDK，确定性计数器，不碰 MC。
 * <p>
 * Holds the love-mode breeding guard
 * {@link io.toterra.subterra.engine.optim.entity.breeding.BreedingCap}. Pure JDK,
 * deterministic counters, no MC coupling.
 */
package io.toterra.subterra.engine.optim.entity.breeding;