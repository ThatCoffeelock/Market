package com.thatcoffeelock.blimey;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import static net.minecraft.commands.Commands.literal;

/** /blimey (help for everyone), /blimey menu and /blimey tank (aboard), /blimey give and /blimey reload (ops). */
final class BlimeyCommands {
	private BlimeyCommands() {
	}

	static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext context, Commands.CommandSelection selection) {
		dispatcher.register(literal("blimey")
			.executes(ctx -> {
				help(ctx.getSource());
				return 1;
			})
			.then(literal("menu").executes(ctx -> {
				ServerPlayer player = ctx.getSource().getPlayerOrException();
				Airship ship = Airships.shipOf(player);
				if (ship == null) {
					ctx.getSource().sendFailure(Component.literal("You're not aboard an airship. Sneak + right-click one to open its menu from outside."));
					return 0;
				}
				AirshipMenu.open(player, ship);
				return 1;
			}))
			.then(literal("tank").executes(ctx -> {
				ServerPlayer player = ctx.getSource().getPlayerOrException();
				Airship ship = Airships.shipOf(player);
				if (ship == null || !ship.mayCommand(player)) {
					ctx.getSource().sendFailure(Component.literal(ship == null ? "You're not aboard an airship." : "The fuel tank is locked by the captain."));
					return 0;
				}
				AirshipMenu.openTank(player, ship);
				return 1;
			}))
			.then(literal("give").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS)).executes(ctx -> {
				ServerPlayer player = ctx.getSource().getPlayerOrException();
				BlimeyItems.give(player, BlimeyItems.airship());
				for (BlimeyItems.BombKind kind : BlimeyItems.BombKind.values()) {
					BlimeyItems.give(player, BlimeyItems.bomb(kind, 4));
				}
				ctx.getSource().sendSuccess(() -> Component.literal("One Flat-Pack Airship and four of each bomb. Diesel not included.").withStyle(ChatFormatting.GOLD), false);
				return 1;
			}))
			.then(literal("reload").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS)).executes(ctx -> {
				BlimeyConfig.load();
				ctx.getSource().sendSuccess(() -> Component.literal("Reloaded config/blimey.json.").withStyle(ChatFormatting.GOLD), false);
				return 1;
			})));
	}

	private static void help(CommandSourceStack source) {
		String[] lines = {
			"§4§l—— Blimey ——",
			"§6Flat-Pack Airship§7: §fPhantom Membrane, Netherite Ingot, Phantom Membrane / Block of Iron, Blast Furnace, Block of Iron / Block of Diamond, Piston, Block of Diamond",
			"§7Right-click the ground with it (about 9 × 26 blocks, 14 up). Rename it in an anvil first to name your airship.",
			"§7Right-click it to board (owners take the controls). Sneak + right-click it for the menu, or §f/blimey menu§7 aboard.",
			"§7Flying: §fW/S§7 throttle, §fA/D§7 turn, §fSpace§7 climb, §fCtrl§7 descend, §fShift§7 bail out (you get a parachute).",
			"§6Diesel§7: from a Fossil Fool Refinery. Put buckets in the §fFuel tank§7 (menu, or §f/blimey tank§7). One bucket is about 2 minutes cruising.",
			"§7Hovering burns less, climbing and racing burn more. Dry tanks: you sink gently to the ground.",
			"§7Refits: menu, §fEngineer§7. Engines (up to +80% speed), Fuel economy (up to -50% diesel), Cargo holds (up to 4 × 54 slots).",
			"§cBombs§7: Small (TNT + 4 iron + string), Big (4 TNT + 4 iron + block of iron), Huge (4 TNT + 4 blocks of iron + end crystal).",
			"§7Aboard, hold a bomb and right-click to drop it. It keeps the airship's speed: lead your target. On foot, right-click a block to light a fuse.",
			"§7Unlike cannonballs, bombs wreck buildings. Crew don't take blast, fall or fire damage aboard."
		};
		for (String line : lines) {
			source.sendSystemMessage(Component.literal(line));
		}
	}
}
