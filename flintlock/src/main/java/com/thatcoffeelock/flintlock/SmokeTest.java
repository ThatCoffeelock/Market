package com.thatcoffeelock.flintlock;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import net.minecraft.core.component.DataComponents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Repairable;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Only runs with -Dflintlock.smokeTest=true (CI). Boots a real server, lines up three villagers on a shooting range and
 * shoots each one with a different gun: the musket must hit hardest, the blunderbuss must shove hardest, and the
 * pistol must land somewhere in between. Then it shoots the floor to check balls stop at blocks. Last it enchants guns
 * (through /enchant, so the tags that let them take bow and crossbow enchantments are tested too) and checks what Power,
 * Punch, Flame, Piercing and Multishot do to a shot, and that Quick Charge shortens the reload.
 */
final class SmokeTest {
	private static final UUID MUSKET_TARGET = UUID.randomUUID();
	private static final UUID PISTOL_TARGET = UUID.randomUUID();
	private static final UUID BLUNDERBUSS_TARGET = UUID.randomUUID();

	private static final UUID SMITH = UUID.randomUUID();
	private static final UUID POWER_TARGET = UUID.randomUUID();
	private static final UUID PUNCH_TARGET = UUID.randomUUID();
	private static final UUID FLAME_TARGET = UUID.randomUUID();
	private static final UUID PIERCED_FIRST = UUID.randomUUID();
	private static final UUID PIERCED_SECOND = UUID.randomUUID();
	private static final UUID PLAIN_FIRST = UUID.randomUUID();
	private static final UUID PLAIN_SECOND = UUID.randomUUID();

	private static Shot.Impact musketHit;
	private static Shot.Impact pistolHit;
	private static GunEnchants power;
	private static GunEnchants punch;

	private SmokeTest() {
	}

	static void run(MinecraftServer server) {
		try {
			ServerLevel level = server.overworld();
			Cmd.run(level, "forceload add -32 -16 32 48");
			FlintlockMod.later(100, () -> step(server, () -> build(level)));
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

	private static void build(ServerLevel level) throws IOException {
		MinecraftServer server = level.getServer();
		Cmd.run(level, "fill -16 99 -8 16 99 32 minecraft:stone");
		Cmd.run(level, "fill -16 100 -8 16 109 32 minecraft:air");

		for (Gun gun : Gun.values()) {
			ItemStack stack = GunItems.gun(gun);
			check(GunItems.gunOf(stack) == gun, gun.id + " is recognised");
			check(stack.is(Items.CARROT_ON_A_STICK), gun.id + " is a carrot on a stick to vanilla clients");
			check(stack.getMaxStackSize() == 1, gun.id + " doesn't stack");
			check(stack.isDamageableItem() && stack.getMaxDamage() == gun.durability, gun.id + " lasts " + gun.durability + " shots");
			Repairable repairable = stack.get(DataComponents.REPAIRABLE);
			check(repairable != null && repairable.isValidRepairItem(new ItemStack(gun.repairItem)), gun.id + " is repaired with " + gun.repairItem);
			check(!repairable.isValidRepairItem(new ItemStack(Items.DIAMOND)), gun.id + " isn't repaired with diamonds");
			check(stack.isEnchantable() && stack.get(DataComponents.ENCHANTABLE).value() == GunItems.ENCHANTABILITY, gun.id + " can be enchanted at a table");
			check(!GunEnchants.of(stack).any(), gun.id + " comes without enchantments");
			stack.hurtAndBreak(1, level, (ServerPlayer) null, item -> { });
			check(stack.getDamageValue() == 1, gun.id + " wears down");
			ItemStack old = GunItems.gun(gun);
			old.remove(DataComponents.MAX_DAMAGE);
			old.remove(DataComponents.REPAIRABLE);
			GunItems.setLoaded(old, gun, true);
			check(old.getMaxDamage() == gun.durability && old.has(DataComponents.REPAIRABLE), gun.id + " made before durability gets it when loaded");
			ItemStack older = GunItems.gun(gun);
			older.remove(DataComponents.ENCHANTABLE);
			GunItems.setLoaded(older, gun, true);
			check(older.isEnchantable(), gun.id + " made before enchanting gets it when loaded");
			check(stack.get(DataComponents.ITEM_MODEL) != null, gun.id + " looks like a crossbow");
			check(!GunItems.isLoaded(stack), gun.id + " comes unloaded");
			check(!stack.has(DataComponents.CHARGED_PROJECTILES), gun.id + " looks unloaded");
			GunItems.setLoaded(stack, gun, true);
			check(GunItems.isLoaded(stack) && GunItems.gunOf(stack) == gun, gun.id + " loads");
			check(stack.has(DataComponents.CHARGED_PROJECTILES), gun.id + " looks loaded");
			GunItems.setLoaded(stack, gun, false);
			check(!GunItems.isLoaded(stack) && !stack.has(DataComponents.CHARGED_PROJECTILES), gun.id + " unloads");
		}
		for (Gun.Ammo ammo : Gun.Ammo.values()) {
			ItemStack stack = GunItems.ammo(ammo, 4);
			check(GunItems.ammoOf(stack) == ammo && stack.getCount() == 4, ammo.id + " is recognised");
			check(GunItems.gunOf(stack) == null, ammo.id + " is not a gun");
		}
		for (Gun gun : Gun.values()) {
			String recipe = resource("/data/flintlock/recipe/" + gun.id + ".json");
			check(recipe.contains("\"" + gun.title + "\"") && recipe.contains(GunItems.stats(gun)) && recipe.contains(gun.blurb)
				&& recipe.contains("reload (1 " + gun.ammo.title + ")") && recipe.contains("\"minecraft:max_damage\": " + gun.durability)
				&& recipe.contains("\"minecraft:enchantable\"") && recipe.contains("\"value\": " + GunItems.ENCHANTABILITY)
				&& recipe.contains("\"items\": \"" + net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(gun.repairItem) + "\""),
				gun.id + " recipe makes the same item as /flintlock give");
		}
		for (Gun.Ammo ammo : Gun.Ammo.values()) {
			String recipe = resource("/data/flintlock/recipe/" + ammo.id + ".json");
			check(recipe.contains("\"" + ammo.title + "\"") && recipe.contains(ammo.blurb) && recipe.contains(ammo.usedBy)
				&& recipe.contains("minecraft:" + ammo.model), ammo.id + " recipe makes the same item as /flintlock give");
		}
		check(GunItems.gunOf(new ItemStack(Items.CARROT_ON_A_STICK)) == null, "a plain carrot on a stick is not a gun");
		check(GunItems.ammoOf(new ItemStack(Items.PAPER)) == null, "plain paper is not ammo");
		check(Gun.BLUNDERBUSS.falloff(0) == 1.0 && Gun.BLUNDERBUSS.falloff(100) == Gun.MIN_FALLOFF, "blunderbuss pellets lose power with range");
		check(Gun.MUSKET.falloff(100) == 1.0, "musket balls don't");
		check(Gun.MUSKET.damage > Gun.PISTOL.damage && Gun.MUSKET.damage > Gun.BLUNDERBUSS.damage, "the musket hits hardest per ball");
		for (String tag : new String[] {"bow", "crossbow"}) {
			check(resource("/data/minecraft/tags/item/enchantable/" + tag + ".json").contains("minecraft:carrot_on_a_stick"), "guns take the " + tag + " enchantments");
		}
		check(GunEnchants.NONE.reloadTicks(Gun.MUSKET) == Gun.MUSKET.reloadTicks && GunEnchants.NONE.damageMultiplier() == 1.0 && GunEnchants.NONE.fan() == 1,
			"no enchantments, no change");
		GunEnchants quick = new GunEnchants(0, 0, 0, 3, 0, 0, 0);
		check(quick.reloadTicks(Gun.MUSKET) < Gun.MUSKET.reloadTicks && quick.reloadTicks(Gun.MUSKET) > Gun.MUSKET.reloadTicks / 2,
			"Quick Charge III shortens the musket's reload (" + quick.reloadTicks(Gun.MUSKET) + " of " + Gun.MUSKET.reloadTicks + " ticks)");
		check(new GunEnchants(0, 0, 0, 20, 0, 0, 0).reloadTicks(Gun.MUSKET) == Gun.MUSKET.reloadTicks / 4, "reloading never gets faster than a quarter of the time");
		check(Gun.MUSKET.reloadTicks > Gun.BLUNDERBUSS.reloadTicks && Gun.BLUNDERBUSS.reloadTicks > Gun.PISTOL.reloadTicks, "reload times: pistol < blunderbuss < musket");

		// three lanes: musket at 15 blocks, pistol at 8, blunderbuss at 2
		summon(level, MUSKET_TARGET, 0.5, 15.5);
		summon(level, PISTOL_TARGET, -8.5, 8.5);
		summon(level, BLUNDERBUSS_TARGET, 8.5, 2.5);
		Shot.RECENT.clear();
		Shot.fire(level, Gun.MUSKET, new Vec3(0.5, 101.5, 0.5), new Vec3(0, 0, 1), null, false);
		check(Shot.flying().size() == 1, "musket fires one ball");
		FlintlockMod.later(10, () -> step(server, () -> musket(level)));
	}

	private static void musket(ServerLevel level) {
		MinecraftServer server = level.getServer();
		check(Shot.flying().isEmpty(), "musket ball landed");
		musketHit = impactOn(MUSKET_TARGET);
		check(musketHit != null, "musket ball hit the villager (hits recorded: " + Shot.RECENT.size() + ")");
		check(musketHit.landed, "musket damage landed (" + musketHit.damage + " dealt, health now " + musketHit.healthAfter + ")");
		check(Math.abs(musketHit.damage - Gun.MUSKET.damage) < 0.01, "musket hit for full damage at 15 blocks (" + musketHit.damage + ")");
		LivingEntity target = living(level, MUSKET_TARGET);
		check(target == null || target.getHealth() <= target.getMaxHealth() - Gun.MUSKET.damage + 0.5,
			"musket took " + Gun.MUSKET.damage + " health off (" + (target == null ? "dead" : target.getHealth() + " left") + ")");

		Shot.fire(level, Gun.PISTOL, new Vec3(-8.5, 101.5, 0.5), new Vec3(0, 0, 1), null, false);
		FlintlockMod.later(10, () -> step(server, () -> pistol(level)));
	}

	private static void pistol(ServerLevel level) {
		MinecraftServer server = level.getServer();
		pistolHit = impactOn(PISTOL_TARGET);
		check(pistolHit != null, "pistol ball hit the villager");
		check(pistolHit.landed, "pistol damage landed (health now " + pistolHit.healthAfter + ")");
		LivingEntity target = living(level, PISTOL_TARGET);
		check(target != null && Math.abs(target.getHealth() - (target.getMaxHealth() - Gun.PISTOL.damage)) < 0.5,
			"pistol took " + Gun.PISTOL.damage + " health off (" + (target == null ? "dead" : target.getHealth() + " left") + ")");

		int pellets = Shot.fire(level, Gun.BLUNDERBUSS, new Vec3(8.5, 101.2, 0.5), new Vec3(0, 0, 1), null, true).size();
		check(pellets == Gun.BLUNDERBUSS.pellets, "blunderbuss fires " + Gun.BLUNDERBUSS.pellets + " pellets");
		FlintlockMod.later(10, () -> step(server, () -> blunderbuss(level)));
	}

	private static void blunderbuss(ServerLevel level) {
		MinecraftServer server = level.getServer();
		check(Shot.flying().isEmpty(), "every pellet landed or dropped");
		Shot.Impact hit = impactOn(BLUNDERBUSS_TARGET);
		check(hit != null, "blunderbuss hit the villager");
		check(hit.landed, "blunderbuss damage landed (health now " + hit.healthAfter + ")");
		check(hit.balls >= 3, "at least 3 of 8 pellets hit at 2 blocks (" + hit.balls + ")");
		check(hit.damage > Gun.BLUNDERBUSS.damage * 2, "pellet damage adds up into one hit (" + hit.damage + ")");
		double shove = horizontal(hit.push);
		check(shove > horizontal(musketHit.push) && shove > horizontal(pistolHit.push),
			"blunderbuss shoves harder than musket and pistol (" + shove + " vs " + horizontal(musketHit.push) + ", " + horizontal(pistolHit.push) + ")");
		check(horizontal(hit.velocityAfter) > 0.6, "the villager was actually sent flying (speed " + horizontal(hit.velocityAfter) + ")");
		check(hit.velocityAfter.z > 0, "away from the shooter");

		// straight into the floor: it must stop there, not fly on through it
		Shot.fire(level, Gun.PISTOL, new Vec3(-12.5, 101.5, 0.5), new Vec3(0, -1, 0.5), null, false);
		FlintlockMod.later(3, () -> step(server, () -> floor(level)));
	}

	private static void floor(ServerLevel level) {
		check(Shot.flying().isEmpty(), "a ball fired into the floor stops there");
		MinecraftServer server = level.getServer();
		FlintlockMod.later(5, () -> step(server, () -> enchanting(level)));
	}

	/** Enchants guns with /enchant and fires them at lined-up villagers. */
	private static void enchanting(ServerLevel level) {
		MinecraftServer server = level.getServer();
		Cmd.run(level, "summon minecraft:villager 14.5 100 28.5 {" + Cmd.uuidNbt(SMITH) + ",NoAI:1b,PersistenceRequired:1b}");
		LivingEntity smith = living(level, SMITH);
		check(smith != null, "an enchanter was summoned");

		// every enchantment a gun is meant to take, one at a time
		check(GunEnchants.of(enchanted(level, smith, Gun.MUSKET, "minecraft:power 3")).power() == 3, "a musket takes Power");
		check(GunEnchants.of(enchanted(level, smith, Gun.MUSKET, "minecraft:punch 2")).punch() == 2, "a musket takes Punch");
		check(GunEnchants.of(enchanted(level, smith, Gun.PISTOL, "minecraft:flame 1")).flame() == 1, "a pistol takes Flame");
		check(GunEnchants.of(enchanted(level, smith, Gun.PISTOL, "minecraft:infinity 1")).infinity() == 1, "a pistol takes Infinity");
		check(GunEnchants.of(enchanted(level, smith, Gun.PISTOL, "minecraft:quick_charge 3")).quickCharge() == 3, "a pistol takes Quick Charge");
		check(GunEnchants.of(enchanted(level, smith, Gun.MUSKET, "minecraft:piercing 2")).piercing() == 2, "a musket takes Piercing");
		check(GunEnchants.of(enchanted(level, smith, Gun.BLUNDERBUSS, "minecraft:multishot 1")).multishot() == 1, "a blunderbuss takes Multishot");
		check(enchanted(level, smith, Gun.MUSKET, "minecraft:unbreaking 3").getEnchantments().size() == 1, "a musket takes Unbreaking");
		check(enchanted(level, smith, Gun.MUSKET, "minecraft:mending 1").getEnchantments().size() == 1, "a musket takes Mending");
		check(!enchanted(level, smith, Gun.MUSKET, "minecraft:sharpness 3").isEnchanted(), "a musket doesn't take Sharpness");
		GunEnchants both = GunEnchants.of(enchanted(level, smith, Gun.MUSKET, "minecraft:power 5", "minecraft:punch 2", "minecraft:piercing 1"));
		check(both.power() == 5 && both.punch() == 2 && both.piercing() == 1, "a gun takes several at once");
		check(GunItems.gunOf(enchanted(level, smith, Gun.MUSKET, "minecraft:power 1")) == Gun.MUSKET, "an enchanted gun is still a musket");

		// what they do: a lane each, all fired at once
		summon(level, POWER_TARGET, -14.5, 6.5);
		summon(level, PUNCH_TARGET, 12.5, 6.5);
		summon(level, FLAME_TARGET, -2.5, 6.5);
		summon(level, PIERCED_FIRST, -5.5, 6.5);
		summon(level, PIERCED_SECOND, -5.5, 10.5);
		summon(level, PLAIN_FIRST, 5.5, 6.5);
		summon(level, PLAIN_SECOND, 5.5, 10.5);
		power = GunEnchants.of(enchanted(level, smith, Gun.MUSKET, "minecraft:power 4"));
		punch = GunEnchants.of(enchanted(level, smith, Gun.MUSKET, "minecraft:punch 2"));
		GunEnchants flame = GunEnchants.of(enchanted(level, smith, Gun.PISTOL, "minecraft:flame 1"));
		GunEnchants piercing = GunEnchants.of(enchanted(level, smith, Gun.MUSKET, "minecraft:piercing 1"));
		Shot.RECENT.clear();
		Shot.fire(level, Gun.MUSKET, power, new Vec3(-14.5, 101.5, 0.5), new Vec3(0, 0, 1), null, false);
		Shot.fire(level, Gun.MUSKET, punch, new Vec3(12.5, 101.5, 0.5), new Vec3(0, 0, 1), null, false);
		Shot.fire(level, Gun.PISTOL, flame, new Vec3(-2.5, 101.5, 0.5), new Vec3(0, 0, 1), null, false);
		Shot.fire(level, Gun.MUSKET, piercing, new Vec3(-5.5, 101.5, 0.5), new Vec3(0, 0, 1), null, false);
		Shot.fire(level, Gun.MUSKET, GunEnchants.NONE, new Vec3(5.5, 101.5, 0.5), new Vec3(0, 0, 1), null, false);
		GunEnchants multi = new GunEnchants(0, 0, 0, 0, 0, 1, 0);
		check(Shot.fire(level, Gun.PISTOL, multi, new Vec3(-12.5, 101.5, 20.5), new Vec3(1, 0, 0), null, false).size() == 3, "Multishot fires three balls from a pistol");
		check(Shot.fire(level, Gun.BLUNDERBUSS, multi, new Vec3(-12.5, 101.5, 24.5), new Vec3(1, 0, 0), null, true).size() == 3 * Gun.BLUNDERBUSS.pellets,
			"Multishot fires three fans of pellets from a blunderbuss");
		FlintlockMod.later(10, () -> step(server, () -> enchantedShots(level)));
	}

	private static void enchantedShots(ServerLevel level) {
		Shot.Impact powerHit = impactOn(POWER_TARGET);
		check(powerHit != null && powerHit.landed, "the Power musket ball hit");
		check(Math.abs(powerHit.damage - Gun.MUSKET.damage * power.damageMultiplier()) < 0.01 && powerHit.damage > Gun.MUSKET.damage * 1.5,
			"Power IV hits harder: " + powerHit.damage + " instead of " + Gun.MUSKET.damage);

		Shot.Impact punchHit = impactOn(PUNCH_TARGET);
		check(punchHit != null && punchHit.landed, "the Punch musket ball hit");
		check(Math.abs(horizontal(punchHit.push) - horizontal(musketHit.push) * punch.knockbackMultiplier()) < 0.01 && horizontal(punchHit.push) > horizontal(musketHit.push),
			"Punch II shoves harder (" + horizontal(punchHit.push) + " instead of " + horizontal(musketHit.push) + ")");

		LivingEntity burning = living(level, FLAME_TARGET);
		check(burning != null && burning.isOnFire(), "Flame sets the target on fire");
		check(impactOn(FLAME_TARGET) != null && Math.abs(impactOn(FLAME_TARGET).damage - Gun.PISTOL.damage) < 0.01, "Flame doesn't change the damage");

		check(impactOn(PIERCED_FIRST) != null && impactOn(PIERCED_SECOND) != null, "Piercing: the ball went through the first villager and hit the second");
		check(Math.abs(impactOn(PIERCED_SECOND).damage - Gun.MUSKET.damage) < 0.01, "Piercing doesn't weaken the ball");
		check(impactOn(PLAIN_FIRST) != null && impactOn(PLAIN_SECOND) == null, "without Piercing the ball stops at the first villager");
		FlintlockMod.later(40, () -> step(level.getServer(), () -> finish(level)));
	}

	private static void finish(ServerLevel level) {
		check(Shot.flying().isEmpty(), "every ball landed or dropped");
		FlintlockMod.LOG.info("FLINTLOCK SMOKE TEST PASSED");
		level.getServer().halt(false);
	}

	/** A fresh gun of this kind, enchanted with these (as "id level", through /enchant) while an enchanter holds it. */
	private static ItemStack enchanted(ServerLevel level, LivingEntity holder, Gun gun, String... enchantments) {
		holder.setItemSlot(EquipmentSlot.MAINHAND, GunItems.gun(gun));
		for (String enchantment : enchantments) {
			Cmd.run(level, "enchant " + holder.getUUID() + " " + enchantment);
		}
		return holder.getMainHandItem().copy();
	}

	private static void summon(ServerLevel level, UUID id, double x, double z) {
		Cmd.run(level, "summon minecraft:villager " + Cmd.pos(x, 100, z) + " {" + Cmd.uuidNbt(id) + ",NoAI:1b,PersistenceRequired:1b,Rotation:[180f,0f]}");
		check(living(level, id) != null, "target villager summoned at " + x + ", " + z);
	}

	private static @Nullable LivingEntity living(ServerLevel level, UUID id) {
		return level.getEntity(id) instanceof LivingEntity living && living.isAlive() ? living : null;
	}

	private static @Nullable Shot.Impact impactOn(UUID id) {
		for (Shot.Impact impact : Shot.RECENT) {
			if (impact.target.getUUID().equals(id)) {
				return impact;
			}
		}
		return null;
	}

	private static String resource(String path) throws IOException {
		try (InputStream in = SmokeTest.class.getResourceAsStream(path)) {
			check(in != null, path + " is in the jar");
			return new String(in.readAllBytes(), StandardCharsets.UTF_8);
		}
	}

	private static double horizontal(Vec3 v) {
		return Math.sqrt(v.x * v.x + v.z * v.z);
	}

	private static void fail(MinecraftServer server, Throwable t) {
		FlintlockMod.LOG.error("FLINTLOCK SMOKE TEST FAILED", t);
		server.halt(false);
	}

	private static void check(boolean ok, String what) {
		if (!ok) {
			throw new IllegalStateException("Smoke check failed: " + what);
		}
		FlintlockMod.LOG.info("[smoke] ok: {}", what);
	}
}
