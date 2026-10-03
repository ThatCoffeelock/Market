package com.thatcoffeelock.overenchant;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import net.minecraft.core.Holder;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;

/**
 * Only runs with -Doverenchant.smokeTest=true (CI). Boots a real server and checks that the maximum level of
 * enchantments went up (with the default config: every enchantment with more than one level reaches X, one-level
 * enchantments are left alone), that the
 * enchanting table's costs were squeezed to match, that /enchant respects the new maximum and no more, and that the
 * level names past X are in the jar.
 */
final class SmokeTest {
	private static final UUID SMITH = UUID.randomUUID();

	private SmokeTest() {
	}

	static void run(MinecraftServer server) {
		try {
			ServerLevel level = server.overworld();
			run(level, "forceload add -16 -16 16 16");
			OverenchantMod.later(100, () -> step(server, () -> test(level)));
		} catch (Throwable t) {
			fail(server, t);
		}
	}

	private interface Step {
		void run() throws Exception;
	}

	private static void step(MinecraftServer server, Step step) {
		try {
			step.run();
		} catch (Throwable t) {
			fail(server, t);
		}
	}

	private static void test(ServerLevel level) throws IOException {
		OverenchantConfig defaults = new OverenchantConfig();
		check(defaults.raise(5) == 10, "with the defaults a maximum of V becomes X");
		check(defaults.raise(3) == 10 && defaults.raise(4) == 10 && defaults.raise(2) == 10, "III, IV and II become X too");
		check(defaults.raise(1) == 1, "one-level enchantments (Mending, Silk Touch) stay at one");
		check(defaults.raise(15) == 15, "a maximum already past the cap is left alone, never lowered");
		OverenchantConfig custom = new OverenchantConfig();
		custom.raiseTo = 0;
		custom.multiplier = 1.0;
		custom.bonusLevels = 2;
		check(custom.raise(5) == 7 && custom.raise(1) == 1, "bonus levels add on top");
		custom.multiplier = 2.0;
		custom.bonusLevels = 0;
		check(custom.raise(3) == 6 && custom.raise(5) == 10, "the multiplier scales the old maximum");
		custom.raiseTo = 8;
		check(custom.raise(2) == 8 && custom.raise(5) == 10, "raiseTo lifts the small ones and leaves the big ones to the multiplier");
		custom.cap = 6;
		check(custom.raise(5) == 6, "the cap holds");
		custom.raiseSingleLevel = true;
		check(custom.raise(1) == 6, "one-level enchantments go up when asked to");

		check(max(level, Enchantments.SHARPNESS) == 10, "Sharpness goes up to X (it is " + max(level, Enchantments.SHARPNESS) + ")");
		check(max(level, Enchantments.UNBREAKING) == 10, "Unbreaking goes up to X (it is " + max(level, Enchantments.UNBREAKING) + ")");
		check(max(level, Enchantments.PROTECTION) == 10, "Protection goes up to X (it is " + max(level, Enchantments.PROTECTION) + ")");
		check(max(level, Enchantments.EFFICIENCY) == 10, "Efficiency goes up to X");
		check(max(level, Enchantments.KNOCKBACK) == 10, "Knockback goes from II to X (it is " + max(level, Enchantments.KNOCKBACK) + ")");
		check(max(level, Enchantments.POWER) == 10 && max(level, Enchantments.QUICK_CHARGE) == 10 && max(level, Enchantments.PIERCING) == 10, "Power, Quick Charge and Piercing go to X");
		check(max(level, Enchantments.MENDING) == 1, "Mending stays at I");
		check(max(level, Enchantments.SILK_TOUCH) == 1, "Silk Touch stays at I");

		// the enchanting table: the new top level costs what the old top level did, and the levels in between are spread out evenly
		Enchantment sharpness = level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.SHARPNESS).value();
		Enchantment.Cost minCost = sharpness.definition().minCost();
		int vanillaFive = minCost.base() + minCost.perLevelAboveFirst() * 4;
		check(sharpness.getMinCost(10) == vanillaFive, "Sharpness X costs what V did at the table (" + sharpness.getMinCost(10) + " and " + vanillaFive + ")");
		check(sharpness.getMinCost(1) == minCost.base(), "Sharpness I costs what it always did");
		boolean rising = true;
		for (int l = 2; l <= 10; l++) {
			rising &= sharpness.getMinCost(l) > sharpness.getMinCost(l - 1) && sharpness.getMaxCost(l) > sharpness.getMaxCost(l - 1);
		}
		check(rising, "every level up to X costs more than the one before");
		check(sharpness.getMinCost(5) < vanillaFive, "the levels you already know got cheaper (V costs " + sharpness.getMinCost(5) + ", not " + vanillaFive + ")");
		check(level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.MENDING).value().getMinCost(1) > 0, "single-level enchantments keep their cost");

		// /enchant uses the same limit: X is fine, XI is refused
		run(level, "summon minecraft:villager 0.5 100 0.5 {" + uuidNbt(SMITH) + ",NoAI:1b,PersistenceRequired:1b}");
		LivingEntity smith = (LivingEntity) level.getEntity(SMITH);
		check(smith != null, "villager summoned");
		smith.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.DIAMOND_SWORD));
		run(level, "enchant " + SMITH + " minecraft:sharpness 10");
		check(levelOn(level, smith, Enchantments.SHARPNESS) == 10, "/enchant gives Sharpness X (it has " + levelOn(level, smith, Enchantments.SHARPNESS) + ")");
		run(level, "enchant " + SMITH + " minecraft:sharpness 11");
		check(levelOn(level, smith, Enchantments.SHARPNESS) == 10, "/enchant refuses Sharpness XI");
		run(level, "enchant " + SMITH + " minecraft:unbreaking 10");
		check(levelOn(level, smith, Enchantments.UNBREAKING) == 10, "/enchant gives Unbreaking X");

		String lang = resource("/assets/overenchant/lang/en_us.json");
		check(lang.contains("\"enchantment.level.11\": \"XI\"") && lang.contains("\"enchantment.level.100\": \"C\""), "levels past X have names");
		check(OverenchantCommands.roman(8).equals("VIII") && OverenchantCommands.roman(10).equals("X"), "roman numerals for the command");

		OverenchantMod.LOG.info("OVERENCHANT SMOKE TEST PASSED");
		level.getServer().halt(false);
	}

	private static int max(ServerLevel level, ResourceKey<Enchantment> key) {
		return level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(key).value().getMaxLevel();
	}

	private static int levelOn(ServerLevel level, LivingEntity holder, ResourceKey<Enchantment> key) {
		Holder<Enchantment> enchantment = level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(key);
		ItemEnchantments all = holder.getMainHandItem().get(DataComponents.ENCHANTMENTS);
		return all == null ? 0 : all.getLevel(enchantment);
	}

	/** NBT int-array form of a UUID, so we can pick the UUID of what we summon and find it again. */
	private static String uuidNbt(UUID uuid) {
		int[] a = UUIDUtil.uuidToIntArray(uuid);
		return "UUID:[I;" + a[0] + "," + a[1] + "," + a[2] + "," + a[3] + "]";
	}

	private static void run(ServerLevel level, String command) {
		MinecraftServer server = level.getServer();
		server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withLevel(level).withSuppressedOutput(), command);
	}

	private static String resource(String path) throws IOException {
		try (InputStream in = SmokeTest.class.getResourceAsStream(path)) {
			check(in != null, path + " is in the jar");
			return new String(in.readAllBytes(), StandardCharsets.UTF_8);
		}
	}

	private static void fail(MinecraftServer server, Throwable t) {
		OverenchantMod.LOG.error("OVERENCHANT SMOKE TEST FAILED", t);
		server.halt(false);
	}

	private static void check(boolean ok, String what) {
		if (!ok) {
			throw new IllegalStateException("Smoke check failed: " + what);
		}
		OverenchantMod.LOG.info("[smoke] ok: {}", what);
	}
}
