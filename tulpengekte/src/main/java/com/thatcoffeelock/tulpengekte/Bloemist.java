package com.thatcoffeelock.tulpengekte;

import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.UUID;

import com.thatcoffeelock.market.MarketData;
import com.thatcoffeelock.market.Money;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.TagValueOutput;

/**
 * The Bloemist: a villager who deals in bulbs. Any villager whose name has "Bloemist" in it is one, so a name tag
 * works, and so does handing a bulb to a jobless villager (or a nitwit: it doesn't take brains to sell flowers).
 *
 * Every day he wants three varieties (one common, one fine, one broken, now and then the Semper Augustus) and pays
 * the Market price plus a premium for them, up to a few per player per day. He also sells common bulbs, at a price
 * well above what they fetch, so nobody can loop him. Every Bloemist on the server wants the same things on the
 * same day: it's one market.
 */
final class Bloemist {
	private static final String VILLAGER = "minecraft:villager";
	private static final String[] NAMES = {"Joost", "Pieter", "Griet", "Saskia", "Maarten", "Trijntje", "Hendrick", "Geertruyd", "Abraham",
		"Lijsbeth", "Cornelis", "Aeltje"};
	private static final Random RANDOM = new Random();

	private Bloemist() {
	}

	static boolean isVillager(Entity entity) {
		return EntityType.getKey(entity.getType()).toString().equals(VILLAGER);
	}

	static boolean is(Entity entity) {
		return isVillager(entity) && entity.isAlive() && entity.hasCustomName()
			&& entity.getCustomName().getString().toLowerCase(Locale.ROOT).contains("bloemist");
	}

	private static void actionBar(ServerPlayer player, String text, ChatFormatting color) {
		player.connection.send(new ClientboundSetActionBarTextPacket(Component.literal(text).withStyle(color)));
	}

	// ---------------------------------------------------------------- talking to villagers

	/** Right-click a villager: a Bloemist opens his stall, a jobless villager offered a bulb becomes one. */
	static InteractionResult useEntity(ServerPlayer player, InteractionHand hand, Entity entity) {
		if (entity.isRemoved() || !isVillager(entity)) {
			return InteractionResult.PASS;
		}
		if (is(entity)) {
			if (hand == InteractionHand.MAIN_HAND) {
				ServerLevel level = (ServerLevel) entity.level();
				Cmd.sound(level, "minecraft:entity.villager.ambient", entity.getX(), entity.getY() + 1, entity.getZ(), 0.8f, 1.1f);
				player.openMenu(new SimpleMenuProvider((id, inv, p) -> new BloemistMenu(id, player, entity),
					Component.literal(entity.getCustomName().getString())));
			}
			return InteractionResult.SUCCESS; // no vanilla trading screen on top
		}
		if (hand != InteractionHand.MAIN_HAND || !TulpItems.isBulb(player.getMainHandItem()) || entity.hasCustomName()) {
			return InteractionResult.PASS;
		}
		String why = whyNot(entity);
		if (why != null) {
			actionBar(player, why, ChatFormatting.YELLOW);
			return InteractionResult.SUCCESS;
		}
		hire(entity);
		if (!player.isCreative()) {
			player.getMainHandItem().shrink(1);
		}
		ServerLevel level = (ServerLevel) entity.level();
		Cmd.sound(level, "minecraft:entity.villager.celebrate", entity.getX(), entity.getY() + 1, entity.getZ(), 1f, 1f);
		Cmd.particles(level, "minecraft:happy_villager", entity.getX(), entity.getY() + 1.8, entity.getZ(), 0.3, 0, 8);
		player.sendSystemMessage(Component.literal(entity.getCustomName().getString()).withStyle(ChatFormatting.LIGHT_PURPLE)
			.append(Component.literal(" sniffs the bulb, pockets it, and opens a flower stall. Right-click him to trade.").withStyle(ChatFormatting.GRAY)));
		return InteractionResult.SUCCESS;
	}

	/** Why this villager can't become a Bloemist, or null if they can. */
	static String whyNot(Entity villager) {
		TagValueOutput out = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, villager.level().registryAccess());
		villager.saveWithoutId(out);
		CompoundTag saved = out.buildResult();
		if (saved.getIntOr("Age", 0) < 0) {
			return "Too young to sell flowers. Come back when they can count.";
		}
		ListTag tags = saved.getListOrEmpty("Tags");
		for (int i = 0; i < tags.size(); i++) {
			if ("colonycraft".equals(tags.getStringOr(i, ""))) {
				return "They work for a colony. They've got enough on their plate.";
			}
		}
		String profession = saved.getCompoundOrEmpty("VillagerData").getStringOr("profession", "minecraft:none");
		if (!profession.endsWith(":none") && !profession.endsWith(":nitwit")) {
			return "They already have a job. Try a villager without one (a nitwit will do).";
		}
		return null;
	}

	/** Makes the villager a Bloemist. */
	static void hire(Entity villager) {
		villager.setCustomName(Component.literal("Bloemist " + NAMES[RANDOM.nextInt(NAMES.length)]).withStyle(ChatFormatting.LIGHT_PURPLE));
		if (villager instanceof Mob mob) {
			mob.setPersistenceRequired();
		}
	}

	// ---------------------------------------------------------------- the day's wants

	/** The Bloemist's day: a new set of wants every Minecraft day (20 minutes). */
	static long day(MinecraftServer server) {
		return server.overworld().getGameTime() / 24000L;
	}

	/** What every Bloemist wants on this day: a common variety, a fine one, and a broken one (or, 1 day in 5, the Semper Augustus). */
	static List<Variety> wants(long day) {
		Random random = new Random(day * 1637L + 0x7475_6C70L);
		Variety common = Variety.random(Variety.Tier.COMMON, random);
		Variety fine = Variety.random(Variety.Tier.FINE, random);
		Variety top = random.nextInt(5) == 0 ? Variety.SEMPER_AUGUSTUS : Variety.random(Variety.Tier.RARE, random);
		return List.of(common, fine, top);
	}

	/** What he pays this player for one bulb he wants, in cents: the Market price, his premium, and their Mercantile. */
	static long price(Variety variety, UUID player) {
		double mercantile = SkillsLink.bonus(player, "mercantile/passive") + SkillsLink.bonus(player, "mercantile/haggler");
		return Math.round(Prices.cents(variety) * (1 + TulpConfig.get().bloemistPremium) * (1 + mercantile));
	}

	/** How many more of this want he'll take from this player today. */
	static int left(long day, UUID player, Variety variety) {
		return Math.max(0, TulpConfig.get().dailyCap(variety.tier) - TulpStore.sold(day, player.toString(), variety));
	}

	/** Bulbs of this variety the player is carrying. */
	static int count(ServerPlayer player, Variety variety) {
		Inventory inv = player.getInventory();
		int n = 0;
		for (int i = 0; i < inv.getContainerSize(); i++) {
			if (TulpItems.variety(inv.getItem(i)) == variety) {
				n += inv.getItem(i).getCount();
			}
		}
		return n;
	}

	/** Sells him as many bulbs of this variety as he'll take today. Returns how many. */
	static int sell(ServerPlayer player, Variety variety) {
		long day = day(((ServerLevel) player.level()).getServer());
		if (!wants(day).contains(variety)) {
			return 0;
		}
		int n = Math.min(count(player, variety), left(day, player.getUUID(), variety));
		if (n <= 0) {
			return 0;
		}
		Inventory inv = player.getInventory();
		int remaining = n;
		for (int i = 0; i < inv.getContainerSize() && remaining > 0; i++) {
			ItemStack stack = inv.getItem(i);
			if (TulpItems.variety(stack) == variety) {
				int take = Math.min(stack.getCount(), remaining);
				stack.shrink(take);
				remaining -= take;
			}
		}
		inv.setChanged();
		long paid = price(variety, player.getUUID()) * n;
		MarketData.deposit(player, paid);
		TulpStore.addSold(day, player.getUUID().toString(), variety, n);
		double xp = switch (variety.tier) {
			case COMMON -> 0.5;
			case FINE -> 3;
			case RARE -> 10;
			case LEGENDARY -> 40;
		};
		SkillsLink.xp(player.getUUID(), "mercantile", xp * n);
		player.sendSystemMessage(Component.literal("Sold " + n + " " + variety.title + " for ").withStyle(ChatFormatting.GRAY)
			.append(Money.text(paid)).append(Component.literal(".").withStyle(ChatFormatting.GRAY)));
		return n;
	}

	/** What a common bulb costs at his stall, in cents. */
	static long bulbPrice() {
		return Money.fromDecimal(TulpConfig.get().bloemistBulbPrice);
	}

	/** Buys common bulbs from him. Returns false if it's not for sale or the player can't pay. */
	static boolean buy(ServerPlayer player, Variety variety, int count) {
		if (variety.tier != Variety.Tier.COMMON || count <= 0) {
			return false;
		}
		if (!MarketData.withdraw(player, bulbPrice() * count)) {
			return false;
		}
		TulpItems.give(player, TulpItems.bulb(variety, count));
		return true;
	}
}
