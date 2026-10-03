package com.thatcoffeelock.apocalypse;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.BooleanSupplier;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * Only runs with -Dapocalypse.smokeTest=true (CI). Boots a real server and puts some hordes in a pen in the sky:
 * they must herd, merge, recruit, share a target, chew through glass, come to a bell, speed up on Horde Night,
 * a fallen player must get back up, nothing may spawn in the light, they must smash the lights they can reach, and the sun must not burn them.
 */
final class SmokeTest {
	private static final int Y = 200;
	private static final int R = 24;

	private static Hordes.Horde first;
	private static UUID straggler;
	private static LivingEntity bait;
	private static Vec3 bell;
	private static double before;
	private static final BlockPos DARK = new BlockPos(33, Y, 33);
	private static Mob control;
	private static Mob sunny;
	private static final BlockPos TORCH = new BlockPos(14, Y, -14);
	private static final BlockPos LANTERN = new BlockPos(16, Y, -8);
	private static final BlockPos HIGH = new BlockPos(10, Y + 3, -20);

	private SmokeTest() {
	}

	private interface Step {
		void run() throws Exception;
	}

	static void run(MinecraftServer server) {
		ServerLevel level = server.overworld();
		Cmd.run(level, "forceload add -48 -48 48 48");
		Cmd.run(level, "difficulty normal");
		Cmd.run(level, "time set midnight");
		ApocalypseMod.later(100, () -> step(server, () -> setup(level)));
	}

	private static void step(MinecraftServer server, Step step) {
		try {
			step.run();
		} catch (Throwable t) {
			ApocalypseMod.LOG.error("APOCALYPSE SMOKE TEST FAILED", t);
			server.halt(false);
		}
	}

	private static void next(ServerLevel level, int ticks, Step step) {
		ApocalypseMod.later(ticks, () -> step(level.getServer(), step));
	}

	/** A glowstone pen in the sky (lit, so nothing spawns in it by itself), two hordes and a straggler. */
	private static void setup(ServerLevel level) {
		Cmd.run(level, "fill " + -R + " " + Y + " " + -R + " " + R + " " + (Y + 6) + " " + R + " minecraft:air");
		Cmd.run(level, "fill " + -R + " " + (Y - 1) + " " + -R + " " + R + " " + (Y - 1) + " " + R + " minecraft:glowstone");
		Cmd.run(level, "fill " + -R + " " + Y + " " + -R + " " + R + " " + (Y + 3) + " " + -R + " minecraft:barrier");
		Cmd.run(level, "fill " + -R + " " + Y + " " + R + " " + R + " " + (Y + 3) + " " + R + " minecraft:barrier");
		Cmd.run(level, "fill " + -R + " " + Y + " " + -R + " " + -R + " " + (Y + 3) + " " + R + " minecraft:barrier");
		Cmd.run(level, "fill " + R + " " + Y + " " + -R + " " + R + " " + (Y + 3) + " " + R + " minecraft:barrier");
		// a sealed stone room next to the pen: pitch dark inside, so zombies may spawn there (until someone brings a torch)
		Cmd.run(level, "fill 30 " + (Y - 1) + " 30 36 " + (Y + 3) + " 36 minecraft:stone hollow");

		check(Doors.breakable(Blocks.OAK_DOOR.defaultBlockState()), "zombies chew oak doors");
		check(Doors.breakable(Blocks.SPRUCE_TRAPDOOR.defaultBlockState()), "zombies chew trapdoors");
		check(Doors.breakable(Blocks.GLASS.defaultBlockState()), "zombies smash glass");
		check(Doors.breakable(Blocks.GLASS_PANE.defaultBlockState()), "zombies smash glass panes");
		check(!Doors.breakable(Blocks.IRON_DOOR.defaultBlockState()), "iron doors hold");
		check(!Doors.breakable(Blocks.IRON_TRAPDOOR.defaultBlockState()), "iron trapdoors hold");
		check(!Doors.breakable(Blocks.STONE.defaultBlockState()), "stone holds");
		check(!Doors.breakable(Blocks.OAK_PLANKS.defaultBlockState()), "planks hold (by default)");

		int total = 1200;
		check(Infection.stage(-1, total) == Infection.Stage.NONE, "no bite, no infection");
		check(Infection.stage(10, total) == Infection.Stage.BITTEN, "a fresh bite");
		check(Infection.stage(700, total) == Infection.Stage.ROTTING, "rotting after a while");
		check(Infection.stage(1100, total) == Infection.Stage.TURNING, "turning near the end");
		check(HordeNight.isHordeDay(7) && HordeNight.isHordeDay(14) && !HordeNight.isHordeDay(8), "Horde Night every 7th day");
		check(HordeNight.daysUntil(5) == 2 && HordeNight.daysUntil(7) == 0, "counting down to Horde Night");

		BlockPos ground = Hordes.ground(level, 5, 5, Y);
		check(ground != null && ground.getY() == Y, "finds the floor of the pen (" + ground + ")");

		first = Hordes.spawn(level, new BlockPos(-4, Y, 0), 6, false, false);
		Hordes.Horde second = Hordes.spawn(level, new BlockPos(2, Y, 0), 3, false, false);
		check(first != null && first.size() == 6, "a horde of 6 rises (" + (first == null ? 0 : first.size()) + ")");
		check(second != null && second.size() == 3, "a horde of 3 rises");
		check(first.leaderMob() != null, "the horde has a leader");
		for (Mob m : members(level, first)) {
			check(Undead.isZombie(m), "horde member " + Undead.typeId(m) + " is a zombie");
			check(Undead.boosted(m), "horde members are faster");
			AttributeInstance range = m.getAttribute(Attributes.FOLLOW_RANGE);
			// vanilla's random spawn bonus can nudge it either way, so: our modifier is on, and it's well past vanilla's 35
			check(range != null && range.getModifier(Undead.HORDE_RANGE) != null && range.getValue() > 40,
				"horde members track from further away (" + (range == null ? 0 : Math.round(range.getValue())) + " blocks)");
		}
		Mob loose = Undead.spawn(level, EntityTypes.ZOMBIE, new BlockPos(-2, Y, 4), 0f);
		check(loose != null && Hordes.of(loose) == null, "a straggler shambles about on its own");
		straggler = loose.getUUID();
		next(level, 80, () -> herd(level));
	}

	/** Hordes that meet become one; stragglers fall in. Then someone gets spotted. */
	private static void herd(ServerLevel level) {
		check(level.getEntity(straggler) != null, "the straggler is still around");
		Hordes.Horde h = Hordes.of(level.getEntity(straggler));
		check(h != null, "the straggler joined a horde");
		int[] census = Hordes.census(level, new Vec3(0, Y, 0), 64);
		check(census[0] == 1, "the two hordes merged into one (" + census[0] + " hordes)");
		check(census[1] >= 10, "one horde of at least 10 (" + census[1] + ")");
		first = h;

		check(!Undead.allowedAt(level, EntityTypes.ZOMBIE, new BlockPos(10, Y, -10)), "nothing spawns on a lit floor");
		check(Hordes.spawn(level, new BlockPos(10, Y, -10), 4, false, true) == null, "no horde rises in the light");
		check(Undead.allowedAt(level, EntityTypes.ZOMBIE, DARK), "zombies can spawn in a dark room");
		Cmd.run(level, "setblock " + (DARK.getX() - 1) + " " + DARK.getY() + " " + (DARK.getZ() - 1) + " minecraft:torch");

		bait = Undead.spawn(level, EntityTypes.VILLAGER, new BlockPos(-R + 3, Y, 0), 0f);
		check(bait instanceof Mob, "a villager to chase");
		((Mob) bait).setNoAi(true);
		// not invulnerable: zombies won't target what they can't hurt. Just very, very tough.
		Cmd.run(level, "effect give " + bait.getUUID() + " minecraft:resistance 120 4 true");
		List<Mob> mobs = members(level, h);
		Mob spotter = mobs.get(mobs.size() - 1);
		spotter.setTarget(bait);
		next(level, 50, () -> aggro(level));
	}

	private static void aggro(ServerLevel level) {
		List<Mob> mobs = members(level, first);
		int chasing = 0;
		for (Mob m : mobs) {
			if (m.getTarget() == bait) {
				chasing++;
			}
		}
		check(chasing == mobs.size(), "one saw the villager, so all of them did (" + chasing + "/" + mobs.size() + ")");

		check(!Undead.allowedAt(level, EntityTypes.ZOMBIE, DARK), "one torch and they can't spawn in that room any more");

		bait.discard();
		for (Mob m : mobs) {
			m.setTarget(null);
		}

		BlockPos window = new BlockPos(R - 3, Y, R - 3);
		Cmd.run(level, "setblock " + window.getX() + " " + window.getY() + " " + window.getZ() + " minecraft:glass");
		check(level.getBlockState(window).is(Blocks.GLASS), "a window to smash");
		int now = ApocalypseMod.seconds();
		int rounds = 0;
		while (!Doors.chewOnce(level, window, 3, mobs.get(0).getId(), now) && rounds < 50) {
			rounds++;
		}
		check(level.getBlockState(window).isAir(), "three zombies got through the window in " + (rounds + 1) + " seconds");
		check(rounds + 1 <= (ApocalypseConfig.get().breakSeconds + 2) / 3 + 1, "more zombies chew faster");

		Mob leader = first.leaderMob();
		check(leader != null, "the horde still has a leader");
		bell = new Vec3(R - 4, Y, -R + 4);
		before = leader.position().distanceTo(bell);
		int heard = Hordes.hear(level, bell, 64, 30, ApocalypseMod.seconds());
		check(heard >= mobs.size(), "the whole horde heard the bell (" + heard + ")");
		next(level, 200, () -> lured(level));
	}

	private static void lured(ServerLevel level) {
		Mob leader = first.leaderMob();
		check(leader != null, "the horde still has a leader after the bell");
		double after = leader.position().distanceTo(bell);
		check(after < before - 4, "the horde went to the bell (" + Math.round(before) + " → " + Math.round(after) + " blocks)");

		// lights: a torch and a lantern they can reach, and a torch on top of a pillar they can't
		Cmd.run(level, "setblock " + TORCH.getX() + " " + TORCH.getY() + " " + TORCH.getZ() + " minecraft:torch");
		Cmd.run(level, "setblock " + LANTERN.getX() + " " + LANTERN.getY() + " " + LANTERN.getZ() + " minecraft:lantern");
		Cmd.run(level, "fill " + HIGH.getX() + " " + Y + " " + HIGH.getZ() + " " + HIGH.getX() + " " + (HIGH.getY() - 1) + " " + HIGH.getZ() + " minecraft:stone");
		Cmd.run(level, "setblock " + HIGH.getX() + " " + HIGH.getY() + " " + HIGH.getZ() + " minecraft:torch");
		check(Lights.isLight(level, TORCH, level.getBlockState(TORCH)) && Lights.isLight(level, LANTERN, level.getBlockState(LANTERN)),
			"torches and lanterns count as lights");
		check(Lights.nearest(level, new BlockPos(0, Y, 0), 3, Set.of()) == null, "a glowstone floor underfoot is never a target");
		first.goal = null; // done with the bell
		waitFor(level, 40, () -> !level.getBlockState(TORCH).is(Blocks.TORCH) && !level.getBlockState(LANTERN).is(Blocks.LANTERN),
			() -> lights(level));
	}

	/**
	 * Checks once a second until the condition holds or maxSeconds run out, then moves on either way:
	 * the next step's own checks say exactly what didn't happen.
	 */
	private static void waitFor(ServerLevel level, int maxSeconds, BooleanSupplier done, Step then) {
		next(level, 20, () -> {
			if (done.getAsBoolean() || maxSeconds <= 1) {
				then.run();
			} else {
				waitFor(level, maxSeconds - 1, done, then);
			}
		});
	}

	private static void lights(ServerLevel level) {
		check(!level.getBlockState(TORCH).is(Blocks.TORCH), "the horde smashed the torch");
		check(!level.getBlockState(LANTERN).is(Blocks.LANTERN), "the horde smashed the lantern");
		check(level.getBlockState(HIGH).is(Blocks.TORCH), "the torch on top of the pillar is out of reach");
		check(first.leaderMob() != null, "the horde still has a leader after the lights");

		// sunlight: first a control (feature off: a bare zombie at noon must catch fire), then the real thing
		Cmd.run(level, "time set noon");
		ApocalypseConfig.get().sunproofZombies = false;
		control = bare(level, new BlockPos(-10, Y, 10));
		next(level, 200, () -> sunControl(level));
	}

	/** A zombie with nothing on its head (a helmet would protect it from the sun anyway). */
	private static Mob bare(ServerLevel level, BlockPos at) {
		Mob z = EntityTypes.ZOMBIE.create(level, EntitySpawnReason.EVENT);
		check(z != null, "a bare zombie");
		z.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0f, 0f);
		level.addFreshEntity(z);
		return z;
	}

	private static void sunControl(ServerLevel level) {
		check(control.isOnFire(), "control: with the feature off, a zombie burns at noon");
		ApocalypseConfig.get().sunproofZombies = true;
		control.clearFire();
		sunny = bare(level, new BlockPos(-14, Y, 14));
		next(level, 300, () -> sun(level));
	}

	private static void sun(ServerLevel level) {
		check(level.isBrightOutside(), "it's broad daylight");
		check(sunny.isAlive() && !sunny.isOnFire(), "a zombie stands in the noon sun and doesn't burn");
		check(sunny.getHealth() >= sunny.getMaxHealth(), "not even a little (" + sunny.getHealth() + "/" + sunny.getMaxHealth() + ")");
		check(control.isAlive() && !control.isOnFire(), "the control zombie stopped burning too");
		Mob leader = first.leaderMob();
		check(leader != null, "the horde still has a leader at noon");

		HordeNight.force(level.getServer());
		check(HordeNight.active(), "Horde Night can be started");
		AttributeInstance speed = leader.getAttribute(Attributes.MOVEMENT_SPEED);
		check(speed != null && speed.getModifier(Undead.HORDE_NIGHT_SPEED) != null, "hordes are faster on Horde Night");
		HordeNight.stop(level.getServer());
		check(!HordeNight.active() && speed.getModifier(Undead.HORDE_NIGHT_SPEED) == null, "and back to normal at dawn");

		Mob risen = Infection.rise(level, new BlockPos(0, Y, R - 4), "Steve");
		check(risen != null && risen.getCustomName() != null && risen.getCustomName().getString().equals("Steve"), "Steve didn't stay dead");
		check(risen.isPersistenceRequired(), "risen players don't despawn");

		int cleared = Hordes.clear();
		check(cleared >= 10 && Hordes.all().isEmpty(), "admins can clear the hordes (" + cleared + ")");

		ApocalypseMod.LOG.info("APOCALYPSE SMOKE TEST PASSED");
		level.getServer().halt(false);
	}

	private static List<Mob> members(ServerLevel level, Hordes.Horde h) {
		List<Mob> out = new ArrayList<>();
		for (UUID id : h.members) {
			if (level.getEntity(id) instanceof Mob m && m.isAlive()) {
				out.add(m);
			}
		}
		return out;
	}

	private static void check(boolean ok, String what) {
		if (!ok) {
			throw new IllegalStateException("Smoke check failed: " + what);
		}
		ApocalypseMod.LOG.info("[smoke] ok: {}", what);
	}
}
