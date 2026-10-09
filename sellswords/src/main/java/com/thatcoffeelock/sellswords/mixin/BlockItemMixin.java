package com.thatcoffeelock.sellswords.mixin;

import com.thatcoffeelock.sellswords.Stations;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Remembers where Mercenary Stations get placed (the tag is on the item, and doesn't survive placement). */
@Mixin(BlockItem.class)
public abstract class BlockItemMixin {
	@Inject(
		method = "updateCustomBlockEntityTag(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/item/ItemStack;)Z",
		at = @At("HEAD")
	)
	private static void sellswords$rememberPlaced(Level level, @Nullable Player player, BlockPos pos, ItemStack stack,
												  CallbackInfoReturnable<Boolean> cir) {
		Stations.onPlaced(level, pos, stack, player);
	}
}
