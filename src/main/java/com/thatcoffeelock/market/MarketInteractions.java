package com.thatcoffeelock.market;

import com.thatcoffeelock.market.gui.MarketGui;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/** Right-clicking Market blocks, vanity decorations and banknotes; breaking Market blocks. */
public final class MarketInteractions {
	private MarketInteractions() {
	}

	public static void register() {
		UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
			if (!(world instanceof ServerLevel level) || !(player instanceof ServerPlayer sp) || player.isSpectator()) {
				return InteractionResult.PASS;
			}
			BlockPos pos = hit.getBlockPos();

			MarketData.VanityRecord vanity = MarketData.vanityAt(level, pos);
			if (vanity != null) {
				if (hand == InteractionHand.MAIN_HAND) {
					Vanity.interact(sp, level, pos, vanity);
				}
				return InteractionResult.SUCCESS;
			}

			if (MarketData.isMarket(level, pos)) {
				if (!level.getBlockState(pos).is(Blocks.LECTERN)) {
					MarketData.removeMarket(level, pos); // stale entry (e.g. blown up)
					return InteractionResult.PASS;
				}
				if (hand == InteractionHand.MAIN_HAND) {
					MarketGui.openHub(sp, pos);
				}
				return InteractionResult.SUCCESS;
			}

			ItemStack held = player.getItemInHand(hand);
			Vanity.Type type = MarketItems.vanityType(held);
			if (type != null) {
				Vanity.tryPlace(sp, level, hit, hand, held, type);
				return InteractionResult.SUCCESS;
			}
			return InteractionResult.PASS;
		});

		UseItemCallback.EVENT.register((player, world, hand) -> {
			if (!(player instanceof ServerPlayer sp)) {
				return InteractionResult.PASS;
			}
			ItemStack stack = player.getItemInHand(hand);
			long value = MarketItems.banknoteValue(stack);
			if (value <= 0) {
				return InteractionResult.PASS;
			}
			int count = player.isShiftKeyDown() ? stack.getCount() : 1;
			stack.shrink(count);
			MarketData.deposit(sp, value * count);
			sp.sendSystemMessage(Component.literal("Deposited ").withStyle(ChatFormatting.GREEN)
				.append(Money.text(value * count))
				.append(Component.literal(". Balance: ").withStyle(ChatFormatting.GREEN))
				.append(Money.text(MarketData.balance(sp))));
			return InteractionResult.SUCCESS;
		});

		PlayerBlockBreakEvents.BEFORE.register((world, player, pos, state, blockEntity) -> {
			if (!(world instanceof ServerLevel level)) {
				return true;
			}
			if (MarketData.isMarket(level, pos)) {
				MarketData.removeMarket(level, pos);
				if (state.is(Blocks.LECTERN)) {
					level.removeBlock(pos, false);
					if (!player.isCreative()) {
						Block.popResource(level, pos, MarketItems.marketBlock());
					}
					return false;
				}
				return true;
			}
			MarketData.VanityRecord vanity = MarketData.vanityAt(level, pos);
			if (vanity != null) {
				Vanity.remove(level, pos, vanity);
				Vanity.Type type = Vanity.Type.byId(vanity.type);
				if (type != null && !player.isCreative()) {
					Block.popResource(level, pos, MarketItems.vanity(type));
				}
				return false;
			}
			return true;
		});
	}
}
