package com.thatcoffeelock.terraincognita;

import java.util.Random;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

/**
 * Terra Incognita is crawling with illagers. Every so often, each player over there may be found by a band of them:
 * pillagers and vindicators, now and then an evoker, rarely a ravager with a rider. They turn up out of sight (24-40
 * blocks away, on dry land) and come looking. Day and night. There's a cap, so it never becomes a lag machine.
 */
final class Raiders {
	private static final Random RANDOM = new Random();

	private Raiders() {
	}

	static void tick(MinecraftServer server) {
		ServerLevel level = Terra.terra(server);
		TerraConfig c = TerraConfig.get();
		if (level == null || c.raidChance <= 0) {
			return;
		}
		for (ServerPlayer player : level.players()) {
			if (player.isCreative() || player.isSpectator() || RANDOM.nextDouble() >= c.raidChance) {
				continue;
			}
			if (nearby(level, player.blockPosition()) < c.maxIllagersNearby) {
				band(level, player.blockPosition(), 2 + RANDOM.nextInt(c.maxBandSize - 1));
			}
		}
	}

	/** Illagers within 48 blocks. */
	static int nearby(ServerLevel level, BlockPos pos) {
		AABB box = new AABB(pos.getX() - 48, pos.getY() - 48, pos.getZ() - 48, pos.getX() + 48, pos.getY() + 48, pos.getZ() + 48);
		return level.getEntitiesOfClass(Mob.class, box, Terra::isIllager).size();
	}

	/** Dry ground 24-40 blocks from {@code near}, in a loaded chunk, or null after a few tries. */
	static @Nullable BlockPos spot(ServerLevel level, BlockPos near) {
		for (int attempt = 0; attempt < 8; attempt++) {
			double angle = RANDOM.nextDouble() * Math.PI * 2;
			double distance = 24 + RANDOM.nextDouble() * 16;
			int x = near.getX() + (int) Math.round(Math.cos(angle) * distance);
			int z = near.getZ() + (int) Math.round(Math.sin(angle) * distance);
			if (!level.isLoaded(new BlockPos(x, near.getY(), z))) {
				continue;
			}
			int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
			BlockPos ground = new BlockPos(x, y - 1, z);
			if (level.getBlockState(ground).getFluidState().isEmpty() && !level.getBlockState(ground).isAir()) {
				return ground.above();
			}
		}
		return null;
	}

	/** Spawns a band of {@code size} near {@code near}. Returns where, or null if there was no dry ground. */
	static @Nullable BlockPos band(ServerLevel level, BlockPos near, int size) {
		BlockPos at = spot(level, near);
		if (at == null) {
			return null;
		}
		for (int i = 0; i < size; i++) {
			double roll = RANDOM.nextDouble();
			String type = roll < 0.03 ? "ravager" : roll < 0.10 ? "evoker" : roll < 0.55 ? "pillager" : "vindicator";
			double x = at.getX() + 0.5 + RANDOM.nextInt(5) - 2;
			double z = at.getZ() + 0.5 + RANDOM.nextInt(5) - 2;
			Cmd.run(level, "summon minecraft:" + type + " " + Cmd.pos(x, at.getY(), z));
			if (type.equals("ravager")) {
				Cmd.run(level, "summon minecraft:pillager " + Cmd.pos(x, at.getY() + 2, z));
			}
		}
		return at;
	}
}
