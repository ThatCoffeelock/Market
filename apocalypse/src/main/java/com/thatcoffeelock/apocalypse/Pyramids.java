package com.thatcoffeelock.apocalypse;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

/**
 * Zombie pyramids. A horde that can't get through a wall goes over it: stuck zombies pile up against the same
 * stretch of wall, climb onto each other (they really ride each other, so you see the tower), and once it's
 * tall enough the top one scrambles onto the wall, then another every second while the pile holds.
 *
 * It takes zombiesPerBlock zombies for every block of wall above the first, so small hordes can't do it.
 * Counters: an overhang (anything above their heads on their side of the wall), or a wall taller than they can
 * stack. Nothing gets damaged; the answer is building better, not rebuilding.
 */
final class Pyramids {
	/** A pile of zombies against one stretch of wall. */
	static final class Pile {
		final BlockPos front;
		final int height;
		final BlockPos ledge;
		/** Bottom first. Each one rides the one before it. */
		final List<UUID> stack = new ArrayList<>();
		int lastSeen;
		int tallSince = -1;
		boolean warned;

		Pile(BlockPos front, int height) {
			this.front = front;
			this.height = height;
			this.ledge = front.above(height);
		}
	}

	private Pyramids() {
	}

	static boolean solid(ServerLevel level, BlockPos pos) {
		return !level.getBlockState(pos).getCollisionShape(level, pos).isEmpty();
	}

	/**
	 * How tall the wall is in front of a zombie standing at feet, facing dir, if it's a wall a pyramid can get
	 * over: at least 2 high (lower, they just jump), no taller than max, room to stand on top, and nothing
	 * overhanging the zombie's side. Otherwise -1.
	 */
	static int wallHeight(ServerLevel level, BlockPos feet, Direction dir, int max) {
		BlockPos front = feet.relative(dir);
		int h = 0;
		while (h <= max && solid(level, front.above(h))) {
			h++;
		}
		if (h < 2 || h > max) {
			return -1;
		}
		if (solid(level, front.above(h + 1))) {
			return -1; // no room to stand on top
		}
		for (int y = 2; y <= h + 1; y++) {
			if (solid(level, feet.above(y))) {
				return -1; // an overhang: nowhere to stack
			}
		}
		return h;
	}

	static int needed(int height) {
		return Math.max(2, (height - 1) * ApocalypseConfig.get().pyramidZombiesPerBlock);
	}

	static int maxHeight() {
		ApocalypseConfig cfg = ApocalypseConfig.get();
		return cfg.pyramidMaxHeight + (HordeNight.active() ? cfg.hordeNightPyramidBonus : 0);
	}

	/** Which way is the target, along the main axis. */
	static Direction towards(Entity from, Entity to) {
		double dx = to.getX() - from.getX();
		double dz = to.getZ() - from.getZ();
		return Math.abs(dx) > Math.abs(dz) ? (dx > 0 ? Direction.EAST : Direction.WEST) : (dz > 0 ? Direction.SOUTH : Direction.NORTH);
	}

	/** Once a second for a horde with a target. */
	static void climb(Hordes.Horde h, List<Mob> mobs, LivingEntity target, int now) {
		if (!ApocalypseConfig.get().pyramids) {
			return;
		}
		ServerLevel level = h.level;
		int max = maxHeight();
		Map<Pile, List<Mob>> at = new LinkedHashMap<>();
		for (Mob m : mobs) {
			if (m.isPassenger() || m.distanceToSqr(target) < 2.5 * 2.5) {
				continue;
			}
			Vec3 last = h.lastPos.get(m.getUUID());
			if (last == null || last.distanceToSqr(m.position()) > 1.0) {
				continue; // still getting somewhere
			}
			Direction dir = towards(m, target);
			int height = wallHeight(level, m.blockPosition(), dir, max);
			if (height < 0) {
				continue;
			}
			BlockPos front = m.blockPosition().relative(dir);
			Pile pile = null;
			for (Pile p : h.piles) {
				if (p.height == height && p.front.getY() == front.getY() && p.front.distManhattan(front) <= 3) {
					pile = p;
					break;
				}
			}
			if (pile == null) {
				pile = new Pile(front, height);
				h.piles.add(pile);
			}
			pile.lastSeen = now;
			at.computeIfAbsent(pile, p -> new ArrayList<>()).add(m);
		}

		for (Iterator<Pile> it = h.piles.iterator(); it.hasNext(); ) {
			Pile pile = it.next();
			List<Mob> base = at.getOrDefault(pile, List.of());
			pile.stack.removeIf(id -> !(level.getEntity(id) instanceof Mob m) || !m.isAlive());
			int total = base.size() + Math.max(0, pile.stack.size() - 1); // the stack's bottom is also in base
			if (now - pile.lastSeen > 3 || total < needed(pile.height)) {
				disband(level, pile);
				pile.tallSince = -1;
				if (now - pile.lastSeen > 3) {
					it.remove();
				}
				continue;
			}
			grow(level, pile, base);
			int tall = Math.min(3, pile.height);
			if (pile.stack.size() >= tall) {
				if (pile.tallSince < 0) {
					pile.tallSince = now;
				}
				if (!pile.warned && target instanceof ServerPlayer player) {
					pile.warned = true;
					player.connection.send(new ClientboundSetActionBarTextPacket(
						Component.literal("☠ They're climbing over each other to get to you.").withStyle(ChatFormatting.DARK_RED)));
				}
				if (now - pile.tallSince >= 2) {
					over(level, pile);
				}
			}
		}
	}

	/** Adds one zombie to the stack per second, so you see it build. */
	private static void grow(ServerLevel level, Pile pile, List<Mob> base) {
		int tall = Math.min(3, pile.height);
		if (pile.stack.size() >= tall) {
			return;
		}
		for (Mob m : base) {
			if (pile.stack.contains(m.getUUID()) || m.isVehicle()) {
				continue;
			}
			if (!pile.stack.isEmpty()) {
				UUID top = pile.stack.get(pile.stack.size() - 1);
				Cmd.run(level, "ride " + m.getUUID() + " mount " + top);
			}
			pile.stack.add(m.getUUID());
			Cmd.sound(level, "minecraft:entity.zombie.step", m.getX(), m.getY(), m.getZ(), 1.0f, 0.6f);
			return;
		}
	}

	/** The top of the pyramid goes over the wall. The next one climbs up to take its place. */
	private static void over(ServerLevel level, Pile pile) {
		UUID top = pile.stack.remove(pile.stack.size() - 1);
		if (!(level.getEntity(top) instanceof Mob m)) {
			return;
		}
		Cmd.run(level, "ride " + top + " dismount");
		Cmd.run(level, "tp " + top + " " + Cmd.pos(pile.ledge.getX() + 0.5, pile.ledge.getY(), pile.ledge.getZ() + 0.5));
		Cmd.sound(level, "minecraft:entity.zombie.attack_wooden_door", pile.ledge.getX() + 0.5, pile.ledge.getY(), pile.ledge.getZ() + 0.5, 0.6f, 0.5f);
		Cmd.particles(level, "minecraft:poof", pile.ledge.getX() + 0.5, pile.ledge.getY() + 0.5, pile.ledge.getZ() + 0.5, 0.3, 0.02, 6);
	}

	/** Everyone climbs down. */
	static void disband(ServerLevel level, Pile pile) {
		for (int i = pile.stack.size() - 1; i >= 1; i--) {
			Cmd.run(level, "ride " + pile.stack.get(i) + " dismount");
		}
		pile.stack.clear();
	}

	/** The horde lost its target: take every pyramid down. */
	static void disbandAll(Hordes.Horde h) {
		for (Pile pile : h.piles) {
			disband(h.level, pile);
		}
		h.piles.clear();
	}
}
