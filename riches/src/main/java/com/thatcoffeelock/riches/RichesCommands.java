package com.thatcoffeelock.riches;

import java.util.HashSet;
import java.util.Set;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

/** /riches (help), /riches relics, /riches trust|untrust|trusted, and the op-only give and admin commands. */
final class RichesCommands {
	private RichesCommands() {
	}

	static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext context, Commands.CommandSelection selection) {
		dispatcher.register(literal("riches")
			.executes(ctx -> {
				help(ctx.getSource());
				return 1;
			})
			.then(literal("relics").executes(ctx -> relics(ctx.getSource())))
			.then(literal("trust").then(argument("player", EntityArgument.player()).executes(ctx -> trust(ctx.getSource(),
				EntityArgument.getPlayer(ctx, "player"), true))))
			.then(literal("untrust").then(argument("player", EntityArgument.player()).executes(ctx -> trust(ctx.getSource(),
				EntityArgument.getPlayer(ctx, "player"), false))))
			.then(literal("trusted").executes(ctx -> trusted(ctx.getSource())))
			.then(literal("give").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
				.then(literal("ledger").executes(ctx -> give(ctx.getSource(), RichesItems.ledger())))
				.then(literal("door").executes(ctx -> give(ctx.getSource(), RichesItems.door())))
				.then(literal("case").executes(ctx -> give(ctx.getSource(), RichesItems.displayCase())))
				.then(literal("pedestal").executes(ctx -> give(ctx.getSource(), RichesItems.pedestal())))
				.then(literal("relic").then(argument("id", StringArgumentType.word()).executes(ctx -> {
					Relic relic = Relic.byId(StringArgumentType.getString(ctx, "id"));
					if (relic == null) {
						ctx.getSource().sendFailure(Component.literal("No such relic. /riches relics lists them (ids are like illager_crown)."));
						return 0;
					}
					return give(ctx.getSource(), RichesItems.relic(relic));
				}))))
			.then(literal("admin").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
				.then(literal("reload").executes(ctx -> {
					RichesConfig.load();
					for (Places.Vault v : Places.VAULTS.values()) {
						v.drawn = Double.NaN;
					}
					ctx.getSource().sendSuccess(() -> Component.literal("Riches config reloaded.").withStyle(ChatFormatting.GOLD), false);
					return 1;
				}))
				.then(literal("unfind").then(argument("id", StringArgumentType.word()).executes(ctx -> {
					Relic relic = Relic.byId(StringArgumentType.getString(ctx, "id"));
					if (relic == null || Relics.FOUND.remove(relic.id()) == null) {
						ctx.getSource().sendFailure(Component.literal("That relic isn't marked as found."));
						return 0;
					}
					Store.changed();
					ctx.getSource().sendSuccess(() -> Component.literal(relic.title + " is lost again, and can be found once more.").withStyle(ChatFormatting.GOLD), false);
					return 1;
				})))));
	}

	private static int give(CommandSourceStack source, ItemStack stack) throws CommandSyntaxException {
		ServerPlayer player = source.getPlayerOrException();
		RichesItems.give(player, stack);
		source.sendSuccess(() -> Component.literal("Here: " + stack.getHoverName().getString() + ".").withStyle(ChatFormatting.GOLD), false);
		return 1;
	}

	private static int trust(CommandSourceStack source, ServerPlayer other, boolean add) throws CommandSyntaxException {
		ServerPlayer me = source.getPlayerOrException();
		String mine = me.getUUID().toString();
		Set<String> set = Places.TRUST.computeIfAbsent(mine, k -> new HashSet<>());
		if (add) {
			set.add(other.getUUID().toString());
		} else {
			set.remove(other.getUUID().toString());
		}
		Store.changed();
		source.sendSuccess(() -> Component.literal(add
			? other.getName().getString() + " can now open your Vault Doors and use your Display Cases."
			: other.getName().getString() + " is out. The locks have been changed.").withStyle(ChatFormatting.GOLD), false);
		return 1;
	}

	private static int trusted(CommandSourceStack source) throws CommandSyntaxException {
		ServerPlayer me = source.getPlayerOrException();
		Set<String> set = Places.TRUST.getOrDefault(me.getUUID().toString(), Set.of());
		if (set.isEmpty()) {
			source.sendSuccess(() -> Component.literal("You trust nobody. Wise.").withStyle(ChatFormatting.GRAY), false);
			return 1;
		}
		StringBuilder names = new StringBuilder();
		for (String id : set) {
			ServerPlayer p = null;
			try {
				p = source.getServer().getPlayerList().getPlayer(java.util.UUID.fromString(id));
			} catch (IllegalArgumentException ignored) {
				// skip
			}
			names.append(names.length() == 0 ? "" : ", ").append(p != null ? p.getName().getString() : id.substring(0, 8) + "… (offline)");
		}
		source.sendSuccess(() -> Component.literal("You trust: " + names).withStyle(ChatFormatting.GOLD), false);
		return 1;
	}

	private static int relics(CommandSourceStack source) {
		source.sendSystemMessage(Component.literal("—— Relics: " + Relics.FOUND.size() + " of " + Relic.values().length + " found ——")
			.withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
		for (Relic.Collection c : Relic.Collection.values()) {
			source.sendSystemMessage(Component.literal(c.title).withStyle(c.color, ChatFormatting.BOLD));
			for (Relic r : c.relics()) {
				Relics.Found f = Relics.FOUND.get(r.id());
				source.sendSystemMessage(f != null
					? Component.literal("  ✔ " + r.title).withStyle(ChatFormatting.WHITE)
						.append(Component.literal("  found by " + f.name() + ", " + f.date()).withStyle(ChatFormatting.GRAY))
					: Component.literal("  ? ??? ").withStyle(ChatFormatting.DARK_GRAY)
						.append(Component.literal(" " + r.hint).withStyle(ChatFormatting.GRAY)));
			}
		}
		return 1;
	}

	private static void help(CommandSourceStack source) {
		String[] lines = {
			"§6§l—— Riches ——",
			"§eVault Ledger§7: put it on your vault's floor. Your §fMarket balance§7 piles up around it in gold. Walk in and wade.",
			"§eVault Door§7: opens for you and the players you §f/riches trust§7. Shuts by itself.",
			"§eDisplay Case§7 / §ePedestal§7: right-click with an item to show it off under a brass plaque. Empty hand takes it back.",
			"§eRelics§7: 24 one-of-a-kind treasures in 4 collections. §f/riches relics§7 shows what's found and where to look.",
			"§7Show a whole collection in your own cases and the Royal Society pays you.",
			"§7/riches trust <player> · /riches untrust <player> · /riches trusted"
		};
		for (String line : lines) {
			source.sendSystemMessage(Component.literal(line));
		}
	}
}
