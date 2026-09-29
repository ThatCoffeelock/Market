package com.thatcoffeelock.market.gui;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import org.jetbrains.annotations.Nullable;

public final class MarketGui {
	private MarketGui() {
	}

	private static Component title(String text) {
		return Component.literal(text).withStyle(ChatFormatting.DARK_GREEN);
	}

	public static void openHub(ServerPlayer player, @Nullable BlockPos origin) {
		player.openMenu(new SimpleMenuProvider((id, inv, p) -> new HubMenu(id, player, origin), title("✦ Market ✦")));
	}

	public static void openSell(ServerPlayer player, @Nullable BlockPos origin) {
		player.openMenu(new SimpleMenuProvider((id, inv, p) -> new SellMenu(id, player, origin), title("Market » Sell")));
	}

	public static void openBuy(ServerPlayer player, @Nullable BlockPos origin, boolean luxury) {
		player.openMenu(new SimpleMenuProvider((id, inv, p) -> new BuyMenu(id, player, origin, luxury),
			title(luxury ? "Market » Luxury" : "Market » Buy")));
	}
}
