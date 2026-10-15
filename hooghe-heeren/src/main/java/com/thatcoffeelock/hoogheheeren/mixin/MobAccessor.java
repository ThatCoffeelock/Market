package com.thatcoffeelock.hoogheheeren.mixin;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** A soldier's brain is a vanilla mob with all of its own ideas taken out: we clear its goals and steer it ourselves. */
@Mixin(Mob.class)
public interface MobAccessor {
	@Accessor("goalSelector")
	GoalSelector hoogheheeren$goals();

	@Accessor("targetSelector")
	GoalSelector hoogheheeren$targets();
}
