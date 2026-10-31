package com.example.darkzombies;

import net.minecraft.world.entity.monster.Drowned;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.monster.ZombifiedPiglin;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public class ZombieEvents {

    @SubscribeEvent
    public void onEntityJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) {
            return;
        }
        if (!(event.getEntity() instanceof Zombie zombie)) {
            return;
        }
        // Zombie, Husk and Zombie Villager only. Drowned and Zombified Piglin have their own habitats.
        if (zombie instanceof Drowned || zombie instanceof ZombifiedPiglin) {
            return;
        }
        boolean alreadyAdded = zombie.goalSelector.getAvailableGoals().stream()
                .anyMatch(wrapped -> wrapped.getGoal() instanceof HideInDarkGoal);
        if (!alreadyAdded) {
            // Priority 1: beats the vanilla attack goal (2) and wandering goals.
            zombie.goalSelector.addGoal(1, new HideInDarkGoal(zombie, 1.1D));
        }
    }
}
