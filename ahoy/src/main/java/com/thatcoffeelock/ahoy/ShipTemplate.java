package com.thatcoffeelock.ahoy;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The default ship: a two-masted brig about 7 wide and 20 long.
 *
 * Local coordinates: x across (+x is port, the left side when facing the bow), y up with 0 being the
 * top water layer, z along the ship with +z the bow. Everything at y <= 0 sits in the water. Air
 * entries at y <= 0 are the dry inside of the hull (the hold), which has to be kept dry when anchoring.
 */
public final class ShipTemplate {
	/** One block of the ship. */
	public record ShipBlock(int x, int y, int z, BlockState state) {
		public static final Codec<ShipBlock> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.INT.fieldOf("x").forGetter(ShipBlock::x),
			Codec.INT.fieldOf("y").forGetter(ShipBlock::y),
			Codec.INT.fieldOf("z").forGetter(ShipBlock::z),
			BlockState.CODEC.fieldOf("state").forGetter(ShipBlock::state)
		).apply(i, ShipBlock::new));

		public BlockPos local() {
			return new BlockPos(x, y, z);
		}
	}

	/** A spot where someone can sit while sailing. y is the seat height above the waterline. */
	public record Spot(String name, double x, double y, double z) {
	}

	/** Where the captain stands (behind the wheel). Always spot 0. */
	public static final Spot CAPTAIN = new Spot("Captain", 0, 2.45, -7);
	public static final BlockPos HELM = new BlockPos(0, 2, -6);
	public static final List<Spot> SPOTS = List.of(
		CAPTAIN,
		new Spot("Port bench", 2, 2.5, -3), new Spot("Starboard bench", -2, 2.5, -3),
		new Spot("Port bench", 2, 2.5, -1), new Spot("Starboard bench", -2, 2.5, -1),
		new Spot("Port bench", 2, 2.5, 1), new Spot("Starboard bench", -2, 2.5, 1),
		new Spot("Bow (port)", 1, 2.45, 6), new Spot("Bow (starboard)", -1, 2.45, 6));

	/** Local bounds used for collisions, boarding checks and for picking up things built on deck. */
	public static final int MIN_X = -6, MAX_X = 6, MIN_Y = -3, MAX_Y = 16, MIN_Z = -12, MAX_Z = 14;

	private ShipTemplate() {
	}

	/** Hull half-width at a given z. */
	private static int hw(int z) {
		if (z <= -8) {
			return 2;
		}
		if (z <= 5) {
			return 3;
		}
		if (z <= 7) {
			return 2;
		}
		return z == 8 ? 1 : 0;
	}

	private static final Map<String, BlockState> STATES = new java.util.HashMap<>();

	/** Block state from a string like "minecraft:spruce_stairs[facing=east]". */
	static BlockState state(String text) {
		return STATES.computeIfAbsent(text, t -> {
			try {
				return BlockStateParser.parseForBlock(BuiltInRegistries.BLOCK, t, false).blockState();
			} catch (Exception e) {
				AhoyMod.LOG.error("Bad block state in ship template: {}", t, e);
				Block block = BuiltInRegistries.BLOCK.getValue(Identifier.parse(t.replaceAll("\\[.*", "")));
				return block.defaultBlockState();
			}
		});
	}

	public static List<ShipBlock> blocks() {
		Map<BlockPos, BlockState> m = new LinkedHashMap<>();
		BlockState air = Blocks.AIR.defaultBlockState();
		BlockState hull = state("minecraft:dark_oak_planks");
		BlockState deck = state("minecraft:spruce_planks");
		BlockState rail = state("minecraft:spruce_fence");

		for (int z = -8; z <= 9; z++) {
			int w = hw(z);
			boolean end = z == -8 || z == 9;
			for (int x = -w; x <= w; x++) {
				boolean side = Math.abs(x) == w;
				// keel / bottom
				if (Math.abs(x) <= w - 1) {
					m.put(new BlockPos(x, -3, z), hull);
				}
				// hold floor
				m.put(new BlockPos(x, -2, z), side || end ? hull : deck);
				// hold walls, dry inside
				for (int y = -1; y <= 0; y++) {
					m.put(new BlockPos(x, y, z), side || end ? hull : air);
				}
				// deck and gunwale
				m.put(new BlockPos(x, 1, z), side || end ? hull : deck);
				// railing
				if (side || end) {
					m.put(new BlockPos(x, 2, z), rail);
				}
			}
		}

		// hatch and ladder down to the hold (starboard aft)
		m.put(new BlockPos(2, 1, -6), state("minecraft:spruce_trapdoor[facing=west,half=top,open=false]"));
		m.put(new BlockPos(2, 0, -6), state("minecraft:ladder[facing=west]"));
		m.put(new BlockPos(2, -1, -6), state("minecraft:ladder[facing=west]"));

		// the hold: cargo barrels down both sides, two bunks in the bow, a workbench and a stove aft
		for (int z = -4; z <= 3; z++) {
			m.put(new BlockPos(2, -1, z), state("minecraft:barrel[facing=up]"));
			m.put(new BlockPos(-2, -1, z), state("minecraft:barrel[facing=up]"));
		}
		m.put(new BlockPos(1, -1, 4), state("minecraft:red_bed[facing=south,part=foot]"));
		m.put(new BlockPos(1, -1, 5), state("minecraft:red_bed[facing=south,part=head]"));
		m.put(new BlockPos(-1, -1, 4), state("minecraft:blue_bed[facing=south,part=foot]"));
		m.put(new BlockPos(-1, -1, 5), state("minecraft:blue_bed[facing=south,part=head]"));
		m.put(new BlockPos(-2, -1, -6), state("minecraft:crafting_table"));
		m.put(new BlockPos(-2, -1, -5), state("minecraft:furnace[facing=east]"));
		m.put(new BlockPos(0, 0, -2), state("minecraft:lantern[hanging=true]"));
		m.put(new BlockPos(0, 0, 3), state("minecraft:lantern[hanging=true]"));

		// benches on deck
		for (int z : new int[] {-3, -1, 1}) {
			m.put(new BlockPos(2, 2, z), state("minecraft:spruce_stairs[facing=east,half=bottom]"));
			m.put(new BlockPos(-2, 2, z), state("minecraft:spruce_stairs[facing=west,half=bottom]"));
		}

		// the wheel (a grindstone, the classic builder's trick) and lanterns
		m.put(HELM, state("minecraft:grindstone[face=floor,facing=north]"));
		m.put(new BlockPos(3, 3, -7), state("minecraft:lantern[hanging=false]"));
		m.put(new BlockPos(-3, 3, -7), state("minecraft:lantern[hanging=false]"));
		m.put(new BlockPos(1, 3, 8), state("minecraft:lantern[hanging=false]"));
		m.put(new BlockPos(-1, 3, 8), state("minecraft:lantern[hanging=false]"));

		// main mast, yard, sail and a pennant
		for (int y = 2; y <= 10; y++) {
			m.put(new BlockPos(0, y, 2), state("minecraft:spruce_log[axis=y]"));
		}
		for (int x = -3; x <= 3; x++) {
			m.put(new BlockPos(x, 9, 3), state("minecraft:stripped_spruce_log[axis=x]"));
			for (int y = 4; y <= 8; y++) {
				m.put(new BlockPos(x, y, 3), state("minecraft:white_wool"));
			}
		}
		m.put(new BlockPos(0, 11, 2), state("minecraft:red_wool"));
		m.put(new BlockPos(0, 11, 3), state("minecraft:red_wool"));

		// mizzen mast and sail
		for (int y = 2; y <= 8; y++) {
			m.put(new BlockPos(0, y, -4), state("minecraft:spruce_log[axis=y]"));
		}
		for (int x = -2; x <= 2; x++) {
			m.put(new BlockPos(x, 8, -3), state("minecraft:stripped_spruce_log[axis=x]"));
			for (int y = 4; y <= 7; y++) {
				m.put(new BlockPos(x, y, -3), state("minecraft:white_wool"));
			}
		}

		// bowsprit
		for (int z = 9; z <= 11; z++) {
			m.put(new BlockPos(0, 2, z), state("minecraft:stripped_spruce_log[axis=z]"));
		}

		List<ShipBlock> blocks = new ArrayList<>();
		m.forEach((pos, s) -> blocks.add(new ShipBlock(pos.getX(), pos.getY(), pos.getZ(), s)));
		return blocks;
	}
}
