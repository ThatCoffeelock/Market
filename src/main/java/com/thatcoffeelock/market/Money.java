package com.thatcoffeelock.market;

import java.util.Locale;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/** All money is stored as whole cents in a long, so nothing gets lost to floating point. */
public final class Money {
	private Money() {
	}

	public static long fromDecimal(double amount) {
		return Math.round(amount * 100.0);
	}

	public static String format(long cents) {
		MarketConfig cfg = MarketConfig.get();
		return String.format(Locale.ROOT, "%,.2f", cents / 100.0) + " " + cfg.currencySymbol;
	}

	public static MutableComponent text(long cents) {
		return Component.literal(format(cents)).withStyle(ChatFormatting.GOLD);
	}
}
