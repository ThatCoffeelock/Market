package com.thatcoffeelock.skills.mixin;

import com.thatcoffeelock.skills.SkillsMod;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Every block a player places passes through here (right after it's set), so we can remember player-placed ores. */
@Mixin(BlockItem.class)
public abstract class BlockItemMixin {
	@Inject(
		method = "updateCustomBlockEntityTag(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/item/ItemStack;)Z",
		at = @At("HEAD")
	)
	private static void skills$rememberPlaced(Level level, @Nullable Player player, BlockPos pos, ItemStack stack,
											  CallbackInfoReturnable<Boolean> cir) {
		if (level instanceof ServerLevel server && player != null) {
			SkillsMod.placed(server, pos);
		}
	}
}
