package com.thatcoffeelock.flintlock;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import net.minecraft.core.component.DataComponents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Only runs with -Dflintlock.smokeTest=true (CI). Boots a real server, lines up three villagers on a shooting range and
 * shoots each one with a different gun: the musket must hit hardest, the blunderbuss must shove hardest, and the
 * pistol must land somewhere in between. Then it shoots the floor to check balls stop at blocks, and shuts down.
 */
final class SmokeTest {
	private static final UUID MUSKET_TARGET = UUID.randomUUID();
	private static final UUID PISTOL_TARGET = UUID.randomUUID();
	private static final UUID BLUNDERBUSS_TARGET = UUID.randomUUID();

	private static Shot.Impact musketHit;
	private static Shot.Impact pistolHit;

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
			check(!stack.isDamageableItem(), gun.id + " has no durability bar");
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
				&& recipe.contains("reload (1 " + gun.ammo.title + ")"), gun.id + " recipe makes the same item as /flintlock give");
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
		FlintlockMod.LOG.info("FLINTLOCK SMOKE TEST PASSED");
		level.getServer().halt(false);
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
