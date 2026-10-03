package com.thatcoffeelock.skills.mixin;

import com.thatcoffeelock.skills.Brewing;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BrewingStandBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Brewing XP and the "ingredient isn't used up" chance. */
@Mixin(BrewingStandBlockEntity.class)
public abstract class BrewingStandMixin {
	@Inject(method = "doBrew", at = @At("HEAD"))
	private static void skills$beforeBrew(ServerLevel level, BlockPos pos, BrewingStandBlockEntity stand, CallbackInfo ci) {
		Brewing.beforeBrew(level, pos, stand);
	}

	@Inject(method = "doBrew", at = @At("TAIL"))
	private static void skills$afterBrew(ServerLevel level, BlockPos pos, BrewingStandBlockEntity stand, CallbackInfo ci) {
		Brewing.afterBrew(level, pos, stand);
	}
}
