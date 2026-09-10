/**
 * 实体合并层：物品/经验实体就近合并的策略核心。
 * Entity merging layer: the core policy merging nearby same-identity pickups.
 * <p>
 * 含合并判据 {@link io.toterra.subterra.engine.optim.entity.merging.MergePolicy}
 * 与执行器 {@link io.toterra.subterra.engine.optim.entity.merging.Merger}。纯 JDK，
 * 纯数据、构造期校验失败即快速失败，不碰 MC。
 * <p>
 * Holds the merge criterion {@link io.toterra.subterra.engine.optim.entity.merging.MergePolicy}
 * and {@link io.toterra.subterra.engine.optim.entity.merging.Merger}. Pure JDK, pure
 * data with fail-fast construction validation, no MC coupling.
 */
package io.toterra.subterra.engine.optim.entity.merging;