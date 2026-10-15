package com.thatcoffeelock.hoogheheeren;

import java.util.UUID;

import com.thatcoffeelock.market.MarketData;
import com.thatcoffeelock.market.Money;
import com.thatcoffeelock.market.PriceBook;
import net.minecraft.world.item.ItemStack;

/** The only place De Hooghe Heeren talks to the Market mod: your balance is your treasury. Money is in cents. */
final class Bank {
	private Bank() {
	}

	static long cents(double marks) {
		return Money.fromDecimal(marks);
	}

	static String format(long cents) {
		return Money.format(cents);
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

	/** Takes up to {@code cents}, as much as there is. Returns what was taken. */
	static long seize(UUID owner, long cents) {
		long take = Math.max(0, Math.min(cents, balance(owner)));
		return take > 0 && charge(owner, take) ? take : 0;
	}

	/** What the Market pays for this stack, or -1 if it won't buy it. */
	static long sellValue(ItemStack stack) {
		return PriceBook.stackSellValue(stack);
	}
}
