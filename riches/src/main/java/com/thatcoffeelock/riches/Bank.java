package com.thatcoffeelock.riches;

import java.util.UUID;

import com.thatcoffeelock.market.MarketData;
import com.thatcoffeelock.market.Money;

/** The only place Riches talks to the Market mod: balances, payouts and formatting. */
final class Bank {
	private Bank() {
	}

	/** A player's balance in cents (0 if they've never had an account). */
	static long balance(UUID player) {
		return MarketData.balance(player);
	}

	static void credit(UUID player, String name, long cents) {
		MarketData.deposit(player, name, cents);
	}

	static long cents(double marks) {
		return Money.fromDecimal(marks);
	}

	static String format(long cents) {
		return Money.format(cents);
	}
}
