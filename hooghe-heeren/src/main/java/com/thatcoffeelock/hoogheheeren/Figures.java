package com.thatcoffeelock.hoogheheeren;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * The people you can walk up to: a rival lord in their hall, a guest at your Audience Bell, the guards at a gate.
 * Each is a vanilla mannequin (a player model with a vanilla skin), standing still, that can't be hurt. They're
 * summoned when a player comes near and taken away when nobody's around; none of them are saved with the world (one
 * left over after a restart is tidied away when its chunk loads).
 * <p>
 * Everything we summon carries the attachment {@link HeerenMod#MARK}: "lord:owner:rival", "guest:owner:rival",
 * "guard:owner:rival:n", or a soldier's "brain:..."/"body:...".
 */
final class Figures {
	static final Map<String, LivingEntity> LIVE = new HashMap<>();
	private static final Map<String, Long> LAST_SEEN = new HashMap<>();

	private Figures() {
	}

	static @Nullable String mark(@Nullable Entity e) {
		return e == null ? null : e.getAttached(HeerenMod.MARK);
	}

	static @Nullable LivingEntity get(String mark) {
		LivingEntity e = LIVE.get(mark);
		return e == null || e.isRemoved() ? null : e;
	}

	/** Chunk loads: one of ours that we're not keeping track of is a leftover. */
	static void onLoad(Entity entity, ServerLevel level) {
		String mark = mark(entity);
		if (mark == null) {
			return;
		}
		LivingEntity live = LIVE.get(mark);
		if (live != null && live.getUUID().equals(entity.getUUID()) && entity instanceof LivingEntity living) {
			LIVE.put(mark, living); // the same figure, back from an unloaded chunk
			return;
		}
		if (Soldiers.adopt(entity)) {
			return;
		}
		HeerenMod.nextTick(entity::discard);
	}

	/** Who stands where, wearing what. */
	record Look(String name, String description, boolean slim, String skin, String colour, String helmet, String mainHand, String offHand) {
	}

	/** Summons a mannequin facing {@code yaw} (0 is south). Null if mannequins won't summon in this game. */
	static @Nullable LivingEntity summon(ServerLevel level, String mark, double x, double y, double z, float yaw, Look look, boolean gravity) {
		UUID id = UUID.randomUUID();
		String base = "summon minecraft:mannequin " + Cmd.pos(x, y, z) + " {" + Cmd.uuidNbt(id) + ",Tags:[\"hoogheheeren\"],CustomName:\""
			+ esc(look.name()) + "\",CustomNameVisible:1b,Silent:1b" + (gravity ? "" : ",NoGravity:1b");
		Cmd.run(level, base + ",immovable:1b,description:" + look.description() + ",profile:{texture:\"" + Names.texture(look.slim(), look.skin())
			+ "\",model:\"" + (look.slim() ? "slim" : "wide") + "\"}}");
		if (!(level.getEntity(id) instanceof LivingEntity)) {
			Cmd.run(level, base + "}");
		}
		if (!(level.getEntity(id) instanceof LivingEntity body)) {
			HeerenMod.LOG.warn("Could not summon a mannequin for {}: {}", look.name(), base + "}");
			Cmd.loud(level, base + "}");
			return null;
		}
		body.setAttached(HeerenMod.MARK, mark);
		body.setYRot(yaw);
		body.setYHeadRot(yaw);
		body.setYBodyRot(yaw);
		dress(level, id, look);
		LIVE.put(mark, body);
		LAST_SEEN.put(mark, level.getGameTime());
		return body;
	}

	/** Puts the clothes on: a tunic in the house's colour, and whatever they carry. */
	static void dress(ServerLevel level, UUID id, Look look) {
		int rgb = Names.colour(look.colour()).rgb();
		Cmd.run(level, "item replace entity " + id + " armor.chest with minecraft:leather_chestplate[dyed_color=" + rgb + "]");
		Cmd.run(level, "item replace entity " + id + " armor.legs with minecraft:leather_leggings[dyed_color=" + (rgb == 0x1D1D21 ? 0x474F52 : 0x1D1D21) + "]");
		Cmd.run(level, "item replace entity " + id + " armor.feet with minecraft:leather_boots[dyed_color=" + 0x3B2A1A + "]");
		if (!look.helmet().isEmpty()) {
			Cmd.run(level, "item replace entity " + id + " armor.head with " + look.helmet());
		}
		if (!look.mainHand().isEmpty()) {
			Cmd.run(level, "item replace entity " + id + " weapon.mainhand with " + look.mainHand());
		}
		if (!look.offHand().isEmpty()) {
			Cmd.run(level, "item replace entity " + id + " weapon.offhand with " + look.offHand());
		}
	}

	/** A line of text for a mannequin's description (the line under its name). */
	static String line(String text, String color) {
		return "{text:\"" + esc(text) + "\",color:\"" + color + "\"}";
	}

	static String esc(String s) {
		return s.replace("\\", "\\\\").replace("\"", "\\\"");
	}

	/** Makes sure the figure is standing there; summons it if it's not. */
	static @Nullable LivingEntity ensure(ServerLevel level, String mark, double x, double y, double z, float yaw, Look look) {
		LivingEntity e = get(mark);
		if (e != null) {
			LAST_SEEN.put(mark, level.getGameTime());
			if (e.level() != level || e.distanceToSqr(x, y, z) > 4) {
				e.teleportTo(x, y, z);
			}
			return e;
		}
		return summon(level, mark, x, y, z, yaw, look, true);
	}

	static void remove(String mark) {
		LivingEntity e = LIVE.remove(mark);
		LAST_SEEN.remove(mark);
		if (e != null && !e.isRemoved()) {
			e.discard();
		}
	}

	/** Takes away every figure whose mark starts with this. */
	static void removeAll(String prefix) {
		for (String mark : new ArrayList<>(LIVE.keySet())) {
			if (mark.startsWith(prefix)) {
				remove(mark);
			}
		}
	}

	static void clear() {
		for (LivingEntity e : LIVE.values()) {
			if (!e.isRemoved()) {
				e.discard();
			}
		}
		LIVE.clear();
		LAST_SEEN.clear();
	}

	/** Is a player (not a spectator) within {@code range} blocks of the spot? */
	static boolean playerNear(ServerLevel level, BlockPos pos, double range) {
		for (Player p : level.players()) {
			if (!p.isSpectator() && p.distanceToSqr(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5) < range * range) {
				return true;
			}
		}
		return false;
	}

	/** A spot near {@code around} where someone can stand: two blocks of air over something solid. */
	static @Nullable BlockPos standable(ServerLevel level, BlockPos around, int radius) {
		List<BlockPos> tries = new ArrayList<>();
		for (int r = 1; r <= radius; r++) {
			for (int dx = -r; dx <= r; dx++) {
				for (int dz = -r; dz <= r; dz++) {
					if (Math.max(Math.abs(dx), Math.abs(dz)) == r) {
						for (int dy = -1; dy <= 1; dy++) {
							tries.add(around.offset(dx, dy, dz));
						}
					}
				}
			}
		}
		for (BlockPos p : tries) {
			BlockState below = level.getBlockState(p.below());
			if (level.getBlockState(p).isAir() && level.getBlockState(p.above()).isAir() && below.isSolid()) {
				return p;
			}
		}
		return null;
	}
}
