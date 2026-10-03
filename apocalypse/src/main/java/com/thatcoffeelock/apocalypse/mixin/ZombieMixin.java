package com.thatcoffeelock.apocalypse.mixin;

import com.thatcoffeelock.apocalypse.ApocalypseConfig;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.zombie.Zombie;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The sun doesn't burn the dead any more. 26.3 burns undead in one place for every mob (Mob.burnUndead),
 * so that's where zombies (and drowned and zombie villagers, which are zombies too) get skipped.
 */
@Mixin(Mob.class)
public abstract class ZombieMixin {
	@Inject(method = "burnUndead", at = @At("HEAD"), cancellable = true)
	private void apocalypse$sunproof(CallbackInfo ci) {
		if ((Object) this instanceof Zombie && ApocalypseConfig.get().sunproofZombies) {
			ci.cancel();
		}
	}
}
