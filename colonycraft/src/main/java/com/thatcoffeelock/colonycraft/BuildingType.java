package com.thatcoffeelock.colonycraft;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * Every building you can buy, with its price, crew, housing and prefab design.
 *
 * Designs use local coordinates: x across, z front-to-back with the door at -z (facing whoever
 * placed it), y up with 0 being the floor, which replaces the ground layer.
 */
public enum BuildingType {
	TOWN_HALL("town_hall", "Town Hall", Items.BELL, 2500, 4, 9, new int[] {1, 1, 1}, new int[] {2, 3, 4}, BuildingType::townHall),
	RESIDENCE("residence", "Residence", Items.OAK_DOOR, 400, 3, 6, new int[] {0, 0, 0}, new int[] {4, 6, 8}, BuildingType::residence),
	FARM("farm", "Farm", Items.WHEAT, 800, 4, 3, new int[] {2, 3, 4}, new int[] {0, 0, 0}, BuildingType::farm),
	LUMBER_CAMP("lumber_camp", "Lumber Camp", Items.IRON_AXE, 900, 4, 6, new int[] {2, 3, 4}, new int[] {0, 0, 0}, BuildingType::lumberCamp),
	MINE("mine", "Mine", Items.IRON_PICKAXE, 2500, 3, 5, new int[] {3, 4, 5}, new int[] {0, 0, 0}, BuildingType::mine),
	WORKSHOP("workshop", "Workshop", Items.CRAFTING_TABLE, 1500, 3, 5, new int[] {2, 3, 4}, new int[] {0, 0, 0}, BuildingType::workshop),
	STOREHOUSE("storehouse", "Storehouse", Items.BARREL, 500, 3, 6, new int[] {1, 1, 2}, new int[] {0, 0, 0}, BuildingType::storehouse);

	public static final int MAX_TIER = 3;

	public final String id;
	public final String displayName;
	public final Item icon;
	/** Price in ₥ of the blueprint (tier 1). */
	public final double price;
	/** Footprint is (2 * half + 1) square. */
	public final int half;
	public final int height;
	private final int[] workers;
	private final int[] housing;
	private final Consumer<Plan> design;

	BuildingType(String id, String displayName, Item icon, double price, int half, int height, int[] workers, int[] housing, Consumer<Plan> design) {
		this.id = id;
		this.displayName = displayName;
		this.icon = icon;
		this.price = price;
		this.half = half;
		this.height = height;
		this.workers = workers;
		this.housing = housing;
		this.design = design;
	}

	public int workers(int tier) {
		return workers[Math.max(1, Math.min(MAX_TIER, tier)) - 1];
	}

	public int housing(int tier) {
		return housing[Math.max(1, Math.min(MAX_TIER, tier)) - 1];
	}

	/** Price in ₥ to go from this tier to the next. */
	public double upgradePrice(int currentTier) {
		return currentTier == 1 ? price * 1.5 : price * 3;
	}

	public static @Nullable BuildingType byId(String id) {
		for (BuildingType type : values()) {
			if (type.id.equals(id)) {
				return type;
			}
		}
		return null;
	}

	/** Where the villagers stand when they're hired, and where they get sent back to. */
	public BlockPos home() {
		return switch (this) {
			case FARM -> new BlockPos(1, 1, 0);
			case LUMBER_CAMP -> new BlockPos(0, 1, 1);
			case RESIDENCE, MINE, WORKSHOP -> new BlockPos(0, 1, -1);
			default -> new BlockPos(0, 1, 0);
		};
	}

	/** The Town Hall's service counter (right-click to open the Town Hall). */
	public static final BlockPos COUNTER = new BlockPos(0, 1, 2);

	public Map<BlockPos, BlockState> plan() {
		Plan plan = new Plan();
		design.accept(plan);
		return plan.blocks;
	}

	// ---------------------------------------------------------------- building helper

	static final class Plan {
		final Map<BlockPos, BlockState> blocks = new LinkedHashMap<>();

		void set(int x, int y, int z, String state) {
			blocks.put(new BlockPos(x, y, z), state(state));
		}

		void fill(int x1, int y1, int z1, int x2, int y2, int z2, String state) {
			for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++) {
				for (int y = Math.min(y1, y2); y <= Math.max(y1, y2); y++) {
					for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++) {
						set(x, y, z, state);
					}
				}
			}
		}

		/** Four walls around a square of the given half-size, logs in the corners. */
		void walls(int h, int y1, int y2, String wall, String corner) {
			for (int y = y1; y <= y2; y++) {
				for (int i = -h; i <= h; i++) {
					set(i, y, -h, wall);
					set(i, y, h, wall);
					set(-h, y, i, wall);
					set(h, y, i, wall);
				}
				for (int[] c : new int[][] {{-h, -h}, {h, -h}, {-h, h}, {h, h}}) {
					set(c[0], y, c[1], corner);
				}
			}
		}

		void door(int z, String wood) {
			set(0, 1, z, "minecraft:" + wood + "_door[facing=south,half=lower,hinge=left,open=false]");
			set(0, 2, z, "minecraft:" + wood + "_door[facing=south,half=upper,hinge=left,open=false]");
		}
	}

	private static final Map<String, BlockState> STATES = new HashMap<>();

	/** Block state from text like "minecraft:oak_stairs[facing=east]". */
	static BlockState state(String text) {
		return STATES.computeIfAbsent(text, t -> {
			try {
				return BlockStateParser.parseForBlock(BuiltInRegistries.BLOCK, t, false).blockState();
			} catch (Exception e) {
				ColonycraftMod.LOG.error("Bad block state in a building design: {}", t, e);
				return Blocks.OAK_PLANKS.defaultBlockState();
			}
		});
	}

	// ---------------------------------------------------------------- the designs

	private static void townHall(Plan p) {
		p.fill(-4, 0, -4, 4, 0, 4, "minecraft:stone_bricks");
		p.walls(4, 1, 4, "minecraft:oak_planks", "minecraft:oak_log[axis=y]");
		p.fill(-4, 1, -4, 4, 1, -4, "minecraft:stone_bricks");
		p.fill(-4, 1, 4, 4, 1, 4, "minecraft:stone_bricks");
		for (int i : new int[] {-2, 2}) {
			p.fill(i, 2, -4, i, 3, -4, "minecraft:glass_pane");
			p.fill(i, 2, 4, i, 3, 4, "minecraft:glass_pane");
			p.fill(-4, 2, i, -4, 3, i, "minecraft:glass_pane");
			p.fill(4, 2, i, 4, 3, i, "minecraft:glass_pane");
		}
		p.door(-4, "oak");
		// roof in three steps and a bell tower
		p.fill(-4, 5, -4, 4, 5, 4, "minecraft:spruce_planks");
		p.fill(-3, 6, -3, 3, 6, 3, "minecraft:spruce_planks");
		p.fill(-2, 7, -2, 2, 7, 2, "minecraft:spruce_slab[type=bottom]");
		p.set(0, 7, 0, "minecraft:spruce_planks");
		p.set(0, 8, 0, "minecraft:bell[attachment=floor,facing=north]");
		// inside: red carpet to the counter, bookshelves, lanterns
		p.fill(-1, 1, -3, 1, 1, 1, "minecraft:red_carpet");
		p.set(COUNTER.getX(), COUNTER.getY(), COUNTER.getZ(), "minecraft:lectern[facing=north]");
		p.fill(-3, 1, 3, -2, 3, 3, "minecraft:bookshelf");
		p.fill(2, 1, 3, 3, 3, 3, "minecraft:bookshelf");
		p.set(-2, 4, 0, "minecraft:lantern[hanging=true]");
		p.set(2, 4, 0, "minecraft:lantern[hanging=true]");
	}

	private static void residence(Plan p) {
		p.fill(-3, 0, -3, 3, 0, 3, "minecraft:oak_planks");
		p.walls(3, 1, 1, "minecraft:cobblestone", "minecraft:oak_log[axis=y]");
		p.walls(3, 2, 3, "minecraft:oak_planks", "minecraft:oak_log[axis=y]");
		p.set(-3, 2, 0, "minecraft:glass_pane");
		p.set(3, 2, 0, "minecraft:glass_pane");
		p.set(0, 2, 3, "minecraft:glass_pane");
		p.door(-3, "oak");
		p.fill(-3, 4, -3, 3, 4, 3, "minecraft:spruce_planks");
		p.fill(-2, 5, -2, 2, 5, 2, "minecraft:spruce_slab[type=bottom]");
		String[] colours = {"red", "blue", "green", "yellow"};
		int[] xs = {-2, -1, 1, 2};
		for (int i = 0; i < 4; i++) {
			p.set(xs[i], 1, 1, "minecraft:" + colours[i] + "_bed[facing=south,part=foot]");
			p.set(xs[i], 1, 2, "minecraft:" + colours[i] + "_bed[facing=south,part=head]");
		}
		p.set(0, 3, 0, "minecraft:lantern[hanging=true]");
	}

	private static void farm(Plan p) {
		p.fill(-4, 0, -4, 4, 0, 4, "minecraft:coarse_dirt");
		p.fill(-3, 0, -3, 3, 0, 3, "minecraft:farmland[moisture=7]");
		p.fill(0, 0, -3, 0, 0, 3, "minecraft:water");
		for (int i = -4; i <= 4; i++) {
			p.set(i, 1, -4, "minecraft:oak_fence");
			p.set(i, 1, 4, "minecraft:oak_fence");
			p.set(-4, 1, i, "minecraft:oak_fence");
			p.set(4, 1, i, "minecraft:oak_fence");
		}
		p.set(0, 1, -4, "minecraft:oak_fence_gate[facing=south,open=false]");
		p.fill(-3, 1, -3, -1, 1, 3, "minecraft:wheat[age=7]");
		p.fill(1, 1, -3, 2, 1, 3, "minecraft:carrots[age=7]");
		p.fill(3, 1, -3, 3, 1, 3, "minecraft:potatoes[age=7]");
		p.set(3, 1, 3, "minecraft:composter[level=4]");
		p.set(-3, 1, 3, "minecraft:hay_block");
		p.set(-3, 2, 3, "minecraft:carved_pumpkin[facing=north]");
	}

	private static void lumberCamp(Plan p) {
		p.fill(-4, 0, -4, 4, 0, 4, "minecraft:coarse_dirt");
		p.fill(-4, 0, 2, -2, 0, 4, "minecraft:podzol");
		// a lean-to shed in the back corner
		for (int[] c : new int[][] {{-4, 2}, {-2, 2}, {-4, 4}, {-2, 4}}) {
			p.fill(c[0], 1, c[1], c[0], 2, c[1], "minecraft:spruce_log[axis=y]");
		}
		p.fill(-4, 3, 2, -2, 3, 4, "minecraft:spruce_slab[type=bottom]");
		p.fill(-4, 1, 4, -2, 2, 4, "minecraft:spruce_planks");
		// log piles
		p.fill(1, 1, 3, 3, 1, 4, "minecraft:stripped_oak_log[axis=x]");
		p.fill(1, 2, 3, 3, 2, 3, "minecraft:stripped_oak_log[axis=x]");
		p.set(1, 1, 0, "minecraft:oak_log[axis=y]");
		// two trees that never get chopped (the real ones are somewhere else, trust us)
		tree(p, -2, -2, "oak");
		tree(p, 2, -2, "birch");
		p.set(3, 1, 1, "minecraft:oak_sapling");
		p.set(0, 1, -3, "minecraft:birch_sapling");
	}

	private static void tree(Plan p, int x, int z, String wood) {
		p.fill(x - 1, 3, z - 1, x + 1, 4, z + 1, "minecraft:" + wood + "_leaves[persistent=true]");
		p.set(x, 5, z, "minecraft:" + wood + "_leaves[persistent=true]");
		p.fill(x, 1, z, x, 4, z, "minecraft:" + wood + "_log[axis=y]");
	}

	private static void mine(Plan p) {
		p.fill(-3, 0, -3, 3, 0, 3, "minecraft:cobblestone");
		p.set(-2, 0, -2, "minecraft:gravel");
		p.set(1, 0, -1, "minecraft:gravel");
		// a rocky hill with a timbered tunnel
		p.fill(-3, 1, 1, 3, 3, 3, "minecraft:stone");
		p.fill(-2, 4, 2, 2, 4, 3, "minecraft:stone");
		p.fill(0, 1, 1, 0, 2, 3, "minecraft:air");
		p.fill(-1, 1, 1, -1, 2, 1, "minecraft:oak_log[axis=y]");
		p.fill(1, 1, 1, 1, 2, 1, "minecraft:oak_log[axis=y]");
		p.fill(-1, 3, 1, 1, 3, 1, "minecraft:oak_planks");
		p.set(-2, 2, 1, "minecraft:coal_ore");
		p.set(2, 1, 1, "minecraft:iron_ore");
		p.set(-3, 1, 1, "minecraft:copper_ore");
		p.set(3, 2, 2, "minecraft:gold_ore");
		p.fill(0, 1, -3, 0, 1, 3, "minecraft:rail[shape=north_south]");
		p.set(2, 1, -2, "minecraft:blast_furnace[facing=north]");
		p.set(-2, 1, -2, "minecraft:lantern[hanging=false]");
		p.set(-3, 1, -1, "minecraft:coal_block");
		p.set(-2, 1, -1, "minecraft:cobblestone");
	}

	private static void workshop(Plan p) {
		p.fill(-3, 0, -3, 3, 0, 3, "minecraft:spruce_planks");
		p.walls(3, 1, 3, "minecraft:spruce_planks", "minecraft:spruce_log[axis=y]");
		p.fill(-2, 1, -3, 2, 3, -3, "minecraft:air"); // open front
		p.fill(-3, 4, -3, 3, 4, 3, "minecraft:spruce_slab[type=bottom]");
		p.set(-2, 1, 2, "minecraft:crafting_table");
		p.set(-1, 1, 2, "minecraft:smithing_table");
		p.set(0, 1, 2, "minecraft:anvil[facing=north]");
		p.set(1, 1, 2, "minecraft:furnace[facing=north]");
		p.set(2, 1, 2, "minecraft:grindstone[face=floor,facing=north]");
		p.set(0, 3, 0, "minecraft:lantern[hanging=true]");
	}

	private static void storehouse(Plan p) {
		p.fill(-3, 0, -3, 3, 0, 3, "minecraft:stone_bricks");
		p.walls(3, 1, 3, "minecraft:barrel[facing=up]", "minecraft:spruce_log[axis=y]");
		p.door(-3, "spruce");
		p.fill(-2, 1, 2, -2, 2, 2, "minecraft:barrel[facing=up]");
		p.fill(2, 1, 2, 2, 2, 2, "minecraft:barrel[facing=up]");
		p.set(-2, 1, 1, "minecraft:hay_block");
		p.set(2, 1, 1, "minecraft:spruce_planks");
		p.fill(-3, 4, -3, 3, 4, 3, "minecraft:spruce_planks");
		p.fill(-2, 5, -2, 2, 5, 2, "minecraft:spruce_slab[type=bottom]");
		p.set(0, 3, 0, "minecraft:lantern[hanging=true]");
	}
}
