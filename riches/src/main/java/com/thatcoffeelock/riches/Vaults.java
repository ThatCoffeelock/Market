package com.thatcoffeelock.riches;

import java.util.ArrayList;
import java.util.UUID;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * The money pile and the vault doors. The pile is a mound of gold around the Vault Ledger: one stretched gold block per
 * floor column, tallest next to the ledger, with coins and ingots lying on top. Its height follows the owner's Market
 * balance on a log scale, so it grows fast at first and slower later, like real wealth (allegedly). It's all display
 * entities riding one root, so nothing can be mined out of it. Players who walk into it wade: they slow down, and it
 * clinks.
 */
final class Vaults {
	static final String PILE_TAG = "riches_pile";
	private static final String[] COINS = {"minecraft:gold_nugget", "minecraft:gold_ingot", "minecraft:gold_nugget", "minecraft:emerald",
		"minecraft:gold_nugget", "minecraft:raw_gold"};

	private Vaults() {
	}

	static String tag(Places.Vault v) {
		return "riches_pile_" + Integer.toHexString(v.dimension.hashCode()) + "_" + Long.toHexString(v.pos.asLong());
	}

	/** Pile height in blocks for a balance in cents. */
	static double height(long cents) {
		RichesConfig c = RichesConfig.get();
		double marks = cents / 100.0;
		if (marks < c.pileStart) {
			return 0;
		}
		double h = 0.2 + c.pileHeightPerTenfold * Math.log10(marks / c.pileStart);
		return Math.min(c.pileMaxHeight, Math.round(h * 20) / 20.0);
	}

	static double balanceHeight(Places.Vault v) {
		if (v.owner.isEmpty()) {
			return 0;
		}
		try {
			return height(Bank.balance(UUID.fromString(v.owner)));
		} catch (IllegalArgumentException e) {
			return 0;
		}
	}

	static void tick(int ticks) {
		for (Places.Vault v : Places.VAULTS.values()) {
			ServerLevel level = Places.level(v.dimension);
			if (level == null || !level.isLoaded(v.pos)) {
				continue;
			}
			if (ticks % 100 == 0 || Double.isNaN(v.drawn)) {
				double h = balanceHeight(v);
				if (Double.isNaN(v.drawn) || Math.abs(h - v.drawn) > 0.001) {
					draw(level, v, h);
				}
			}
			if (ticks % 10 == 0 && v.drawn > 0) {
				wade(level, v);
			}
		}
		if (ticks % 10 == 0) {
			doors();
		}
	}

	// ---------------------------------------------------------------- the pile

	static void clear(ServerLevel level, Places.Vault v) {
		Cmd.run(level, "kill @e[tag=" + tag(v) + "]");
	}

	static String transformation(double tx, double ty, double tz, double sx, double sy, double sz, boolean flat) {
		String rot = flat ? "[0.7071f,0f,0f,0.7071f]" : "[0f,0f,0f,1f]";
		return "{left_rotation:" + rot + ",right_rotation:[0f,0f,0f,1f],translation:[" + Cmd.f(tx) + "f," + Cmd.f(ty) + "f," + Cmd.f(tz)
			+ "f],scale:[" + Cmd.f(sx) + "f," + Cmd.f(sy) + "f," + Cmd.f(sz) + "f]}";
	}

	/** Rebuilds the pile at this height (0: no pile). */
	static void draw(ServerLevel level, Places.Vault v, double h) {
		clear(level, v);
		v.drawn = h;
		if (h <= 0) {
			return;
		}
		int r = RichesConfig.get().pileRadius;
		String tag = tag(v);
		StringBuilder cmd = new StringBuilder("summon minecraft:item_display ").append(Cmd.pos(v.pos.getX() + 0.5, v.pos.getY(), v.pos.getZ() + 0.5))
			.append(" {Tags:[\"").append(PILE_TAG).append("\",\"").append(tag).append("\"],Passengers:[");
		boolean first = true;
		int n = 0;
		for (int dx = -r; dx <= r; dx++) {
			for (int dz = -r; dz <= r; dz++) {
				if (dx == 0 && dz == 0) {
					continue;
				}
				double d = Math.sqrt(dx * dx + dz * dz);
				double col = h * Math.max(0, 1 - d / (r + 1.0));
				if (col < 0.05) {
					continue;
				}
				BlockPos floor = v.pos.offset(dx, 0, dz);
				if (!level.getBlockState(floor).canBeReplaced()) {
					continue; // a wall or a pillar: no gold inside it
				}
				cmd.append(first ? "" : ",").append("{id:\"minecraft:block_display\",block_state:\"minecraft:gold_block\",Tags:[\"")
					.append(tag).append("\"],transformation:").append(transformation(dx - 0.5, 0, dz - 0.5, 1, col, 1, false)).append("}");
				first = false;
				// a few coins lying on top, the same ones every time
				long seed = v.pos.asLong() * 31 + dx * 7919L + dz * 104729L;
				if (Math.floorMod(seed, 3) == 0) {
					String coin = COINS[(int) Math.floorMod(seed, (long) COINS.length)];
					double ox = (Math.floorMod(seed, 7) - 3) / 10.0;
					double oz = (Math.floorMod(seed / 7, 7) - 3) / 10.0;
					cmd.append(",{id:\"minecraft:item_display\",item:{id:\"").append(coin).append("\",count:1},Tags:[\"").append(tag)
						.append("\"],transformation:").append(transformation(dx + ox, col + 0.02, dz + oz, 0.45, 0.45, 0.45, true)).append("}");
				}
				n++;
			}
		}
		if (n == 0) {
			return;
		}
		Cmd.run(level, cmd.append("]}").toString());
	}

	/** Players inside the pile wade through it: slower, with a clink, and a word from the narrator the first time. */
	private static void wade(ServerLevel level, Places.Vault v) {
		int r = RichesConfig.get().pileRadius;
		double cx = v.pos.getX() + 0.5;
		double cz = v.pos.getZ() + 0.5;
		for (ServerPlayer p : new ArrayList<>(level.players())) {
			boolean in = Math.abs(p.getX() - cx) <= r + 0.5 && Math.abs(p.getZ() - cz) <= r + 0.5
				&& p.getY() >= v.pos.getY() - 0.5 && p.getY() < v.pos.getY() + v.drawn;
			if (!in) {
				v.wading.remove(p.getUUID());
				continue;
			}
			Cmd.run(level, "effect give " + p.getUUID() + " minecraft:slowness 1 1 true");
			Cmd.sound(level, "minecraft:block.chain.step", p.getX(), p.getY(), p.getZ(), 0.5f, 1.6f + (float) Math.random() * 0.3f);
			Cmd.particles(level, "minecraft:wax_on", p.getX(), p.getY() + 0.4, p.getZ(), 0.3, 0.02, 3);
			if (v.wading.add(p.getUUID())) {
				long cents = 0;
				try {
					cents = Bank.balance(UUID.fromString(v.owner));
				} catch (IllegalArgumentException ignored) {
					// no owner
				}
				boolean mine = v.owner.equals(p.getUUID().toString());
				String text = mine ? "You dive into " + Bank.format(cents) + ". Swim, you magnificent tycoon, swim!"
					: "You wade through " + v.ownerName + "'s " + Bank.format(cents) + ". Don't get any ideas.";
				p.connection.send(new ClientboundSetActionBarTextPacket(Component.literal(text).withStyle(ChatFormatting.GOLD)));
			}
		}
	}

	// ---------------------------------------------------------------- vault doors

	/** Opens a vault door; it swings shut by itself. */
	static void open(ServerLevel level, Places.Door door) {
		door.openUntil = level.getGameTime() + RichesConfig.get().doorOpenTicks;
		setOpen(level, door, true);
	}

	static boolean isOpen(ServerLevel level, Places.Door door) {
		BlockState s = level.getBlockState(door.pos);
		return s.hasProperty(BlockStateProperties.OPEN) && s.getValue(BlockStateProperties.OPEN);
	}

	/** The way vanilla does it: set the lower half, and the upper half follows. */
	private static void setOpen(ServerLevel level, Places.Door door, boolean open) {
		BlockState s = level.getBlockState(door.pos);
		if (!s.hasProperty(BlockStateProperties.OPEN) || s.getValue(BlockStateProperties.OPEN) == open) {
			return;
		}
		level.setBlock(door.pos, s.setValue(BlockStateProperties.OPEN, open), 10);
		BlockState up = level.getBlockState(door.pos.above());
		if (up.hasProperty(BlockStateProperties.OPEN) && up.getValue(BlockStateProperties.OPEN) != open) {
			level.setBlock(door.pos.above(), up.setValue(BlockStateProperties.OPEN, open), 10);
		}
		Cmd.sound(level, open ? "minecraft:block.iron_door.open" : "minecraft:block.iron_door.close",
			door.pos.getX() + 0.5, door.pos.getY() + 1, door.pos.getZ() + 0.5, 1.0f, 0.6f);
	}

	/** Shuts doors whose time is up, and any a sneaky redstone circuit opened. */
	private static void doors() {
		for (Places.Door door : Places.DOORS.values()) {
			ServerLevel level = Places.level(door.dimension);
			if (level == null || !level.isLoaded(door.pos)) {
				continue;
			}
			if (level.getGameTime() >= door.openUntil && isOpen(level, door)) {
				setOpen(level, door, false);
			}
		}
	}
}
