package com.thatcoffeelock.fuckillagers;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.thatcoffeelock.market.Money;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

/** /bounty (help, your contract), /bounty give and /bounty build (ops). */
final class BountyCommands {
	private BountyCommands() {
	}

	static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext context, Commands.CommandSelection selection) {
		dispatcher.register(literal("bounty")
			.executes(ctx -> {
				help(ctx.getSource());
				return 1;
			})
			.then(literal("contract").executes(ctx -> {
				ServerPlayer player = ctx.getSource().getPlayerOrException();
				Contract c = Bounties.openContract(player.getUUID());
				if (c == null) {
					ctx.getSource().sendFailure(Component.literal("No contract running. Take one at a Bounty Station."));
					return 0;
				}
				ctx.getSource().sendSuccess(() -> Component.literal("Wanted: " + c.target + " (" + c.tier().label + "), in " + c.site().what
					+ " near X " + c.x + ", Z " + c.z + ". Reward " + Money.format(Bounties.reward(c.tier())) + ".").withStyle(ChatFormatting.GOLD), false);
				return 1;
			}))
			.then(literal("give").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
				.then(literal("station").executes(ctx -> {
					Trophies.give(ctx.getSource().getPlayerOrException(), Trophies.station());
					return 1;
				}))
				.then(literal("fingers").then(argument("count", IntegerArgumentType.integer(1, 64)).executes(ctx -> {
					Trophies.give(ctx.getSource().getPlayerOrException(), Trophies.finger(IntegerArgumentType.getInteger(ctx, "count")));
					return 1;
				}))))
			.then(literal("build").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
				.then(argument("site", StringArgumentType.word())
					.suggests((ctx, b) -> {
						for (Tier.Site s : Tier.Site.values()) {
							b.suggest(s.name().toLowerCase());
						}
						return b.buildFuture();
					})
					.executes(ctx -> {
						ServerPlayer player = ctx.getSource().getPlayerOrException();
						Tier.Site site = Tier.Site.byName(StringArgumentType.getString(ctx, "site"));
						Contract test = new Contract();
						test.site = site.name();
						test.tier = tierOf(site).name();
						test.target = Bounties.randomName();
						test.x = player.getBlockX();
						test.z = player.getBlockZ();
						Sites.build((ServerLevel) player.level(), test);
						ctx.getSource().sendSuccess(() -> Component.literal("Built " + site.what + " (test, no contract: no skull).").withStyle(ChatFormatting.GOLD), false);
						return 1;
					}))));
	}

	private static Tier tierOf(Tier.Site site) {
		for (Tier tier : Tier.values()) {
			if (tier.sites.contains(site)) {
				return tier;
			}
		}
		return Tier.EASY;
	}

	private static void help(CommandSourceStack source) {
		String[] lines = {
			"§4§l—— Fuck Illagers ——",
			"§6Bounty Station§7: §fPaper Copper Paper / Copper Crafting-table Copper / Paper Copper Paper",
			"§7Kill illagers for §fIllager Fingers§7 (pillager/vindicator 1, evoker 2). A Bounty Station pays " + Money.format(Bounties.fingerPrice()) + " each.",
			"§7At the station, take a contract: §aEasy§7 (wagon/tower, " + Money.format(Bounties.reward(Tier.EASY)) + "), §6Medium§7 (camp/fortress, "
				+ Money.format(Bounties.reward(Tier.MEDIUM)) + "), §cHard§7 (dungeon/castle, " + Money.format(Bounties.reward(Tier.HARD)) + ").",
			"§7The target hides 1000-2000 blocks away. Hold the §fWanted Poster§7 to see how far. Kill it, sell its §fskull§7 at a station.",
			"§7§f/bounty contract§7 shows your current contract."
		};
		for (String line : lines) {
			source.sendSystemMessage(Component.literal(line));
		}
	}
}
