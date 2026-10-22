package com.thatcoffeelock.terraincognita;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Prediction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Crossing over: the captain and every crew member aboard something nearby are carried through the fog to the same
 * spot on the other side (or the nearest open water to it). An Ahoy ship with Copper sheathing comes along, cargo and
 * all; anyone else arrives in a fresh boat, and whatever they sailed in stays moored at home.
 */
final class Crossing {
	/** How far (in chunks) to look for open water to arrive in. */
	static final int SEARCH_CHUNKS = 5;

	private Crossing() {
	}

	/** Open water nearest to (x, z) to arrive in: the block above the surface. Falls back to dry land at (x, z). */
	static BlockPos arrival(ServerLevel level, int x, int z) {
		for (int r = 0; r <= SEARCH_CHUNKS; r++) {
			for (int dx = -r; dx <= r; dx++) {
				for (int dz = -r; dz <= r; dz++) {
					if (Math.max(Math.abs(dx), Math.abs(dz)) != r) {
						continue; // only the ring at this distance
					}
					int bx = x + dx * 16;
					int bz = z + dz * 16;
					level.getChunk(bx >> 4, bz >> 4); // heights read as the bottom of the world until the chunk is there
					int y = level.getHeight(Heightmap.Types.WORLD_SURFACE, bx, bz);
					if (!level.getBlockState(new BlockPos(bx, y - 1, bz)).getFluidState().isEmpty()) {
						return new BlockPos(bx, y, bz);
					}
				}
			}
		}
		level.getChunk(x >> 4, z >> 4);
		return new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
	}

	/** The captain and the crew: players aboard something within {@link TerraConfig#crewRadius} of the captain. */
	static List<ServerPlayer> crew(ServerPlayer captain) {
		List<ServerPlayer> crew = new ArrayList<>();
		crew.add(captain);
		int r = TerraConfig.get().crewRadius;
		for (ServerPlayer p : ((ServerLevel) captain.level()).players()) {
			if (p != captain && p.isPassenger() && !p.isSpectator() && p.distanceToSqr(captain) <= (double) r * r) {
				crew.add(p);
			}
		}
		return crew;
	}

	/**
	 * Carries the captain and crew across. An Ahoy ship the captain is aboard goes too, bottled up on this side and
	 * launched on the other with everyone back in their seats; if there's no room for it over there, the captain gets
	 * the bottle and the crew a boat each. Returns false if there's nowhere to go from here.
	 */
	static boolean cross(ServerPlayer captain) {
		ServerLevel from = (ServerLevel) captain.level();
		ServerLevel to = Terra.across(from);
		if (to == null) {
			return false;
		}
		boolean outward = Terra.isTerra(to);
		BlockPos at = arrival(to, captain.getBlockX(), captain.getBlockZ());
		List<ServerPlayer> crew = crew(captain);
		Map<String, Object> ship = AhoyLink.aboard(captain);
		ItemStack bottle = ItemStack.EMPTY;
		if (ship != null && AhoyLink.refusal(ship) == null) {
			for (ServerPlayer sailor : AhoyLink.crew(ship)) {
				if (!crew.contains(sailor)) {
					crew.add(sailor);
				}
			}
			bottle = AhoyLink.pack(captain);
		}
		for (int i = 0; i < crew.size(); i++) {
			ServerPlayer sailor = crew.get(i);
			double x = at.getX() + 0.5 + (i % 3) * 2.5;
			double z = at.getZ() + 0.5 + (i / 3) * 2.5;
			double y = at.getY() + 0.2;
			sailor.stopRiding();
			Cmd.run(from, "execute in " + Terra.id(to) + " run tp " + sailor.getUUID() + " " + Cmd.pos(x, y, z));
			Cmd.effect(to, sailor.getUUID().toString(), "darkness", 3, 0);
			SkillsLink.xp(sailor.getUUID(), "sailing", outward ? 40 : 15);
			sailor.sendSystemMessage(outward
				? Component.literal("The fog lifts over islands nobody has charted. Terra Incognita. Something on the shore is watching you.")
					.withStyle(ChatFormatting.GOLD)
				: Component.literal("The fog thins, and the stars are the ones you know. Home waters.").withStyle(ChatFormatting.AQUA));
		}
		ItemStack shipBottle = bottle;
		TerraMod.later(3, () -> {
			if (!shipBottle.isEmpty()) {
				if (AhoyLink.launch(shipBottle, crew)) {
					return;
				}
				// no room for the hull: the ship stays safe in its bottle, the crew rows
				captain.getInventory().placeItemBackInInventory(shipBottle, Prediction.SERVER_ONLY);
				captain.sendSystemMessage(Component.literal("No water roomy enough for the " + AhoyLink.name(ship)
					+ " here. She's safe in her bottle in your pack; launch her on open water.").withStyle(ChatFormatting.YELLOW));
			}
			for (ServerPlayer sailor : crew) {
				if (sailor.level() != to) {
					continue;
				}
				UUID boat = UUID.randomUUID();
				Cmd.run(to, "summon minecraft:oak_boat " + Cmd.pos(sailor.getX(), sailor.getY(), sailor.getZ()) + " {" + Cmd.uuidNbt(boat) + "}");
				Cmd.run(to, "ride " + sailor.getUUID() + " mount " + boat);
			}
		});
		Cmd.sound(to, "minecraft:ambient.underwater.exit", at.getX() + 0.5, at.getY() + 1, at.getZ() + 0.5, 1f, 0.6f);
		if (outward) {
			TerraMod.LOG.info("{} sailed into Terra Incognita at {} with {} aboard{}", captain.getName().getString(), at.toShortString(), crew.size(),
				bottle.isEmpty() ? "" : " the " + AhoyLink.name(ship));
		}
		return true;
	}
}
