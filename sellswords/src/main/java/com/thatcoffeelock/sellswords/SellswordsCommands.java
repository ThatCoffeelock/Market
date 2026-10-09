package com.thatcoffeelock.sellswords;

import java.util.List;

import com.mojang.brigadier.CommandDispatcher;
import com.thatcoffeelock.market.Money;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import static net.minecraft.commands.Commands.literal;

/** /sellswords (help), list, rally, hold, charge; /sellswords give station and /sellswords hire (ops). */
final class SellswordsCommands {
	private SellswordsCommands() {
	}

	static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext context, Commands.CommandSelection selection) {
		dispatcher.register(literal("sellswords")
			.executes(ctx -> {
				help(ctx.getSource());
				return 1;
			})
			.then(literal("list").executes(ctx -> {
				ServerPlayer player = ctx.getSource().getPlayerOrException();
				List<Merc> mine = Mercs.ownedBy(player.getUUID());
				ctx.getSource().sendSuccess(() -> Component.literal("Your mercenaries (" + mine.size() + " / " + Mercs.squadCap(player.getUUID()) + "):")
					.withStyle(ChatFormatting.GOLD), false);
				for (Merc m : mine) {
					ctx.getSource().sendSuccess(() -> Component.literal(" • " + m.title() + " " + m.rank().stars() + " – " + Ui.ordersText(m) + ", "
						+ Ui.health(m).toLowerCase() + ", " + m.kills + " kills, last seen at " + (int) Math.floor(m.x) + " " + (int) Math.floor(m.y)
						+ " " + (int) Math.floor(m.z)).withStyle(m.rank().path.color), false);
				}
				return mine.size();
			}))
			.then(literal("rally").executes(ctx -> Horn.blow(ctx.getSource().getPlayerOrException(), false)))
			.then(literal("charge").executes(ctx -> Horn.blow(ctx.getSource().getPlayerOrException(), true)))
			.then(literal("give").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
				.then(literal("station").executes(ctx -> {
					Stations.give(ctx.getSource().getPlayerOrException(), Stations.item());
					return 1;
				})))
			.then(literal("hire").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS)).executes(ctx -> {
				ServerPlayer player = ctx.getSource().getPlayerOrException();
				Merc m = Mercs.hire((ServerLevel) player.level(), player.blockPosition(), player.getUUID(), player.getName().getString(), null);
				if (m == null) {
					ctx.getSource().sendFailure(Component.literal("Couldn't place a mercenary here."));
					return 0;
				}
				m.orders(Merc.Orders.FOLLOW);
				ctx.getSource().sendSuccess(() -> Component.literal("Hired " + m.name + " (free, admin).").withStyle(ChatFormatting.GOLD), false);
				return 1;
			})));
	}

	private static void help(CommandSourceStack source) {
		String[] lines = {
			"§6§l—— Sellswords ——",
			"§6Mercenary Station§7: §f_ Crossbow _ / Iron-sword Target Iron-sword / Gold Gold Gold",
			"§7Right-click the station to hire a recruit for " + Money.format(Math.round(SellswordsConfig.get().hireCost * 100))
				+ ". You can have " + SellswordsConfig.get().squadSize + " (more with the Leadership skill).",
			"§7Right-click a mercenary with an empty hand: §ffollow§7, §fhold this spot§7, §fback to the station§7, §fhunting§7, §fdismiss§7, and §fpromotions§7.",
			"§7Promotions cost gold ingots: §bRanged§7 path → Musketeer, §cMelee§7 path → Foestopper Bulwark. The first one picks the path.",
			"§7Goat horn: rally everyone in earshot, or make your followers hold. §fSneak§7 + horn: charge at what you're looking at.",
			"§7They board your ships and airships, follow you through portals, heal slowly, and die for good.",
			"§7§f/sellswords list§7 shows your squad. §f/sellswords rally§7 and §f/sellswords charge§7 work like the horn."
		};
		for (String line : lines) {
			source.sendSystemMessage(Component.literal(line));
		}
	}
}
