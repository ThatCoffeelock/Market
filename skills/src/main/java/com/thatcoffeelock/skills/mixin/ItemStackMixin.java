package com.thatcoffeelock.skills.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import com.thatcoffeelock.skills.Smithing;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Blacksmithing: a chance that a player's gear doesn't lose durability. Stacks with Unbreaking. */
@Mixin(ItemStack.class)
public abstract class ItemStackMixin {
	@ModifyVariable(
		method = "hurtAndBreak(ILnet/minecraft/server/level/ServerLevel;Lnet/minecraft/server/level/ServerPlayer;Ljava/util/function/Consumer;)V",
		at = @At("HEAD"),
		argsOnly = true
	)
	private int skills$tempered(int amount, @Local(argsOnly = true) ServerPlayer player) {
		return Smithing.wear(player, amount);
	}
}
