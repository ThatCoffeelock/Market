package com.thatcoffeelock.sellswords.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import com.thatcoffeelock.sellswords.Combat;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Damage a mercenary takes: a Bulwark's shield and the owner's Shield Wall perk soak some of it, before armor. */
@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {
	@ModifyVariable(method = "hurtServer", at = @At("HEAD"), argsOnly = true)
	private float sellswords$adjustDamage(float amount, @Local(argsOnly = true) DamageSource source) {
		return Combat.incoming((LivingEntity) (Object) this, source, amount);
	}
}
