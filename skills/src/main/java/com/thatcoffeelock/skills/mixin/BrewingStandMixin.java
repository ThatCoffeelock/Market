package com.thatcoffeelock.skills.mixin;

import com.thatcoffeelock.skills.Brewing;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BrewingStandBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Brewing XP and the "ingredient isn't used up" chance. */
@Mixin(BrewingStandBlockEntity.class)
public abstract class BrewingStandMixin {
	@Inject(method = "doBrew", at = @At("HEAD"))
	private static void skills$beforeBrew(Level level, BlockPos pos, NonNullList<ItemStack> items, CallbackInfo ci) {
		Brewing.beforeBrew(level, pos, items);
	}

	@Inject(method = "doBrew", at = @At("TAIL"))
	private static void skills$afterBrew(Level level, BlockPos pos, NonNullList<ItemStack> items, CallbackInfo ci) {
		Brewing.afterBrew(level, pos, items);
	}
}
