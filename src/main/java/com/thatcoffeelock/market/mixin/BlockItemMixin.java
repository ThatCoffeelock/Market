package com.thatcoffeelock.market.mixin;

import com.thatcoffeelock.market.MarketData;
import com.thatcoffeelock.market.MarketItems;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Remembers where a Market block gets placed (it's a lectern with a tag, the tag doesn't survive placement). */
@Mixin(BlockItem.class)
public abstract class BlockItemMixin {
	@Inject(
		method = "updateCustomBlockEntityTag(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/level/block/state/BlockState;)Z",
		at = @At("HEAD")
	)
	private void market$rememberPlacedMarket(BlockPos pos, Level level, @Nullable Player player, ItemStack stack, BlockState state,
											 CallbackInfoReturnable<Boolean> cir) {
		if (level instanceof ServerLevel && MarketItems.isMarketBlock(stack)) {
			MarketData.addMarket(level, pos);
		}
	}
}
