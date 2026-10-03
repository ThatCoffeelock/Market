package com.thatcoffeelock.skills;

import java.util.ArrayList;
import java.util.List;

import com.mojang.serialization.Codec;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;

/**
 * Remembers which XP-giving blocks (ores, logs, natural stone, dirt, sand...) a player placed, so placing and
 * breaking the same block can't farm XP. Stored on the chunk itself, so it costs nothing for unloaded chunks and
 * only blocks that would give XP are tracked: your stone-brick castle doesn't count, your cobble generator doesn't
 * either (cobblestone gives no XP to begin with).
 */
final class Placed {
	static final AttachmentType<List<Long>> PLACED = AttachmentRegistry.create(
		Identifier.fromNamespaceAndPath(SkillsMod.MOD_ID, "placed"), builder -> builder.persistent(Codec.LONG.listOf()));

	private Placed() {
	}

	/** Called for every block a player places (see BlockItemMixin). */
	static void onPlaced(ServerLevel level, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		if (!Gathering.tracked(state)) {
			return;
		}
		LevelChunk chunk = level.getChunkAt(pos);
		List<Long> list = chunk.getAttached(PLACED);
		long key = pos.asLong();
		if (list != null && list.contains(key)) {
			return;
		}
		List<Long> next = list == null ? new ArrayList<>() : new ArrayList<>(list);
		next.add(key);
		chunk.setAttached(PLACED, next);
	}

	/** Forgets the spot and says whether a player had placed the block that was there. */
	static boolean remove(ServerLevel level, BlockPos pos) {
		LevelChunk chunk = level.getChunkAt(pos);
		List<Long> list = chunk.getAttached(PLACED);
		long key = pos.asLong();
		if (list == null || !list.contains(key)) {
			return false;
		}
		List<Long> next = new ArrayList<>(list);
		next.remove(Long.valueOf(key));
		if (next.isEmpty()) {
			chunk.removeAttached(PLACED);
		} else {
			chunk.setAttached(PLACED, next);
		}
		return true;
	}

	static boolean isPlaced(ServerLevel level, BlockPos pos) {
		List<Long> list = level.getChunkAt(pos).getAttached(PLACED);
		return list != null && list.contains(pos.asLong());
	}
}
