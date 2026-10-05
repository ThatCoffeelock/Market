package com.thatcoffeelock.fossilfool;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import org.jetbrains.annotations.Nullable;

/**
 * Oil underground. Whether a chunk has an oil pocket, and where and how big it is, follows from the world seed and
 * the chunk's position, so pockets exist everywhere from the start without touching world generation. The oil only
 * shows up as blocks when someone breaks into the pocket (by hand or with a Drill Rig): then its rock turns into
 * crude (black concrete), and every crude block is one bucket. Only the blocks we turned into oil count as oil, so
 * placing black concrete doesn't print money.
 */
final class Pockets {
	/** A pocket: an ellipsoid of rock that turns into crude when broken into. Keyed by its chunk. */
	record Pocket(long key, int x, int y, int z, int rx, int ry, int rz, boolean gusher) {
		boolean contains(int bx, int by, int bz) {
			double dx = (bx - x) / (rx + 0.5);
			double dy = (by - y) / (ry + 0.5);
			double dz = (bz - z) / (rz + 0.5);
			return dx * dx + dy * dy + dz * dz <= 1.0;
		}

		/** Inside, or touching the outside of it (breaking a block next to the oil also breaks in). */
		boolean near(int bx, int by, int bz) {
			return Math.abs(bx - x) <= rx + 1 && Math.abs(by - y) <= ry + 1 && Math.abs(bz - z) <= rz + 1;
		}

		BlockPos center() {
			return new BlockPos(x, y, z);
		}
	}

	/** Chunk key -> its pocket, or null for none. Pure function of seed + config, so it's only a cache. */
	private static final Map<Long, Pocket> CACHE = new LinkedHashMap<>(256, 0.75f, true) {
		@Override
		protected boolean removeEldestEntry(Map.Entry<Long, Pocket> eldest) {
			return size() > 8192;
		}
	};
	private static final Map<Long, Boolean> HAS = new HashMap<>();
	/** Test pockets made with {@link #create}, by key. */
	private static final Map<Long, Pocket> MADE = new HashMap<>();

	/** Pockets that were broken into: pocket key -> the positions that are still crude. */
	static final Map<Long, Set<Long>> OIL = new HashMap<>();
	/** Where each opened pocket is, so a dowsing rod and a rig can find their way back. */
	static final Map<Long, Pocket> OPENED = new HashMap<>();
	/** Oil block -> its pocket. */
	private static final Map<Long, Long> OWNER = new HashMap<>();

	private static @Nullable Block crude;

	private Pockets() {
	}

	/** Crude in the ground is black concrete. Looked up by id: 26.3 doesn't have a field for every coloured block. */
	static Block crudeBlock() {
		if (crude == null) {
			for (Block block : BuiltInRegistries.BLOCK) {
				if (BuiltInRegistries.BLOCK.getKey(block).getPath().equals("black_concrete")
					&& BuiltInRegistries.BLOCK.getKey(block).getNamespace().equals("minecraft")) {
					crude = block;
				}
			}
			if (crude == null) {
				crude = Blocks.COAL_BLOCK;
			}
		}
		return crude;
	}

	static boolean isCrudeBlock(BlockState state) {
		return state.is(crudeBlock());
	}

	static void clearCache() {
		CACHE.clear();
		HAS.clear();
	}

	static void reset() {
		clearCache();
		MADE.clear();
		OIL.clear();
		OPENED.clear();
		OWNER.clear();
	}

	/** Only the overworld has oil. Dinosaurs didn't live in the Nether. */
	static boolean hasOil(Level level) {
		return Level.OVERWORLD.equals(level.dimension());
	}

	private static long chunkKey(int cx, int cz) {
		return ((long) cx << 32) ^ (cz & 0xFFFFFFFFL);
	}

	/** The pocket in this chunk, if it has one. Opened pockets keep their shape even if the config changes. */
	static @Nullable Pocket inChunk(ServerLevel level, int cx, int cz) {
		long key = chunkKey(cx, cz);
		Pocket made = MADE.get(key);
		if (made != null) {
			return made;
		}
		Pocket opened = OPENED.get(key);
		if (opened != null) {
			return opened;
		}
		if (HAS.containsKey(key)) {
			return CACHE.get(key);
		}
		Pocket p = roll(level.getSeed(), cx, cz, key, level.getMinY());
		HAS.put(key, p != null);
		if (p != null) {
			CACHE.put(key, p);
		}
		if (HAS.size() > 65536) {
			HAS.clear();
		}
		return p;
	}

	private static @Nullable Pocket roll(long seed, int cx, int cz, long key, int minY) {
		FossilConfig c = FossilConfig.get();
		Random r = new Random(seed ^ (cx * 341873128712L) ^ (cz * 132897987541L) ^ 0x0F0551L);
		if (r.nextDouble() >= c.pocketChance) {
			return null;
		}
		boolean gusher = r.nextDouble() < c.gusherChance;
		int rx = gusher ? 4 + r.nextInt(2) : 2 + r.nextInt(3);
		int rz = gusher ? 4 + r.nextInt(2) : 2 + r.nextInt(3);
		int ry = gusher ? 3 : 1 + r.nextInt(2);
		int x = (cx << 4) + 3 + r.nextInt(10);
		int z = (cz << 4) + 3 + r.nextInt(10);
		int low = Math.max(c.pocketMinY, minY + 6 + ry);
		int y = low + r.nextInt(Math.max(1, c.pocketMaxY - low + 1));
		return new Pocket(key, x, y, z, rx, ry, rz, gusher);
	}

	/** Pockets whose area could reach this position (its chunk and the ones around it). */
	static List<Pocket> around(ServerLevel level, int x, int z) {
		List<Pocket> list = new ArrayList<>();
		if (!hasOil(level)) {
			return list;
		}
		int cx = x >> 4;
		int cz = z >> 4;
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				Pocket p = inChunk(level, cx + dx, cz + dz);
				if (p != null) {
					list.add(p);
				}
			}
		}
		return list;
	}

	static boolean isOpened(Pocket p) {
		return OPENED.containsKey(p.key());
	}

	/** Is this block crude we put there? Forgets it if someone swapped the block out. */
	static boolean isOil(ServerLevel level, BlockPos pos) {
		Long owner = OWNER.get(pos.asLong());
		if (owner == null || !level.isLoaded(pos)) {
			return false;
		}
		if (!isCrudeBlock(level.getBlockState(pos))) {
			forget(pos.asLong());
			return false;
		}
		return true;
	}

	static @Nullable Pocket pocketOf(BlockPos pos) {
		Long owner = OWNER.get(pos.asLong());
		return owner == null ? null : OPENED.get(owner);
	}

	static int left(Pocket p) {
		Set<Long> set = OIL.get(p.key());
		return set == null ? 0 : set.size();
	}

	private static void forget(long pos) {
		Long owner = OWNER.remove(pos);
		if (owner != null) {
			Set<Long> set = OIL.get(owner);
			if (set != null) {
				set.remove(pos);
			}
		}
		Store.changed();
	}

	/** Takes one bucket's worth out of the ground: the crude block turns into air. False if it wasn't oil. */
	static boolean drain(ServerLevel level, BlockPos pos) {
		if (!isOil(level, pos)) {
			return false;
		}
		forget(pos.asLong());
		level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
		return true;
	}

	/** The crude block in this pocket closest to the given spot, or null when it's dry. */
	static @Nullable BlockPos nearestOil(ServerLevel level, Pocket p, BlockPos from) {
		Set<Long> set = OIL.get(p.key());
		if (set == null || set.isEmpty()) {
			return null;
		}
		BlockPos best = null;
		double bestDist = Double.MAX_VALUE;
		List<Long> stale = new ArrayList<>();
		for (long l : set) {
			BlockPos pos = BlockPos.of(l);
			if (!level.isLoaded(pos)) {
				continue;
			}
			if (!isCrudeBlock(level.getBlockState(pos))) {
				stale.add(l);
				continue;
			}
			double d = pos.distSqr(from);
			if (d < bestDist) {
				bestDist = d;
				best = pos;
			}
		}
		for (long l : stale) {
			forget(l);
		}
		return best;
	}

	// ---------------------------------------------------------------- breaking in

	/** Rock that soaks up oil. Ores, fluids, and anything with contents are left alone. */
	private static boolean soaks(BlockState state) {
		if (state.isAir()) {
			return true;
		}
		if (state.hasBlockEntity() || !state.getFluidState().isEmpty()) {
			return false;
		}
		String id = BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
		return switch (id) {
			case "stone", "deepslate", "tuff", "granite", "diorite", "andesite", "dirt", "gravel", "calcite", "clay",
				"smooth_basalt", "sandstone", "sand", "red_sand", "terracotta", "cobblestone", "cobbled_deepslate" -> true;
			default -> false;
		};
	}

	/**
	 * Someone broke (or a rig drilled) a block here. If it's in or next to an unopened pocket, the pocket opens: its
	 * rock turns into crude. Returns the pocket that opened, or null.
	 */
	static @Nullable Pocket breach(ServerLevel level, BlockPos pos, @Nullable ServerPlayer by) {
		for (Pocket p : around(level, pos.getX(), pos.getZ())) {
			if (!isOpened(p) && p.near(pos.getX(), pos.getY(), pos.getZ())) {
				open(level, p, pos, by);
				return p;
			}
		}
		return null;
	}

	/** Turns the pocket's rock into crude. Skips the block that was just broken (it's gone). */
	static int open(ServerLevel level, Pocket p, @Nullable BlockPos broken, @Nullable ServerPlayer by) {
		Set<Long> set = new LinkedHashSet<>();
		for (int bx = p.x() - p.rx(); bx <= p.x() + p.rx(); bx++) {
			for (int bY = p.y() - p.ry(); bY <= p.y() + p.ry(); bY++) {
				for (int bz = p.z() - p.rz(); bz <= p.z() + p.rz(); bz++) {
					if (!p.contains(bx, bY, bz)) {
						continue;
					}
					BlockPos at = new BlockPos(bx, bY, bz);
					if (at.equals(broken) || !level.isLoaded(at) || !soaks(level.getBlockState(at))) {
						continue;
					}
					level.setBlock(at, crudeBlock().defaultBlockState(), 2);
					set.add(at.asLong());
					OWNER.put(at.asLong(), p.key());
				}
			}
		}
		OPENED.put(p.key(), p);
		OIL.put(p.key(), set);
		Store.changed();
		BlockPos c = p.center();
		Cmd.sound(level, "minecraft:block.bubble_column.upwards_ambient", c.getX() + 0.5, c.getY(), c.getZ() + 0.5, 2.0f, 0.5f);
		if (by != null) {
			by.sendSystemMessage(Component.literal("You broke into an oil pocket! ").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
				.append(Component.literal(set.size() + " buckets of crude. Right-click it with empty buckets.").withStyle(ChatFormatting.YELLOW)));
		}
		if (p.gusher()) {
			gush(level, p, by == null ? null : by.getName().getString());
		}
		return set.size();
	}

	/** A gusher: ten seconds of black spray out of the ground above it, and the whole server hears about it. */
	static void gush(ServerLevel level, Pocket p, @Nullable String who) {
		int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, p.x(), p.z());
		double x = p.x() + 0.5;
		double z = p.z() + 0.5;
		Cmd.sound(level, "minecraft:entity.generic.explode", x, top, z, 3.0f, 0.5f);
		for (int i = 0; i < 20; i++) {
			int t = i;
			FossilFoolMod.later(i * 10, () -> {
				Cmd.particles(level, "minecraft:squid_ink", x, top + 2 + (t % 4), z, 0.6, 0.25, 60);
				Cmd.particles(level, "minecraft:large_smoke", x, top + 4, z, 0.8, 0.05, 15);
				if (t % 4 == 0) {
					Cmd.sound(level, "minecraft:block.bubble_column.whirlpool_ambient", x, top, z, 2.0f, 0.6f);
				}
			});
		}
		String text = (who == null ? "A drill rig" : who) + " struck a GUSHER near " + p.x() + ", " + p.z() + "! Black gold, Texas tea!";
		level.getServer().getPlayerList().broadcastSystemMessage(Component.literal("🛢 " + text).withStyle(ChatFormatting.GOLD), false);
	}

	/** For the smoke test and admins: makes a pocket right here and opens it. Returns how many buckets it holds. */
	static Pocket create(ServerLevel level, BlockPos center, int rx, int ry, int rz) {
		long key = chunkKey(center.getX() >> 4, center.getZ() >> 4);
		Pocket p = new Pocket(key, center.getX(), center.getY(), center.getZ(), rx, ry, rz, false);
		MADE.put(key, p);
		OPENED.remove(key);
		Set<Long> old = OIL.remove(key);
		if (old != null) {
			old.forEach(OWNER::remove);
		}
		return p;
	}

	/** Puts back opened pockets read from disk. */
	static void restore(Pocket p, Set<Long> oil) {
		OPENED.put(p.key(), p);
		OIL.put(p.key(), oil);
		for (long l : oil) {
			OWNER.put(l, p.key());
		}
	}

	// ---------------------------------------------------------------- dowsing

	/** What the rod feels: the nearest pocket that still has oil in it, within range. */
	record Reading(Pocket pocket, double distance, int depth) {
	}

	static @Nullable Reading dowse(ServerLevel level, BlockPos from, int range) {
		if (!hasOil(level)) {
			return null;
		}
		int chunks = (range >> 4) + 1;
		Reading best = null;
		for (int dx = -chunks; dx <= chunks; dx++) {
			for (int dz = -chunks; dz <= chunks; dz++) {
				Pocket p = inChunk(level, (from.getX() >> 4) + dx, (from.getZ() >> 4) + dz);
				if (p == null || (isOpened(p) && left(p) == 0)) {
					continue;
				}
				double ddx = p.x() + 0.5 - (from.getX() + 0.5);
				double ddz = p.z() + 0.5 - (from.getZ() + 0.5);
				double dist = Math.sqrt(ddx * ddx + ddz * ddz);
				if (dist <= range && (best == null || dist < best.distance())) {
					best = new Reading(p, dist, from.getY() - p.y());
				}
			}
		}
		return best;
	}

	/** "north-east" etc. from one spot to another. */
	static String direction(BlockPos from, int x, int z) {
		double angle = Math.toDegrees(Math.atan2(x - from.getX(), -(z - from.getZ())));
		String[] names = {"north", "north-east", "east", "south-east", "south", "south-west", "west", "north-west"};
		int i = (int) Math.round(((angle % 360) + 360) % 360 / 45.0) % 8;
		return names[i];
	}
}
