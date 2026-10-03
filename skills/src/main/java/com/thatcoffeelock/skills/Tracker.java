package com.thatcoffeelock.skills;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stat;
import net.minecraft.stats.Stats;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;

/**
 * Once a second per player: reads vanilla statistics (fish caught, animals bred, villager trades, items enchanted,
 * gear crafted, potions drunk) and turns the increases into skill XP and perk effects. Statistics are a vanilla
 * feature every action already updates, so this needs no hooks into fishing rods, villagers or enchanting tables.
 * Also tracks distance ridden and sailed, and whether the player is AFK.
 */
final class Tracker {
	private static final String[][] FISH = {{"cod", "60"}, {"salmon", "25"}, {"tropical_fish", "12"}, {"pufferfish", "3"}};
	private static final String[][] TREASURE = {
		{"nautilus_shell", "20"}, {"name_tag", "15"}, {"saddle", "15"}, {"emerald", "15"}, {"lead", "10"},
		{"book", "10"}, {"heart_of_the_sea", "1"}, {"golden_apple", "4"}, {"diamond", "2"}, {"prismarine_crystals", "8"},
	};

	private static final class Watch {
		boolean ready;
		int fish, bred, traded, enchanted, potions;
		final Map<Item, Integer> crafted = new HashMap<>();
		double x, z;
		float yaw, pitch;
		int lastLook;
		int totalXp, xpLevel;
		double carry;
		final Map<Holder<MobEffect>, Integer> effects = new HashMap<>();
		double rideCarry, sailCarry;
	}

	private static final Map<UUID, Watch> WATCHES = new HashMap<>();
	/** Babies we already rolled twins for. Cleared every few minutes. */
	private static final Set<UUID> BABIES = new HashSet<>();
	/** Every damageable item (tools, weapons, armor...): crafting one is Blacksmithing. */
	private static List<Item> gear;

	private Tracker() {
	}

	private static List<Item> gear() {
		if (gear == null) {
			List<Item> list = new ArrayList<>();
			for (Item item : BuiltInRegistries.ITEM) {
				if (item.getDefaultInstance().isDamageableItem()) {
					list.add(item);
				}
			}
			gear = list;
		}
		return gear;
	}

	private static int stat(ServerPlayer player, Stat<?> stat) {
		return player.getStats().getValue(stat);
	}

	static void forget(UUID player) {
		WATCHES.remove(player);
	}

	static void clearBabies() {
		BABIES.clear();
	}

	static void tick(ServerPlayer player) {
		Watch w = WATCHES.computeIfAbsent(player.getUUID(), k -> new Watch());
		int now = SkillsMod.ticks();
		int fish = stat(player, Stats.CUSTOM.get(Stats.FISH_CAUGHT));
		int bred = stat(player, Stats.CUSTOM.get(Stats.ANIMALS_BRED));
		int traded = stat(player, Stats.CUSTOM.get(Stats.TRADED_WITH_VILLAGER));
		int enchanted = stat(player, Stats.CUSTOM.get(Stats.ENCHANT_ITEM));
		int potions = stat(player, Stats.ITEM_USED.get(Skills.item("potion")));
		if (!w.ready) {
			w.ready = true;
			w.fish = fish;
			w.bred = bred;
			w.traded = traded;
			w.enchanted = enchanted;
			w.potions = potions;
			for (Item item : gear()) {
				w.crafted.put(item, stat(player, Stats.ITEM_CRAFTED.get(item)));
			}
			w.x = player.getX();
			w.z = player.getZ();
			w.yaw = player.getYRot();
			w.pitch = player.getXRot();
			w.lastLook = now;
			w.totalXp = player.totalExperience;
			w.xpLevel = player.experienceLevel;
			snapshotEffects(player, w);
			return;
		}

		if (Math.abs(player.getYRot() - w.yaw) > 0.5f || Math.abs(player.getXRot() - w.pitch) > 0.5f) {
			w.lastLook = now;
		}
		w.yaw = player.getYRot();
		w.pitch = player.getXRot();
		int afkTicks = SkillsConfig.get().afkMinutes * 60 * 20;
		boolean afk = afkTicks > 0 && now - w.lastLook > afkTicks;

		if (fish > w.fish) {
			caught(player, fish - w.fish, afk);
		}
		if (bred > w.bred) {
			bred(player, bred - w.bred);
		}
		if (traded > w.traded) {
			Skills.award(player, Skill.MERCANTILE, 8.0 * (traded - w.traded));
		}
		if (enchanted > w.enchanted) {
			enchanted(player, enchanted - w.enchanted, Math.max(0, w.xpLevel - player.experienceLevel));
		}
		for (Item item : gear()) {
			int made = stat(player, Stats.ITEM_CRAFTED.get(item));
			int before = w.crafted.getOrDefault(item, made);
			if (made > before) {
				smithed(player, item, made - before);
			}
			w.crafted.put(item, made);
		}
		travel(player, w, afk);
		scholar(player, w);
		if (potions > w.potions) {
			potent(player, w);
		}
		ironStomach(player);

		w.fish = fish;
		w.bred = bred;
		w.traded = traded;
		w.enchanted = enchanted;
		w.potions = potions;
		w.totalXp = player.totalExperience;
		w.xpLevel = player.experienceLevel;
		snapshotEffects(player, w);
	}

	// ---------------------------------------------------------------- fishing

	private static void caught(ServerPlayer player, int count, boolean afk) {
		if (afk) {
			return;
		}
		for (int i = 0; i < count; i++) {
			Skills.award(player, Skill.FISHING, 20);
			double bonus = Skills.passive(player, Skill.FISHING) + Skills.perk(player, Perk.ANGLER);
			if (player.getVehicle() != null && !(player.getVehicle() instanceof LivingEntity)) {
				bonus += Skills.perk(player, Perk.MARINER);
			}
			if (Skills.roll(bonus)) {
				Skills.give(player, new ItemStack(Skills.item(pick(FISH))));
			}
			if (Skills.roll(Skills.perk(player, Perk.TREASURE_SENSE))) {
				Skills.give(player, new ItemStack(Skills.item(pick(TREASURE))));
				Cmd.sound(player, "minecraft:entity.player.levelup", 0.4f, 2.0f);
			}
		}
	}

	private static String pick(String[][] table) {
		int total = 0;
		for (String[] row : table) {
			total += Integer.parseInt(row[1]);
		}
		int roll = Skills.random().nextInt(total);
		for (String[] row : table) {
			roll -= Integer.parseInt(row[1]);
			if (roll < 0) {
				return row[0];
			}
		}
		return table[0][0];
	}

	// ---------------------------------------------------------------- farming (animals)

	private static void bred(ServerPlayer player, int count) {
		Skills.award(player, Skill.FARMING, 15.0 * count);
		double twins = Skills.perk(player, Perk.RANCHER);
		if (twins <= 0) {
			return;
		}
		ServerLevel level = (ServerLevel) player.level();
		AABB box = player.getBoundingBox().inflate(12);
		List<LivingEntity> babies = level.getEntitiesOfClass(LivingEntity.class, box,
			e -> e.isBaby() && e.tickCount < 60 && !(e instanceof ServerPlayer) && !BABIES.contains(e.getUUID()));
		int rolled = 0;
		for (LivingEntity baby : babies) {
			if (rolled++ >= count) {
				break;
			}
			BABIES.add(baby.getUUID());
			if (Skills.roll(twins)) {
				String type = BuiltInRegistries.ENTITY_TYPE.getKey(baby.getType()).toString();
				Cmd.run(level, "summon " + type + " " + Cmd.f(baby.getX()) + " " + Cmd.f(baby.getY()) + " " + Cmd.f(baby.getZ()) + " {Age:-24000}");
				Cmd.particles(level, "minecraft:heart", baby.getX(), baby.getY() + 0.5, baby.getZ(), 0.4, 5);
				player.sendSystemMessage(Component.literal("Twins!").withStyle(ChatFormatting.GREEN));
			}
		}
	}

	// ---------------------------------------------------------------- enchanting

	private static void enchanted(ServerPlayer player, int count, int levelsSpent) {
		int spent = Math.min(3, levelsSpent);
		Skills.award(player, Skill.ENCHANTING, 15.0 * count + 15.0 * spent);
		if (spent <= 0) {
			return;
		}
		if (Skills.roll(Skills.passive(player, Skill.ENCHANTING) + Skills.perk(player, Perk.MANA_WELL))) {
			player.giveExperienceLevels(spent);
			player.sendSystemMessage(Component.literal("Your levels flow back to you.")
				.withStyle(ChatFormatting.LIGHT_PURPLE));
		}
		if (Skills.roll(Skills.perk(player, Perk.LAPIS_SAVER))) {
			Skills.give(player, new ItemStack(Skills.item("lapis_lazuli"), spent));
		}
	}

	/** Scholar: a bonus on vanilla XP picked up since last second. */
	private static void scholar(ServerPlayer player, Watch w) {
		int gained = player.totalExperience - w.totalXp;
		double pct = Skills.perk(player, Perk.SCHOLAR);
		if (gained <= 0 || pct <= 0) {
			return;
		}
		w.carry += gained * pct;
		int bonus = (int) w.carry;
		if (bonus > 0) {
			w.carry -= bonus;
			player.giveExperiencePoints(bonus);
		}
	}

	// ---------------------------------------------------------------- blacksmithing

	private static void smithed(ServerPlayer player, Item item, int count) {
		String id = Skills.id(item);
		double xp;
		String material;
		if (id.contains("netherite")) {
			xp = 60;
			material = null;
		} else if (id.contains("diamond")) {
			xp = 30;
			material = "diamond";
		} else if (id.contains("iron")) {
			xp = 12;
			material = "iron_ingot";
		} else if (id.contains("chainmail")) {
			xp = 12;
			material = "iron_nugget";
		} else if (id.contains("golden")) {
			xp = 10;
			material = "gold_ingot";
		} else if (id.contains("copper")) {
			xp = 8;
			material = "copper_ingot";
		} else if (id.contains("turtle")) {
			xp = 20;
			material = null;
		} else if (id.contains("leather")) {
			xp = 6;
			material = "leather";
		} else if (id.contains("stone")) {
			xp = 4;
			material = "cobblestone";
		} else if (id.contains("wooden")) {
			xp = 2;
			material = "oak_planks";
		} else {
			xp = 8;
			material = null;
		}
		Skills.award(player, Skill.BLACKSMITHING, xp * count);
		if (material != null) {
			for (int i = 0; i < count; i++) {
				if (Skills.roll(Skills.perk(player, Perk.THRIFTY_SMITH))) {
					Skills.give(player, new ItemStack(Skills.item(material)));
				}
			}
		}
	}

	// ---------------------------------------------------------------- travel

	private static void travel(ServerPlayer player, Watch w, boolean afk) {
		double dx = player.getX() - w.x;
		double dz = player.getZ() - w.z;
		w.x = player.getX();
		w.z = player.getZ();
		Entity vehicle = player.getVehicle();
		if (vehicle == null || afk) {
			return;
		}
		double moved = Math.sqrt(dx * dx + dz * dz);
		if (moved < 0.5 || moved > 40) {
			return; // standing still, or a teleport
		}
		if (vehicle instanceof LivingEntity) {
			w.rideCarry += moved / 8.0;
			int xp = (int) w.rideCarry;
			if (xp > 0) {
				w.rideCarry -= xp;
				Skills.award(player, Skill.HORSERIDING, xp);
			}
		} else if (onWater(player, vehicle)) {
			w.sailCarry += moved / 8.0;
			int xp = (int) w.sailCarry;
			if (xp > 0) {
				w.sailCarry -= xp;
				Skills.award(player, Skill.SAILING, xp);
			}
		}
	}

	/** Boats, rafts and ship seats: whatever you sit in, if there's water right under it. */
	private static boolean onWater(ServerPlayer player, Entity vehicle) {
		ServerLevel level = (ServerLevel) player.level();
		BlockPos base = vehicle.blockPosition();
		for (int dy = 1; dy >= -5; dy--) {
			if (level.getFluidState(base.offset(0, dy, 0)).is(FluidTags.WATER)) {
				return true;
			}
		}
		return false;
	}

	// ---------------------------------------------------------------- brewing perks

	private static void snapshotEffects(ServerPlayer player, Watch w) {
		w.effects.clear();
		for (MobEffectInstance effect : player.getActiveEffects()) {
			w.effects.put(effect.getEffect(), effect.getDuration());
		}
	}

	/** Potent: effects that just appeared or got longer (you drank a potion) are stretched. */
	private static void potent(ServerPlayer player, Watch w) {
		double pct = Skills.perk(player, Perk.POTENT);
		if (pct <= 0) {
			return;
		}
		for (MobEffectInstance effect : new ArrayList<>(player.getActiveEffects())) {
			if (effect.isInfiniteDuration() || effect.isAmbient() || !effect.getEffect().value().isBeneficial()) {
				continue;
			}
			Integer before = w.effects.get(effect.getEffect());
			if (before != null && effect.getDuration() <= before) {
				continue;
			}
			int longer = (int) Math.round(effect.getDuration() * (1.0 + pct));
			player.addEffect(new MobEffectInstance(effect.getEffect(), longer, effect.getAmplifier(), effect.isAmbient(),
				effect.isVisible(), effect.showIcon()));
		}
	}

	/** Iron Stomach: harmful effects lose extra time every second. */
	private static void ironStomach(ServerPlayer player) {
		double pct = Skills.perk(player, Perk.IRON_STOMACH);
		if (pct <= 0) {
			return;
		}
		int extra = (int) Math.round(20 * pct);
		for (MobEffectInstance effect : new ArrayList<>(player.getActiveEffects())) {
			if (effect.isInfiniteDuration() || effect.getDuration() <= extra + 20
				|| effect.getEffect().value().getCategory() != MobEffectCategory.HARMFUL) {
				continue;
			}
			Holder<MobEffect> holder = effect.getEffect();
			MobEffectInstance shorter = new MobEffectInstance(holder, effect.getDuration() - extra, effect.getAmplifier(),
				effect.isAmbient(), effect.isVisible(), effect.showIcon());
			player.removeEffect(holder);
			player.addEffect(shorter);
		}
	}
}
