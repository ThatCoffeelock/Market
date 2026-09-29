package com.thatcoffeelock.market;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * Wealth decorations built out of vanilla display entities, so they render on unmodded clients.
 * Each one sits on an invisible barrier block (so you can walk on it and click it).
 */
public final class Vanity {
	private static final String TAG = "market_vanity";

	/** One piece of a decoration. Coordinates are relative to the bottom-centre of the block. */
	private record Part(String kind, String id, float x, float y, float z, float sx, float sy, float sz) {
	}

	public enum Type {
		CASH_STACK("cash_stack", "Stack of Cash", "A couple of fat bundles of bills. Starter wealth.", "minecraft:paper", 5_000),
		GOLD_STACK("gold_stack", "Gold Bar Stack", "A neat little pyramid of solid gold bars.", "minecraft:gold_ingot", 25_000),
		CASH_PALLET("cash_pallet", "Pallet of Cash", "A shrink-wrapped pallet of bills. Very subtle.", "minecraft:green_dye", 50_000),
		GOLD_PALLET("gold_pallet", "Pallet of Gold", "Forty gold bars on a pallet. Fort Knox who?", "minecraft:gold_block", 150_000),
		TROPHY("trophy", "Tycoon Trophy", "A golden trophy with your name floating above it.", "minecraft:bell", 250_000);

		public final String id;
		public final String displayName;
		public final String description;
		private final String iconId;
		public final double defaultPrice;

		Type(String id, String displayName, String description, String iconId, double defaultPrice) {
			this.id = id;
			this.displayName = displayName;
			this.description = description;
			this.iconId = iconId;
			this.defaultPrice = defaultPrice;
		}

		public Item icon() {
			return PriceBook.item(iconId, Items.PAPER);
		}

		/** Price in cents (configurable in market.json -> vanityPrices). */
		public long price() {
			return Money.fromDecimal(MarketConfig.get().vanityPrice(id, defaultPrice));
		}

		public static @Nullable Type byId(String id) {
			for (Type type : values()) {
				if (type.id.equals(id)) {
					return type;
				}
			}
			return null;
		}
	}

	private Vanity() {
	}

	// ---------------------------------------------------------------- placing & picking up

	public static void tryPlace(ServerPlayer player, ServerLevel level, BlockHitResult hit, InteractionHand hand, ItemStack held, Type type) {
		BlockPos pos = level.getBlockState(hit.getBlockPos()).canBeReplaced() ? hit.getBlockPos() : hit.getBlockPos().relative(hit.getDirection());
		if (!level.getBlockState(pos).canBeReplaced() || MarketData.vanityAt(level, pos) != null) {
			player.sendSystemMessage(Component.literal("There's no room for your " + type.displayName + " there.").withStyle(ChatFormatting.RED));
			return;
		}
		float yaw = Math.round(player.getYRot() / 90f) * 90f + 180f;
		place(level, pos, type, yaw, player.getUUID().toString(), player.getName().getString());
		if (!player.isCreative()) {
			held.shrink(1);
		}
		player.sendSystemMessage(Component.literal("Placed your " + type.displayName + ". Sneak + right-click it with an empty hand to pick it up.")
			.withStyle(ChatFormatting.GOLD));
	}

	/** Builds the decoration. Returns the entity tag used to find its parts later. */
	public static String place(ServerLevel level, BlockPos pos, Type type, float yaw, String ownerUuid, String ownerName) {
		String groupTag = "mkv_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
		level.setBlockAndUpdate(pos, Blocks.BARRIER.defaultBlockState());
		double cx = pos.getX() + 0.5;
		double cy = pos.getY();
		double cz = pos.getZ() + 0.5;
		for (Part part : parts(type, ownerName)) {
			run(level, summon(part, cx, cy, cz, yaw, groupTag));
		}
		MarketData.VanityRecord record = new MarketData.VanityRecord();
		record.type = type.id;
		record.owner = ownerUuid;
		record.ownerName = ownerName;
		record.tag = groupTag;
		MarketData.putVanity(level, pos, record);
		return groupTag;
	}

	public static void interact(ServerPlayer player, ServerLevel level, BlockPos pos, MarketData.VanityRecord record) {
		Type type = Type.byId(record.type);
		String name = type == null ? "decoration" : type.displayName;
		boolean owner = player.getUUID().toString().equals(record.owner);
		if (player.isShiftKeyDown() && player.getMainHandItem().isEmpty()) {
			if (!owner && !player.isCreative()) {
				player.sendSystemMessage(Component.literal("Hands off! That belongs to " + record.ownerName + ".").withStyle(ChatFormatting.RED));
				return;
			}
			remove(level, pos, record);
			if (type != null) {
				MarketItems.give(player, MarketItems.vanity(type));
			}
			player.sendSystemMessage(Component.literal("Packed up your " + name + ".").withStyle(ChatFormatting.GOLD));
			return;
		}
		Component worth = type == null ? Component.empty()
			: Component.literal(" (worth ").withStyle(ChatFormatting.GRAY).append(Money.text(type.price())).append(Component.literal(")").withStyle(ChatFormatting.GRAY));
		player.sendSystemMessage(Component.literal(name).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
			.append(Component.literal(" owned by " + record.ownerName).withStyle(ChatFormatting.YELLOW))
			.append(worth));
	}

	public static void remove(ServerLevel level, BlockPos pos, MarketData.VanityRecord record) {
		run(level, "kill @e[type=!minecraft:player,tag=" + record.tag + "]");
		if (level.getBlockState(pos).is(Blocks.BARRIER)) {
			level.removeBlock(pos, false);
		}
		MarketData.removeVanity(level, pos);
	}

	static void run(ServerLevel level, String command) {
		MinecraftServer server = level.getServer();
		CommandSourceStack source = server.createCommandSourceStack().withLevel(level).withSuppressedOutput();
		server.getCommands().performPrefixedCommand(source, command);
	}

	private static String f(double v) {
		return String.format(Locale.ROOT, "%.4f", v);
	}

	private static String summon(Part part, double cx, double cy, double cz, float yaw, String groupTag) {
		String common = "Tags:[\"" + TAG + "\",\"" + groupTag + "\"],Rotation:[" + f(yaw) + "f,0f]";
		if (part.kind.equals("text")) {
			return "summon minecraft:text_display " + f(cx) + " " + f(cy + part.y) + " " + f(cz) + " {" + common
				+ ",billboard:\"center\",background:0,shadow:1b,text:" + part.id + "}";
		}
		String transform = ",transformation:{left_rotation:[0f,0f,0f,1f],right_rotation:[0f,0f,0f,1f],translation:["
			+ f(part.x) + "f," + f(part.y) + "f," + f(part.z) + "f],scale:[" + f(part.sx) + "f," + f(part.sy) + "f," + f(part.sz) + "f]}";
		return "summon minecraft:block_display " + f(cx) + " " + f(cy) + " " + f(cz) + " {" + common
			+ ",block_state:\"" + part.id + "\"" + transform + "}";
	}

	// ---------------------------------------------------------------- the actual art

	private static final String CASH = "minecraft:lime_terracotta";
	private static final String BAND = "minecraft:white_concrete";
	private static final String GOLD = "minecraft:gold_block";

	private static Part block(String id, float x, float y, float z, float sx, float sy, float sz) {
		return new Part("block", id, x, y, z, sx, sy, sz);
	}

	private static List<Part> parts(Type type, String ownerName) {
		List<Part> parts = new ArrayList<>();
		switch (type) {
			case CASH_STACK -> {
				// two piles of bundles, slightly messy
				float[][] piles = {{-0.42f, -0.30f, 5}, {0.02f, -0.05f, 3}};
				for (float[] pile : piles) {
					for (int i = 0; i < (int) pile[2]; i++) {
						float jitter = (i % 2 == 0) ? 0f : 0.02f;
						bundle(parts, pile[0] + jitter, i * 0.09f, pile[1] - jitter, 0.40f, 0.085f, 0.20f);
					}
				}
			}
			case CASH_PALLET -> {
				pallet(parts);
				// 2x2 columns of shrink-wrapped cash cubes, 4 layers high
				for (int layer = 0; layer < 4; layer++) {
					for (int cx = 0; cx < 2; cx++) {
						for (int cz = 0; cz < 2; cz++) {
							parts.add(block(CASH, -0.47f + cx * 0.48f, 0.12f + layer * 0.17f, -0.47f + cz * 0.48f, 0.46f, 0.16f, 0.46f));
						}
					}
				}
				for (int cx = 0; cx < 2; cx++) {
					for (int cz = 0; cz < 2; cz++) {
						// paper band running up each column
						parts.add(block(BAND, -0.47f + cx * 0.48f + 0.20f, 0.12f, -0.472f + cz * 0.48f, 0.06f, 4 * 0.17f - 0.01f, 0.464f));
					}
				}
			}
			case GOLD_STACK -> {
				float[] xs = {-0.36f, 0.02f};
				for (float x : xs) {
					for (float z : new float[] {-0.26f, -0.08f, 0.10f}) {
						parts.add(block(GOLD, x, 0f, z, 0.34f, 0.10f, 0.16f));
					}
					for (float z : new float[] {-0.17f, 0.01f}) {
						parts.add(block(GOLD, x, 0.10f, z, 0.34f, 0.10f, 0.16f));
					}
				}
				parts.add(block(GOLD, -0.17f, 0.20f, -0.08f, 0.34f, 0.10f, 0.16f));
			}
			case GOLD_PALLET -> {
				pallet(parts);
				for (int layer = 0; layer < 4; layer++) {
					float y = 0.12f + layer * 0.105f;
					for (int a = 0; a < 2; a++) {
						for (int b = 0; b < 5; b++) {
							if (layer % 2 == 0) {
								parts.add(block(GOLD, -0.47f + a * 0.48f, y, -0.47f + b * 0.19f, 0.46f, 0.10f, 0.18f));
							} else {
								parts.add(block(GOLD, -0.47f + b * 0.19f, y, -0.47f + a * 0.48f, 0.18f, 0.10f, 0.46f));
							}
						}
					}
				}
			}
			case TROPHY -> {
				parts.add(block("minecraft:polished_blackstone", -0.25f, 0f, -0.25f, 0.5f, 0.12f, 0.5f));
				parts.add(block(GOLD, -0.18f, 0.12f, -0.18f, 0.36f, 0.05f, 0.36f));
				parts.add(block(GOLD, -0.05f, 0.17f, -0.05f, 0.10f, 0.28f, 0.10f));
				parts.add(block(GOLD, -0.20f, 0.45f, -0.20f, 0.40f, 0.30f, 0.40f));
				parts.add(block(GOLD, -0.29f, 0.52f, -0.04f, 0.09f, 0.18f, 0.08f));
				parts.add(block(GOLD, 0.20f, 0.52f, -0.04f, 0.09f, 0.18f, 0.08f));
				parts.add(block("minecraft:emerald_block", -0.08f, 0.60f, -0.205f, 0.16f, 0.12f, 0.01f));
				String safeName = ownerName.replaceAll("[^A-Za-z0-9_]", "");
				parts.add(new Part("text", "{text:\"" + safeName + "'s Fortune\",color:\"gold\",bold:true}", 0, 1.15f, 0, 1, 1, 1));
				parts.add(new Part("text", "{text:\"certified rich\",color:\"yellow\",italic:true}", 0, 0.95f, 0, 1, 1, 1));
			}
		}
		return parts;
	}

	private static void pallet(List<Part> parts) {
		for (float z : new float[] {-0.5f, -0.05f, 0.4f}) {
			parts.add(block("minecraft:stripped_spruce_wood", -0.5f, 0f, z, 1f, 0.08f, 0.1f));
		}
		for (int i = 0; i < 5; i++) {
			parts.add(block("minecraft:spruce_planks", -0.5f + i * 0.205f, 0.08f, -0.5f, 0.18f, 0.04f, 1f));
		}
	}

	private static void bundle(List<Part> parts, float x, float y, float z, float sx, float sy, float sz) {
		parts.add(block(CASH, x, y, z, sx, sy, sz));
		parts.add(block(BAND, x + sx / 2 - 0.035f, y - 0.002f, z - 0.002f, 0.07f, sy + 0.004f, sz + 0.004f));
	}
}
