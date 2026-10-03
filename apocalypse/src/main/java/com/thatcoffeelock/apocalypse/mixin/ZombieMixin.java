package com.thatcoffeelock.apocalypse.mixin;

import com.thatcoffeelock.apocalypse.ApocalypseConfig;
import net.minecraft.world.entity.monster.zombie.Zombie;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The sun doesn't burn the dead any more. Covers zombies, drowned and zombie villagers (husks never burned). */
@Mixin(Zombie.class)
public abstract class ZombieMixin {
	@Inject(method = "isSunSensitive", at = @At("HEAD"), cancellable = true)
	private void apocalypse$sunproof(CallbackInfoReturnable<Boolean> cir) {
		if (ApocalypseConfig.get().sunproofZombies) {
			cir.setReturnValue(false);
		}
	}
}
