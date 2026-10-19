package com.thatcoffeelock.overenchant.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.thatcoffeelock.overenchant.Lure;
import net.minecraft.world.entity.projectile.FishingHook;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Lure VI and up would push the wait for a bite to 0 or less on every roll, and vanilla just rolls again, forever. The
 * rolled wait is already in timeUntilLured when lureSpeed is read to take off it, so the speed is trimmed to leave a
 * second (see {@link Lure}).
 */
@Mixin(FishingHook.class)
public abstract class FishingHookMixin {
	@Shadow
	private int timeUntilLured;

	@ModifyExpressionValue(method = "catchingFish", at = @At(value = "FIELD", target = "Lnet/minecraft/world/entity/projectile/FishingHook;lureSpeed:I"))
	private int overenchant$lureSpeed(int lureSpeed) {
		return Lure.speed(lureSpeed, timeUntilLured);
	}
}
