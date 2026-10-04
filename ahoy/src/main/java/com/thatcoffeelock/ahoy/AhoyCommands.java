package com.thatcoffeelock.ahoy;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import static net.minecraft.commands.Commands.literal;

/** /ahoy (help for everyone), /ahoy menu (open the ship's menu), /ahoy give (ops). */
final class AhoyCommands {
	private AhoyCommands() {
	}

	static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext context, Commands.CommandSelection selection) {
		dispatcher.register(literal("ahoy")
			.executes(ctx -> {
				help(ctx.getSource());
				return 1;
			})
			.then(literal("menu").executes(ctx -> {
				ServerPlayer player = ctx.getSource().getPlayerOrException();
				Ship ship = Ships.shipOf(player);
				if (ship == null) {
					ctx.getSource().sendFailure(Component.literal("You're not aboard a ship. Sneak + right-click one to open its menu from outside."));
					return 0;
				}
				ShipMenu.open(player, ship);
				return 1;
			}))
			.then(literal("guns").executes(ctx -> {
				ServerPlayer player = ctx.getSource().getPlayerOrException();
				Ship ship = Ships.shipOf(player);
				if (ship == null || ship.gunDeck == null) {
					ctx.getSource().sendFailure(Component.literal(ship == null ? "You're not aboard a ship." : "This server has no Cannon mod, so no gun ports."));
					return 0;
				}
				GunMenu.open(player, ship);
				return 1;
			}))
			.then(literal("bunks").executes(ctx -> {
				ServerPlayer player = ctx.getSource().getPlayerOrException();
				Ship ship = Ships.shipOf(player);
				if (ship == null) {
					ctx.getSource().sendFailure(Component.literal("You're not aboard a ship."));
					return 0;
				}
				BunkMenu.open(player, ship);
				return 1;
			}))
			.then(literal("give").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS)).executes(ctx -> {
				ServerPlayer player = ctx.getSource().getPlayerOrException();
				Bottle.give(player, Bottle.empty());
				ctx.getSource().sendSuccess(() -> Component.literal("One Ship in a Bottle. Mind the glass.").withStyle(ChatFormatting.GOLD), false);
				return 1;
			})));
	}

	private static void help(CommandSourceStack source) {
		String[] lines = {
			"§6§l—— Ahoy ——",
			"§bShip in a Bottle§7: §fWool Glass-bottle Wool / Chest Boat Chest / Planks Planks Planks",
			"§7Right-click open water with it. Rename the bottle in an anvil to name your ship.",
			"§7Right-click the ship to climb aboard (owners take the wheel). Sneak + right-click it for the menu.",
			"§7Aboard, right-click does what it does ashore: fish, shoot, throw, eat, open chests on the pier. With an empty hand it opens the menu (so does §f/ahoy menu§7).",
			"§7Sailing: §fW/S§7 sails up/down, §fA/D§7 rudder, §fSpace§7 bell, §fShift§7 go ashore.",
			"§7Cargo: two holds of 54 slots, in the menu. Bottle the ship up to take it with you.",
			"§7The wind matters: sail with it for full speed, against it for half.",
			"§7Guns: with the Cannon mod, four gun ports on deck take Cannons (menu, Gun deck). Man one from there; balls come from your pockets, then the holds (/ahoy guns).",
				"§7Bunks: menu, Bunks. Slot a bed into one of the two berths on the foredeck and lie down in it at night (§f/ahoy bunks§7). Sleep while anchored; Shift gets you up.",
				"§7Faster: menu, §fShipwright§7. Three refits (Extra canvas, Copper sheathing, Clipper rigging), each +20% top speed. The captain pays in materials.",
				"§aAt sea, nobody aboard can be hurt, and sea monsters get zapped away."
		};
		for (String line : lines) {
			source.sendSystemMessage(Component.literal(line));
		}
	}
}
