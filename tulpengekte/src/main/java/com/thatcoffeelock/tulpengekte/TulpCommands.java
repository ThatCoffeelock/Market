package com.thatcoffeelock.tulpengekte;

import java.util.List;
import java.util.UUID;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.thatcoffeelock.market.Money;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import static net.minecraft.commands.Commands.literal;

/** /tulpengekte (help for everyone), /tulpengekte wants (today's wants), /tulpengekte give|bloemist (ops). */
final class TulpCommands {
	private TulpCommands() {
	}

	static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext context, Commands.CommandSelection selection) {
		LiteralArgumentBuilder<CommandSourceStack> give = literal("give").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS));
		for (Variety v : Variety.values()) {
			give.then(literal(v.id()).executes(ctx -> give(ctx.getSource(), TulpItems.bulb(v, 8), "8 " + v.title + " bulbs")));
		}
		for (Variety.Tier tier : Variety.Tier.values()) {
			give.then(literal(tier.name().toLowerCase(java.util.Locale.ROOT)).executes(ctx -> {
				ServerPlayer player = ctx.getSource().getPlayerOrException();
				for (Variety v : Variety.of(tier)) {
					TulpItems.give(player, TulpItems.bulb(v, 4));
				}
				ctx.getSource().sendSuccess(() -> Component.literal("Here are 4 of every " + tier.title + " tulip. Don't tell the Bloemist.")
					.withStyle(ChatFormatting.LIGHT_PURPLE), false);
				return 1;
			}));
		}
		dispatcher.register(literal("tulpengekte")
			.executes(ctx -> {
				help(ctx.getSource());
				return 1;
			})
			.then(literal("wants").executes(ctx -> {
				wants(ctx.getSource());
				return 1;
			}))
			.then(give)
			.then(literal("bloemist").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS)).executes(ctx -> {
				ServerPlayer player = ctx.getSource().getPlayerOrException();
				Cmd.run((ServerLevel) player.level(), "summon minecraft:villager " + Cmd.pos(player.getX(), player.getY(), player.getZ())
					+ " {" + Cmd.uuidNbt(UUID.randomUUID()) + ",PersistenceRequired:1b,CustomName:\"Bloemist\"}");
				ctx.getSource().sendSuccess(() -> Component.literal("A Bloemist sets up shop. He smells faintly of soil and ambition.")
					.withStyle(ChatFormatting.LIGHT_PURPLE), false);
				return 1;
			})));
	}

	private static int give(CommandSourceStack source, ItemStack stack, String what) throws CommandSyntaxException {
		ServerPlayer player = source.getPlayerOrException();
		TulpItems.give(player, stack);
		source.sendSuccess(() -> Component.literal("Here's " + what + ". Spend them wisely. Nobody in 1637 did.").withStyle(ChatFormatting.LIGHT_PURPLE), false);
		return 1;
	}

	private static void wants(CommandSourceStack source) {
		long day = Bloemist.day(source.getServer());
		List<Variety> wants = Bloemist.wants(day);
		ServerPlayer player = source.getPlayer();
		source.sendSystemMessage(Component.literal("Today the Bloemist wants:").withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD));
		for (Variety v : wants) {
			long cents = player != null ? Bloemist.price(v, player.getUUID()) : Math.round(Prices.cents(v) * (1 + TulpConfig.get().bloemistPremium));
			String left = player != null ? " (" + Bloemist.left(day, player.getUUID(), v) + " more from you today)" : "";
			source.sendSystemMessage(Component.literal(" ✿ ").withStyle(v.tier.format)
				.append(Component.literal(v.title).withStyle(v.tier.format))
				.append(Component.literal(" · " + v.tier.title + " · ").withStyle(ChatFormatting.DARK_GRAY))
				.append(Component.literal(Money.format(cents) + " each" + left).withStyle(ChatFormatting.GOLD)));
		}
	}

	private static void help(CommandSourceStack source) {
		TulpConfig c = TulpConfig.get();
		String[] lines = {
			"§d§l—— Tulpengekte ——",
			"§e1. Bulbs§7: break a wild tulip, now and then there's a §fbulb§7 under it. Or buy common ones from a §dBloemist§7.",
			"§e2. Plant§7: right-click grass, dirt or farmland with a bulb. It flowers in about a day. No bone meal.",
			"§e3. Lift§7: break the flower for the bulb back plus §foffsets§7 (common bulbs give 0-2).",
			"§e4. Breaking§7: now and then an offset comes up streaked: a §aFine§7 (1 in 20), §dBroken§7 (1 in 100)",
			"§7   or the §6Semper Augustus§7 (1 in 1,000). Broken bulbs are weak: few offsets, and half revert.",
			"§e5. Sell§7: any bulb sells at the Market (§f₥" + fmt(c.commonSellPrice) + " / ₥" + fmt(c.fineSellPrice) + " / ₥" + fmt(c.rareSellPrice)
				+ " / ₥" + fmt(c.legendarySellPrice) + "§7).",
			"§7   A §dBloemist§7 pays +" + Math.round(c.bloemistPremium * 100) + "% for today's three wants (§f/tulpengekte wants§7).",
			"§e6. Bloemist§7: hand a bulb to a jobless villager or a nitwit, or name-tag any villager \"Bloemist\".",
			"§8Every Bloemist wants the same thing on the same day. It's one market. Don't corner it.",
		};
		for (String line : lines) {
			source.sendSystemMessage(Component.literal(line));
		}
	}

	private static String fmt(double marks) {
		return marks == Math.floor(marks) ? Long.toString((long) marks) : Double.toString(marks);
	}
}
