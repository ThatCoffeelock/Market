package com.thatcoffeelock.compagnie;

import java.util.ArrayList;
import java.util.List;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

/**
 * /compagnie: help. /compagnie news: the latest. /compagnie telegrams and /compagnie answer N 1-3: deal with telegrams
 * from anywhere. Ops: /compagnie give desk, /compagnie dawn (run a day now).
 */
final class CompagnieCommands {
	private CompagnieCommands() {
	}

	static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext context, Commands.CommandSelection selection) {
		dispatcher.register(literal("compagnie")
			.executes(ctx -> {
				help(ctx.getSource());
				return 1;
			})
			.then(literal("news").executes(ctx -> news(ctx.getSource())))
			.then(literal("telegrams").executes(ctx -> telegrams(ctx.getSource())))
			.then(literal("answer")
				.then(argument("telegram", IntegerArgumentType.integer(1, 20))
					.then(argument("answer", IntegerArgumentType.integer(1, 3))
						.executes(ctx -> answer(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "telegram"),
							IntegerArgumentType.getInteger(ctx, "answer"))))))
			.then(literal("give").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
				.then(literal("desk").executes(ctx -> {
					Desks.give(ctx.getSource().getPlayerOrException(), Desks.item());
					ctx.getSource().sendSuccess(() -> Component.literal("Here's a Company Desk. Sign here, and here, and here.").withStyle(ChatFormatting.GOLD), false);
					return 1;
				})))
			.then(literal("dawn").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
				.executes(ctx -> {
					Companies.lastDay++;
					Companies.dawn(Companies.lastDay);
					ctx.getSource().sendSuccess(() -> Component.literal("A new day dawns on every company (day " + Companies.lastDay + ").")
						.withStyle(ChatFormatting.GOLD), true);
					return 1;
				})));
	}

	private static Company company(CommandSourceStack source) throws CommandSyntaxException {
		ServerPlayer player = source.getPlayerOrException();
		return Companies.of(player.getUUID(), player.getName().getString());
	}

	private static int news(CommandSourceStack source) throws CommandSyntaxException {
		Company c = company(source);
		c.unread = 0;
		source.sendSystemMessage(Component.literal("—— " + c.ownerName + "'s " + c.rank().title + " ——").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
		for (int i = Math.max(0, c.news.size() - 10); i < c.news.size(); i++) {
			source.sendSystemMessage(Component.literal(c.news.get(i)).withStyle(ChatFormatting.YELLOW));
		}
		return 1;
	}

	private static List<Job> pending(Company c) {
		List<Job> out = new ArrayList<>();
		for (Job j : c.active) {
			if (j.pendingTelegram()) {
				out.add(j);
			}
		}
		return out;
	}

	private static int telegrams(CommandSourceStack source) throws CommandSyntaxException {
		Company c = company(source);
		List<Job> jobs = pending(c);
		if (jobs.isEmpty()) {
			source.sendSystemMessage(Component.literal("No telegrams waiting.").withStyle(ChatFormatting.GRAY));
			return 1;
		}
		for (int i = 0; i < jobs.size(); i++) {
			Job job = jobs.get(i);
			Telegram t = Telegram.byName(job.telegram);
			JobType type = job.type();
			if (t == null || type == null) {
				continue;
			}
			source.sendSystemMessage(Component.literal("📨 " + (i + 1) + ". " + type.title + ": " + t.text(job)).withStyle(ChatFormatting.GOLD));
			for (int a = 0; a < 3; a++) {
				source.sendSystemMessage(Component.literal("   /compagnie answer " + (i + 1) + " " + (a + 1) + "  →  " + t.answer(a, job))
					.withStyle(ChatFormatting.YELLOW));
			}
		}
		return 1;
	}

	private static int answer(CommandSourceStack source, int which, int choice) throws CommandSyntaxException {
		Company c = company(source);
		List<Job> jobs = pending(c);
		if (which > jobs.size()) {
			source.sendFailure(Component.literal("There's no telegram number " + which + ". Try /compagnie telegrams."));
			return 0;
		}
		Job job = jobs.get(which - 1);
		Telegram t = Telegram.byName(job.telegram);
		String why = Jobs.answer(c, job, choice - 1);
		if (why != null) {
			source.sendFailure(Component.literal(why));
			return 0;
		}
		String sent = t == null ? "" : t.answer(choice - 1, job);
		source.sendSuccess(() -> Component.literal("Telegram sent: \"" + sent + "\"").withStyle(ChatFormatting.GOLD), false);
		return 1;
	}

	private static void help(CommandSourceStack source) {
		String[] lines = {
			"§6§l—— De Compagnie ——",
			"§e1. The desk§7: §fCartography Table + Gold Ingot + Book§7 = §6Company Desk§7. Place it, right-click it.",
			"§e2. Agents§7: hire them at the §fHiring Board§7. Force, Coin and Guile, a trait or two, a daily wage.",
			"§e3. Jobs§7: pick one on the §fJob Board§7: trade, raid, explore or something shady. Choose the advisor",
			"§7   (§fDe Kapitein§7: force, §fDe Koopman§7: coin, §fDe Spion§7: guile), a stake and an agent. Send them.",
			"§e4. Dawn§7: every Minecraft day, jobs come home. Marks to your balance, spoils to the §fStrongbox§7.",
			"§7   Triumph, success, partial, failure or disaster. Agents get wounded, captured or worse.",
			"§e5. Telegrams§7: news from the road. Pay up, press on or gamble. §8/compagnie telegrams",
			"§e6. The Ledger§7: lend Marks (or gold) to borrowers. Defaulters get a collector.",
			"§8Grow from a Charter to the VOC: more job slots, more agents, bigger jobs.",
		};
		for (String line : lines) {
			source.sendSystemMessage(Component.literal(line));
		}
	}
}
