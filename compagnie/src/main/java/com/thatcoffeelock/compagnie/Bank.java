package com.thatcoffeelock.compagnie;

import java.util.UUID;

import com.thatcoffeelock.market.MarketData;
import com.thatcoffeelock.market.Money;
import com.thatcoffeelock.market.PriceBook;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.ItemStack;

/** The only place De Compagnie talks to the Market mod: balances, payments and sell prices. Money is in cents. */
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

	static long balance(UUID owner) {
		return MarketData.balance(owner);
	}

	/** Takes money whether the owner is online or not. False (and nothing taken) if they can't afford it. */
	static boolean charge(UUID owner, long cents) {
		return cents <= 0 || MarketData.withdraw(owner, cents);
	}

	static void credit(UUID owner, String name, long cents) {
		if (cents > 0) {
			MarketData.deposit(owner, name, cents);
		}
	}

	/** What the Market pays for this stack, or -1 if it won't buy it. */
	static long sellValue(ItemStack stack) {
		return PriceBook.stackSellValue(stack);
	}
}
