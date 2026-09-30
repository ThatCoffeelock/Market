package com.thatcoffeelock.colonycraft;

import java.util.UUID;

import com.thatcoffeelock.market.MarketData;
import com.thatcoffeelock.market.Money;
import com.thatcoffeelock.market.PriceBook;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/** The only place Colonycraft talks to the Market mod: balances, payments and sell prices. */
final class Bank {
	private Bank() {
	}

	static long cents(double marks) {
		return Money.fromDecimal(marks);
	}

	static String format(long cents) {
		return Money.format(cents);
	}

	static MutableComponent text(long cents) {
		return Money.text(cents);
	}

	static long balance(ServerPlayer player) {
		return MarketData.balance(player);
	}

	static long balance(UUID player) {
		return MarketData.balance(player);
	}

	/** Player pays at a counter. False (and nothing taken) if they can't afford it. */
	static boolean pay(ServerPlayer player, long cents) {
		return MarketData.withdraw(player, cents);
	}

	/** Wages and other bills, taken whether the owner is online or not. */
	static boolean charge(UUID owner, long cents) {
		return MarketData.withdraw(owner, cents);
	}

	static void credit(UUID owner, String name, long cents) {
		MarketData.deposit(owner, name, cents);
	}

	/** What the Market pays for this stack, or -1 if it won't buy it. */
	static long sellValue(ItemStack stack) {
		return PriceBook.stackSellValue(stack);
	}
}
