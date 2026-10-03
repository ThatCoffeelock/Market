package com.thatcoffeelock.warehouse;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import static net.minecraft.commands.Commands.literal;

/** /warehouse (help), /warehouse list (your warehouses), /warehouse give core|rack|dock (ops). */
final class WarehouseCommands {
	private WarehouseCommands() {
	}

	static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext context, Commands.CommandSelection selection) {
		dispatcher.register(literal("warehouse")
			.executes(ctx -> {
				help(ctx.getSource());
				return 1;
			})
			.then(literal("list").executes(ctx -> {
				list(ctx.getSource().getPlayerOrException());
				return 1;
			}))
			.then(literal("give").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
				.then(literal("core").executes(ctx -> give(ctx.getSource(), Parts.core(), "a Warehouse Core")))
				.then(literal("rack").executes(ctx -> give(ctx.getSource(), Parts.rack().copyWithCount(16), "16 Storage Racks")))
				.then(literal("dock").executes(ctx -> give(ctx.getSource(), Parts.dock(), "a Loading Dock")))));
	}

	private static int give(CommandSourceStack source, ItemStack stack, String what) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		ServerPlayer player = source.getPlayerOrException();
		Parts.give(player, stack);
		source.sendSuccess(() -> Component.literal("Here's " + what + ".").withStyle(ChatFormatting.GOLD), false);
		return 1;
	}

	private static void list(ServerPlayer player) {
		int n = 0;
		for (Warehouse w : Warehouses.all()) {
			if (!w.isOwner(player) || w.owner.isEmpty()) {
				continue;
			}
			n++;
			String where = w.packed ? "packed up" : w.pos.toShortString() + " in " + w.dimension.replaceAll(".*[:/ ]", "").replace("]", "");
			player.sendSystemMessage(Component.literal("• " + w.name).withStyle(ChatFormatting.GOLD)
				.append(Component.literal(" · " + Gui.n(w.total()) + " / " + Gui.n(w.capacity()) + " items · " + where).withStyle(ChatFormatting.GRAY)));
		}
		if (n == 0) {
			player.sendSystemMessage(Component.literal("You don't own any warehouses yet. Craft a Warehouse Core!").withStyle(ChatFormatting.GRAY));
		}
	}

	private static void help(CommandSourceStack source) {
		WarehouseConfig config = WarehouseConfig.get();
		String[] lines = {
			"§6§l—— Warehouse ——",
			"§bWarehouse Core§7: §fIron Book Iron / Chest Cartography-table Chest / Iron Chest Iron",
			"§bStorage Rack§7: §fBarrel + Chest + 2 Iron Ingots §7(anywhere in the grid)",
			"§bLoading Dock§7: §fLantern + Chest + Lead §7(anywhere in the grid)",
			"§7Place a core, then racks touching it (or each other): §f" + config.coreCapacity + "§7 + §f" + config.rackCapacity + "§7 items per rack.",
			"§7Right-click the core or a rack to browse. Click to take, drop items on it to store.",
			"§7Fill it with hoppers into racks, a chest named §fWarehouse Intake§7 touching it,",
			"§7a Cargo Train §fDrop-off Station§7 touching it, or a §fLoading Dock§7 for ships.",
			"§7Settings: make it a specialist (food only, ores only...), lock it, rename it.",
			"§7Break the core to pack it up: the stock stays inside. §f/warehouse list§7 shows yours."
		};
		for (String line : lines) {
			source.sendSystemMessage(Component.literal(line));
		}
	}
}
