// Ported from ServerCore (github.com/Wesley1808/ServerCore), MIT (c) Wesley1808.
package io.toterra.subterra.optim.server.dynamic;

import net.minecraft.world.entity.MobCategory;

/**
 * Marks {@link MobCategory} (via {@link MobCategoryMixin}) so the dynamic
 * feature can scale a category's spawn cap on the fly.
 */
public interface IMobCategory {
    void subterra$modifyCapacity(double modifier);

    static IMobCategory of(MobCategory category) {
        return (IMobCategory) (Object) category;
    }

    static void modifyCapacity(MobCategory category, double modifier) {
        IMobCategory.of(category).subterra$modifyCapacity(modifier);
    }
}