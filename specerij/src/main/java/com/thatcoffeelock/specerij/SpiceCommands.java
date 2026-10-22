package com.thatcoffeelock.specerij;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import static net.minecraft.commands.Commands.literal;

/** /specerij (help), /specerij here (what grows where you stand), /specerij give ... (ops). */
final class SpiceCommands {
	private SpiceCommands() {
	}

	static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext context, Commands.CommandSelection selection) {
		LiteralArgumentBuilder<CommandSourceStack> give = literal("give").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS));
		for (Spice spice : Spice.values()) {
			LiteralArgumentBuilder<CommandSourceStack> one = literal(spice.id())
				.executes(ctx -> give(ctx.getSource(), SpiceItems.cutting(spice, 4), "4 " + spice.title + " cuttings"));
			one.then(literal("fresh").executes(ctx -> give(ctx.getSource(), SpiceItems.fresh(spice, 16), "16 " + spice.freshName)));
			one.then(literal("dried").executes(ctx -> give(ctx.getSource(), SpiceItems.dried(spice, 16), "16 " + spice.driedName)));
			give.then(one);
		}
		give.then(literal("blacktea").executes(ctx -> give(ctx.getSource(), SpiceItems.blackTea(16), "16 Black Tea")));
		give.then(literal("rack").executes(ctx -> give(ctx.getSource(), SpiceItems.dryingRack(), "a Drying Rack")));
		dispatcher.register(literal("specerij")
			.executes(ctx -> {
				help(ctx.getSource());
				return 1;
			})
			.then(literal("here").executes(ctx -> here(ctx.getSource())))
			.then(give));
	}

	private static int give(CommandSourceStack source, ItemStack stack, String what) throws CommandSyntaxException {
		ServerPlayer player = source.getPlayerOrException();
		SpiceItems.give(player, stack);
		source.sendSuccess(() -> Component.literal("Here's " + what + ". No ship needed. Cheater.").withStyle(ChatFormatting.GOLD), false);
		return 1;
	}

	/** What grows where you stand, and whether it's a Hortus. */
	private static int here(CommandSourceStack source) throws CommandSyntaxException {
		ServerPlayer player = source.getPlayerOrException();
		ServerLevel level = (ServerLevel) player.level();
		BlockPos pos = player.blockPosition();
		String biome = Hortus.biome(level, pos);
		Spice spice = Spice.forBiome(biome);
		source.sendSystemMessage(Component.literal("You're in " + biome.substring(biome.indexOf(':') + 1).replace('_', ' ') + ". ")
			.withStyle(ChatFormatting.GRAY)
			.append(spice == null ? Component.literal("No spice grows wild here.").withStyle(ChatFormatting.YELLOW)
				: Component.literal(spice.title + " grows here: plant it, and break the grass for wild cuttings.").withStyle(ChatFormatting.GREEN)));
		boolean glass = Hortus.underGlass(level, pos);
		boolean heat = Hortus.heated(level, pos);
		source.sendSystemMessage(Component.literal(glass && heat ? "This is a Hortus: every spice grows here, at half speed."
			: "Not a Hortus: " + (glass ? "" : "no glass roof overhead") + (!glass && !heat ? ", " : "") + (heat ? "" : "no fire going within 4 blocks") + ".")
			.withStyle(glass && heat ? ChatFormatting.GREEN : ChatFormatting.DARK_GRAY));
		return 1;
	}

	private static void help(CommandSourceStack source) {
		String[] lines = {
			"§6§l—— Specerij ——",
			"§e1. Cuttings§7: break grass and ferns in a spice's home biome. Now and then you find a §acutting§7.",
			"§7   §fPepper§7 jungle · §fNutmeg§7 mangrove swamp · §fCloves§7 bamboo jungle · §fCinnamon§7 savanna",
			"§7   §fTea§7 cherry grove, windswept hills · §fSaffron§7 meadow, flower forest",
			"§e2. Plant§7: on grass, dirt or mud, in its home biome. Anywhere else it won't grow... except in a §6Hortus§7:",
			"§7   under a glass roof with a fire going within 4 blocks. Half speed. §f/specerij here§7 checks the spot.",
			"§e3. Pick§7: in a day it's ripe. Right-click to pick it; it grows back. Breaking it digs it up (cutting back).",
			"§e4. Dry§7: §fBarrel + String§7 = §6Drying Rack§7 (or name any barrel \"Drying Rack\"). Fresh spice dries in a day.",
			"§7   Leave §fGreen Tea§7 in two more days for §dBlack Tea§7.",
			"§e5. Sell§7: at the Market. Dried is worth about four times fresh. Saffron is worth a lot more.",
			"§8With Colonycraft: a Spice Plantation in a far-off colony grows the local spice, more the farther it is from your Headquarters.",
		};
		for (String line : lines) {
			source.sendSystemMessage(Component.literal(line));
		}
	}
}
