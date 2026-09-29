package com.thatcoffeelock.market.gui;

import java.util.List;

import com.thatcoffeelock.market.Gui;
import com.thatcoffeelock.market.MarketConfig;
import com.thatcoffeelock.market.MarketData;
import com.thatcoffeelock.market.MarketItems;
import com.thatcoffeelock.market.MarketMod;
import com.thatcoffeelock.market.Money;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

/** The front page: sell, buy, luxury, balance, banknotes and the rich list. */
public final class HubMenu extends MarketMenu {
	public HubMenu(int syncId, ServerPlayer viewer, @Nullable BlockPos origin) {
		super(syncId, viewer, origin);
		render();
	}

	private void render() {
		clearButtons();
		String currency = MarketConfig.get().currencyName;

		button(20, Gui.icon(Items.HOPPER, Gui.text("Sell Items", ChatFormatting.GREEN, ChatFormatting.BOLD),
			Gui.text("Drop your loot in, see the total,", ChatFormatting.GRAY),
			Gui.text("then hit confirm to cash out.", ChatFormatting.GRAY)), (b, t) -> {
			click();
			MarketMod.nextTick(() -> MarketGui.openSell(viewer, origin));
		});

		button(22, Gui.glow(Gui.icon(Items.SUNFLOWER, Gui.text("Your Balance", ChatFormatting.GOLD, ChatFormatting.BOLD),
			Money.text(MarketData.balance(viewer)).withStyle(style -> style.withItalic(false)),
			Component.empty(),
			Gui.text("Earn " + currency + " by selling to the market.", ChatFormatting.GRAY))), null);

		button(24, Gui.icon(Items.EMERALD, Gui.text("Buy Items", ChatFormatting.AQUA, ChatFormatting.BOLD),
			Gui.text("Everything has a price.", ChatFormatting.GRAY),
			Gui.text("Rarer items cost extra.", ChatFormatting.GRAY)), (b, t) -> {
			click();
			MarketMod.nextTick(() -> MarketGui.openBuy(viewer, origin, false));
		});

		button(30, Gui.glow(Gui.icon(Items.GOLD_BLOCK, Gui.text("Luxury & Vanity", ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD),
			Gui.text("Pallets of cash, gold bars and more.", ChatFormatting.GRAY),
			Gui.text("Completely useless. Absolutely essential.", ChatFormatting.DARK_GRAY))), (b, t) -> {
			click();
			MarketMod.nextTick(() -> MarketGui.openBuy(viewer, origin, true));
		});

		button(32, Gui.icon(Items.WRITABLE_BOOK, Gui.text("Rich List", ChatFormatting.YELLOW, ChatFormatting.BOLD),
			Gui.text("Who's winning capitalism?", ChatFormatting.GRAY),
			Gui.text("Click to print the top 10 in chat.", ChatFormatting.DARK_GRAY)), (b, t) -> {
			click();
			showRichList(viewer);
		});

		button(40, Gui.icon(Items.PAPER, Gui.text("Withdraw Banknote", ChatFormatting.GREEN, ChatFormatting.BOLD),
			List.of(Gui.text("Turn " + currency + " into paper you can trade.", ChatFormatting.GRAY),
				Component.empty(),
				Gui.text("Left-click: 100", ChatFormatting.YELLOW),
				Gui.text("Right-click: 1,000", ChatFormatting.YELLOW),
				Gui.text("Shift-click: 10,000", ChatFormatting.YELLOW))), (b, t) -> {
			long amount = Money.fromDecimal(t == ContainerInput.QUICK_MOVE ? 10_000 : b == 1 ? 1_000 : 100);
			if (MarketData.withdraw(viewer, amount)) {
				MarketItems.give(viewer, MarketItems.banknote(amount));
				kaching();
				render();
			} else {
				nope();
				viewer.sendSystemMessage(Component.literal("You can't afford that banknote.").withStyle(ChatFormatting.RED));
			}
		});

		button(49, Gui.icon(Items.BARRIER, Gui.text("Close", ChatFormatting.RED)), (b, t) -> MarketMod.nextTick(viewer::closeContainer));
		fillEmptyButtons();
	}

	public static void showRichList(ServerPlayer player) {
		player.sendSystemMessage(Component.literal("—— Rich List ——").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
		int rank = 1;
		for (MarketData.Account account : MarketData.richest(10)) {
			player.sendSystemMessage(Component.literal(rank++ + ". ").withStyle(ChatFormatting.YELLOW)
				.append(Component.literal(account.name + "  ").withStyle(ChatFormatting.WHITE))
				.append(Money.text(account.cents)));
		}
	}
}
