package com.thatcoffeelock.colonycraft;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.trading.Merchant;
import net.minecraft.world.item.trading.MerchantOffer;
import org.jetbrains.annotations.Nullable;

/**
 * The town street (Colonycraft 1.4.0): the trading post's master traders, the bank's vault, the museum's cases, the
 * chapel, the ranch's animals and the fuel depot's tanks. Colonies calls in here at the moments that matter:
 * hiring, furnishing, payday, demolishing.
 */
final class Street {
	private Street() {
	}

	// ---------------------------------------------------------------- daily income

	/** What the trading post, bank and museum bring in per day, in ₥, by tier. More than their staff cost. */
	static double income(BuildingType type, int tier) {
		int t = Math.max(1, Math.min(BuildingType.MAX_TIER, tier)) - 1;
		return switch (type) {
			case TRADING_POST -> new double[] {25, 55, 100}[t];
			case BANK -> new double[] {25, 50, 90}[t];
			case MUSEUM -> new double[] {20, 40, 75}[t];
			default -> 0;
		};
	}

	/** Extra per relic on show in a museum, per day, in ₥. */
	static final double PER_RELIC = 10;

	/** A building's earnings today, in cents: nothing without staff. */
	static long earnings(@Nullable ServerLevel level, Colony.Building b) {
		if (b.alive() == 0) {
			return 0;
		}
		long cents = Bank.cents(income(b.type, b.tier));
		if (b.type == BuildingType.MUSEUM && level != null) {
			cents += Bank.cents(PER_RELIC) * RichesLink.relics(level, shows(b));
		}
		return cents;
	}

	// ---------------------------------------------------------------- the trading post's master traders

	/** One trade: so many emeralds for this item (as item SNBT). */
	private record Trade(int emeralds, String sell) {
	}

	/** A master trader: their profession, title, and what they sell. */
	private record Master(String profession, String title, List<Trade> trades) {
	}

	private static String book(String enchantment, int level) {
		return "{id:\"minecraft:enchanted_book\",count:1,components:{\"minecraft:stored_enchantments\":{\"minecraft:" + enchantment + "\":" + level + "}}}";
	}

	private static String item(String id) {
		return "{id:\"minecraft:" + id + "\",count:1}";
	}

	/** One per tier: the toolsmith, then the armorer, then the librarian. */
	private static final Master[] MASTERS = {
		new Master("toolsmith", "Master Toolsmith", List.of(new Trade(4, item("iron_pickaxe")), new Trade(18, item("diamond_pickaxe")),
			new Trade(16, item("diamond_axe")), new Trade(9, item("diamond_shovel")), new Trade(10, item("diamond_hoe")))),
		new Master("armorer", "Master Armorer", List.of(new Trade(18, item("diamond_helmet")), new Trade(28, item("diamond_chestplate")),
			new Trade(24, item("diamond_leggings")), new Trade(15, item("diamond_boots")), new Trade(5, item("shield")))),
		new Master("librarian", "Master Librarian", List.of(new Trade(30, book("mending", 1)), new Trade(20, book("unbreaking", 3)),
			new Trade(25, book("efficiency", 5)), new Trade(22, book("protection", 4)), new Trade(12, item("name_tag"))))};

	/** How many of each trade a master sells before payday restocks them. */
	static final int STOCK = 4;

	static String title(int index) {
		return MASTERS[Math.max(0, Math.min(MASTERS.length - 1, index))].title();
	}

	/** What the master in this stall sells, for the Town Hall page. */
	static List<String> wares(int index) {
		List<String> out = new ArrayList<>();
		for (Trade t : MASTERS[Math.max(0, Math.min(MASTERS.length - 1, index))].trades()) {
			String sell = t.sell();
			String id = sell.substring(sell.indexOf("minecraft:") + 10, sell.indexOf('"', sell.indexOf("minecraft:")));
			if (sell.contains("stored_enchantments")) {
				int e = sell.indexOf("minecraft:", sell.indexOf("stored_enchantments"));
				id = sell.substring(e + 10, sell.indexOf('"', e)) + " book";
			}
			out.add(t.emeralds() + " emeralds: " + id.replace('_', ' '));
		}
		return out;
	}

	private static String offers(int index, boolean books) {
		StringBuilder sb = new StringBuilder("{Recipes:[");
		boolean first = true;
		for (Trade t : MASTERS[Math.max(0, Math.min(MASTERS.length - 1, index))].trades()) {
			if (!books && t.sell().contains("stored_enchantments")) {
				continue;
			}
			sb.append(first ? "" : ",").append("{buy:{id:\"minecraft:emerald\",count:").append(t.emeralds()).append("},sell:").append(t.sell())
				.append(",uses:0,maxUses:").append(STOCK).append(",rewardExp:0b,xp:0,priceMultiplier:0.0f,demand:0,specialPrice:0}");
			first = false;
		}
		return sb.append("]}").toString();
	}

	/** The extra summon data for trader number {@code index}: a master who stays put and sells for emeralds. */
	static String traderNbt(int index) {
		Master m = MASTERS[Math.max(0, Math.min(MASTERS.length - 1, index))];
		return ",NoAI:1b,VillagerData:{profession:\"minecraft:" + m.profession() + "\",level:5,type:\"minecraft:plains\"},Xp:250,Offers:" + offers(index, true);
	}

	/** Did the trader get our wares (rather than a villager's own random ones)? */
	static boolean stocked(Entity trader, int index) {
		if (!(trader instanceof Merchant villager) || villager.getOffers().isEmpty()) {
			return false;
		}
		MerchantOffer first = villager.getOffers().get(0);
		String wanted = MASTERS[Math.max(0, Math.min(MASTERS.length - 1, index))].trades().get(0).sell();
		return wanted.contains(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(first.getResult().getItem()).getPath() + "\"");
	}

	/** Puts the trades back to full stock (every payday, and right after hiring). Books too, if this server takes them. */
	static void restock(ServerLevel level, Colony.Building b) {
		for (int i = 0; i < b.villagers.size(); i++) {
			UUID v = b.villagers.get(i);
			Entity trader = v == null ? null : level.getEntity(v);
			if (trader != null) {
				stock(level, trader, i);
			}
		}
	}

	/** Full stock for one master. */
	static void stock(ServerLevel level, Entity trader, int index) {
		Cmd.run(level, "data merge entity " + trader.getUUID() + " {Offers:" + offers(index, true) + "}");
		if (!stocked(trader, index)) {
			// this game version wants enchanted books written differently: sell the rest without them
			ColonycraftMod.LOG.warn("A master trader's enchanted books didn't load; they sell the rest");
			Cmd.run(level, "data merge entity " + trader.getUUID() + " {Offers:" + offers(index, false) + "}");
		}
	}

	// ---------------------------------------------------------------- the bank

	/** Someone pays into the vault. False if they don't have it. */
	static boolean deposit(UUID who, Colony.Building bank, long cents) {
		if (cents <= 0 || !Bank.charge(who, cents)) {
			return false;
		}
		bank.vault += cents;
		Colonies.markDirty();
		return true;
	}

	/** Someone takes money out of the vault (anyone may). Returns what they got: no more than is in it. */
	static long withdraw(UUID who, String name, Colony.Building bank, long cents) {
		long out = Math.min(cents, bank.vault);
		if (out <= 0) {
			return 0;
		}
		bank.vault -= out;
		Bank.credit(who, name, out);
		Colonies.markDirty();
		return out;
	}

	// ---------------------------------------------------------------- furnishing and clearing up

	static List<BlockPos> shows(Colony.Building b) {
		List<BlockPos> out = new ArrayList<>();
		for (int i = 0; i < BuildingType.shows(b.tier); i++) {
			out.add(b.world(BuildingType.show(i)));
		}
		return out;
	}

	static List<BlockPos> tanks(Colony.Building b) {
		List<BlockPos> out = new ArrayList<>();
		for (int i = 0; i < BuildingType.depotTanks(b.tier); i++) {
			out.add(b.world(BuildingType.depotTank(i)));
		}
		return out;
	}

	private static List<BlockPos> pipes(Colony.Building b) {
		List<BlockPos> out = new ArrayList<>();
		for (int x = -b.type.half; x <= b.type.half; x++) {
			out.add(b.world(new BlockPos(x, 1, BuildingType.DEPOT_PIPE_Z)));
		}
		return out;
	}

	private static String animalTag(Colony.Building b) {
		return "colonycraft_" + b.id;
	}

	/** After it's built (or rebuilt): registers its Riches places or Fossil Fool machines, lets the animals out. */
	static void furnish(ServerLevel level, Colony.Building b) {
		Colony c = b.colony;
		switch (b.type) {
			case BANK -> {
				RichesLink.vault(level, b.world(BuildingType.BANK_LEDGER), b.id, c.name + " Bank");
				RichesLink.publicDoor(level, b.world(BuildingType.BANK_DOOR));
			}
			case MUSEUM -> {
				for (int i = 0; i < BuildingType.shows(b.tier); i++) {
					RichesLink.showcase(level, b.world(BuildingType.show(i)), BuildingType.isPedestal(i), c.owner.toString(), c.ownerName);
				}
			}
			case FUEL_DEPOT -> {
				for (int i = 0; i < BuildingType.depotTanks(b.tier); i++) {
					FossilLink.tank(level, b.world(BuildingType.depotTank(i)), BuildingType.depotFluid(i));
				}
				FossilLink.refinery(level, b.world(BuildingType.DEPOT_REFINERY), c.owner.toString());
				for (BlockPos pipe : pipes(b)) {
					FossilLink.pipe(level, pipe);
				}
			}
			case RANCH -> {
				Cmd.run(level, "kill @e[tag=" + animalTag(b) + "]");
				BlockPos at = b.world(BuildingType.PADDOCK);
				String[] herd = {"cow", "cow", "sheep", "sheep", "pig", "chicken", "chicken"};
				for (int i = 0; i < herd.length; i++) {
					double x = at.getX() + 0.5 + (i % 4) - 1.5;
					double z = at.getZ() + 0.5 + (i / 4) - 0.5;
					Cmd.run(level, "summon minecraft:" + herd[i] + " " + Cmd.pos(x, at.getY(), z) + " {PersistenceRequired:1b,Tags:[\"colonycraft\",\""
						+ animalTag(b) + "\"]}");
				}
			}
			case TRADING_POST -> restock(level, b);
			default -> {
			}
		}
	}

	/** Before it's knocked down: forgets its Riches places and Fossil Fool machines, rounds up its animals. */
	static void forget(ServerLevel level, Colony.Building b) {
		switch (b.type) {
			case BANK -> {
				RichesLink.remove(level, b.world(BuildingType.BANK_LEDGER));
				RichesLink.remove(level, b.world(BuildingType.BANK_DOOR));
			}
			case MUSEUM -> {
				for (BlockPos pos : shows(b)) {
					RichesLink.remove(level, pos);
				}
			}
			case FUEL_DEPOT -> {
				for (BlockPos pos : tanks(b)) {
					FossilLink.remove(level, pos);
				}
				FossilLink.remove(level, b.world(BuildingType.DEPOT_REFINERY));
				for (BlockPos pipe : pipes(b)) {
					FossilLink.remove(level, pipe);
				}
			}
			case RANCH -> Cmd.run(level, "kill @e[tag=" + animalTag(b) + "]");
			default -> {
			}
		}
	}

	/** Why it can't be demolished yet, or null. */
	static @Nullable String whyNoDemolish(Colony.Building b) {
		ServerLevel level = Colonies.level(b.colony);
		if (b.type == BuildingType.BANK && b.vault > 0) {
			return "The vault still holds " + Bank.format(b.vault) + ". Take it out at the teller first.";
		}
		if (b.type == BuildingType.MUSEUM && level != null && RichesLink.occupied(level, shows(b)) > 0) {
			return "Take the exhibits out of the cases first.";
		}
		if (b.type == BuildingType.FUEL_DEPOT && level != null && FossilLink.buckets(level, tanks(b)) > 0) {
			return "The tanks aren't empty. Drain them first.";
		}
		return null;
	}

	// ---------------------------------------------------------------- the chapel

	/** A staffed chapel makes replacing the dead cheaper: a quarter off per tier. */
	static double replaceDiscount(Colony c) {
		int best = 0;
		for (Colony.Building b : c.buildings) {
			if (b.type == BuildingType.CHAPEL && b.alive() > 0) {
				best = Math.max(best, b.tier);
			}
		}
		return 0.25 * best;
	}

	/** Every few seconds: anyone inside a staffed chapel is healed (regeneration, stronger at tier 3). */
	static void chapels(ServerLevel level, Colony c) {
		for (Colony.Building b : c.buildings) {
			if (b.type != BuildingType.CHAPEL || b.alive() == 0) {
				continue;
			}
			for (ServerPlayer player : level.players()) {
				if (b.contains(player.blockPosition())) {
					Cmd.run(level, "effect give " + player.getUUID() + " minecraft:regeneration 6 " + (b.tier >= 3 ? 1 : 0) + " true");
				}
			}
		}
	}
}
