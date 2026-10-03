package com.thatcoffeelock.skills.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import com.thatcoffeelock.skills.Fighting;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Skill damage bonuses and reductions, applied before armor and the rest of vanilla's damage maths. */
@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {
	@ModifyVariable(method = "hurtServer", at = @At("HEAD"), argsOnly = true)
	private float skills$adjustDamage(float amount, @Local(argsOnly = true) DamageSource source) {
		return Fighting.modify((LivingEntity) (Object) this, source, amount);
	}
}
