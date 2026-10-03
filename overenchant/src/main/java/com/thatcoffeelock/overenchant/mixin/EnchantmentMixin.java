package com.thatcoffeelock.overenchant.mixin;

import com.thatcoffeelock.overenchant.OverenchantConfig;
import com.thatcoffeelock.overenchant.OverenchantCosts;
import net.minecraft.world.item.enchantment.Enchantment;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Every place that asks how high an enchantment may go (the anvil, the enchanting table, /enchant, villager trades)
 * goes through getMaxLevel, so raising it here raises it everywhere. The enchantment's own data is left alone, so the
 * cost curves and effects carry on past the old maximum. The enchanting table finds what it can offer by asking for the
 * cost range of each level, so those get squeezed too (see {@link OverenchantCosts}).
 */
@Mixin(Enchantment.class)
public abstract class EnchantmentMixin {
	@Inject(method = "getMaxLevel", at = @At("RETURN"), cancellable = true)
	private void overenchant$raise(CallbackInfoReturnable<Integer> cir) {
		cir.setReturnValue(OverenchantConfig.get().raise(cir.getReturnValue()));
	}

	@Inject(method = "getMinCost", at = @At("RETURN"), cancellable = true)
	private void overenchant$minCost(int level, CallbackInfoReturnable<Integer> cir) {
		cir.setReturnValue(OverenchantCosts.cost((Enchantment) (Object) this, level, false, cir.getReturnValue()));
	}

	@Inject(method = "getMaxCost", at = @At("RETURN"), cancellable = true)
	private void overenchant$maxCost(int level, CallbackInfoReturnable<Integer> cir) {
		cir.setReturnValue(OverenchantCosts.cost((Enchantment) (Object) this, level, true, cir.getReturnValue()));
	}
}
