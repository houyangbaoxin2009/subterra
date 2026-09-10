/**
 * 物品/方块黑名单控制：以物品与方块命中的黑名单规则核心。
 * Item/block control: the blacklist-rule core matched against items and blocks.
 * <p>
 * 含规则 {@link io.toterra.subterra.engine.optim.server.item_control.ItemControlRule} 与去重集合
 * {@link io.toterra.subterra.engine.optim.server.item_control.ItemControlRuleSet}（线性单遍命中，
 * 禁 O(n²)）。纯 JDK，不碰 MC。
 * <p>
 * Holds the rule {@link io.toterra.subterra.engine.optim.server.item_control.ItemControlRule} and the
 * deduplicated set {@link io.toterra.subterra.engine.optim.server.item_control.ItemControlRuleSet}
 * (single linear pass, no O(n²)). Pure JDK, no MC coupling.
 */
package io.toterra.subterra.engine.optim.server.item_control;