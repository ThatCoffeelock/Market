package com.thatcoffeelock.hamlets;

import java.util.ArrayList;
import java.util.List;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class HamletsMod implements ModInitializer {
	public static final String MOD_ID = "hamlets";
	public static final Logger LOG = LoggerFactory.getLogger("Hamlets");

	static StructureType<HamletStructure> STRUCTURE_TYPE;
	static StructurePieceType PIECE_TYPE;

	private static final List<Delayed> LATER = new ArrayList<>();
	private static int ticks;

	@Override
	public void onInitialize() {
		StructureType<HamletStructure> type = () -> HamletStructure.CODEC;
		STRUCTURE_TYPE = Registry.register(BuiltInRegistries.STRUCTURE_TYPE, id("hamlet"), type);
		StructurePieceType piece = (context, tag) -> new HamletPiece(tag);
		PIECE_TYPE = Registry.register(BuiltInRegistries.STRUCTURE_PIECE, id("hamlet"), piece);

		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			if (Boolean.getBoolean("hamlets.smokeTest")) {
				SmokeTest.run(server);
			}
		});
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> LATER.clear());
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			ticks++;
			if (!LATER.isEmpty()) {
				List<Delayed> due = new ArrayList<>();
				LATER.removeIf(d -> d.at <= ticks && due.add(d));
				due.forEach(d -> d.task.run());
			}
		});
		CommandRegistrationCallback.EVENT.register(HamletsCommands::register);
		LOG.info("Hamlets & Horrors loaded. Mind the neighbours.");
	}

	static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}

	/** Builds a structure right now in a running world, e.g. from the command. */
	static Canvas buildNow(ServerLevel level, Plan plan, Folk folk, BlockPos origin, Rotation turn, long seed) {
		Style style = Style.forBiome(level.getBiome(origin));
		BoundingBox clip = new BoundingBox(origin.getX() - plan.radius, level.getMinY(), origin.getZ() - plan.radius,
			origin.getX() + plan.radius, level.getMaxY(), origin.getZ() + plan.radius);
		Canvas canvas = new Canvas(level, clip, origin, turn, seed, style, folk == Folk.MONSTERS, true);
		Builders.build(canvas, plan);
		return canvas;
	}

	static void later(int delayTicks, Runnable task) {
		LATER.add(new Delayed(ticks + delayTicks, task));
	}

	private record Delayed(int at, Runnable task) {
	}
}
