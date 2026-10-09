package com.thatcoffeelock.sellswords;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiFunction;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import org.jetbrains.annotations.Nullable;

/**
 * Mercenaries following you climb aboard when you do: Ahoy ships, Blimey airships and Mobile Home vehicles put a
 * hook in the ObjectShare list {@code sellswords:board}, a {@code BiFunction<ServerPlayer, Entity, Boolean>} that
 * seats the passenger (the mercenary's body) in a free seat of whatever the player is riding, and says whether it did.
 * Aboard, the body sits in the seat and the brain stays with it, switched off. Rangers shoot from the rail.
 * When you get off, so do they.
 */
final class Boarding {
	static final String KEY = "sellswords:board";

	private Boarding() {
	}

	static void publish() {
		FabricLoader.getInstance().getObjectShare().putIfAbsent(KEY, new CopyOnWriteArrayList<BiFunction<ServerPlayer, Entity, Boolean>>());
	}

	@SuppressWarnings("unchecked")
	static List<BiFunction<ServerPlayer, Entity, Boolean>> hooks() {
		Object list = FabricLoader.getInstance().getObjectShare().get(KEY);
		return list instanceof List<?> l ? (List<BiFunction<ServerPlayer, Entity, Boolean>>) l : List.of();
	}

	/** Returns true while the mercenary sits aboard something. */
	static boolean tick(Merc m, Duty.State s, LivingEntity brain, @Nullable LivingEntity body, @Nullable ServerPlayer owner) {
		Mob mob = (Mob) brain;
		boolean aboardWanted = owner != null && m.orders() == Merc.Orders.FOLLOW && owner.isPassenger() && owner.level() == brain.level();
		if (body != null && body.isPassenger()) {
			if (!aboardWanted || body.distanceTo(owner) > 48) {
				leave(m, s, brain, body);
				return false;
			}
			if (!mob.isNoAi()) {
				mob.setNoAi(true);
				mob.getNavigation().stop();
			}
			brain.setPos(body.getX(), body.getY(), body.getZ());
			brain.setYRot(body.getYRot());
			s.seated = true;
			return true;
		}
		if (s.seated || mob.isNoAi()) {
			mob.setNoAi(false); // got off (or the seat went away, or a restart while aboard)
			s.seated = false;
		}
		if (aboardWanted && body != null && Duty.ticks() % 10 == 0 && brain.distanceTo(owner) < 32) {
			for (BiFunction<ServerPlayer, Entity, Boolean> hook : hooks()) {
				Boolean seated;
				try {
					seated = hook.apply(owner, body);
				} catch (RuntimeException e) {
					SellswordsMod.LOG.warn("A boarding hook failed", e);
					continue;
				}
				if (Boolean.TRUE.equals(seated) && body.isPassenger()) {
					mob.getNavigation().stop();
					mob.setNoAi(true);
					s.seated = true;
					if (brain.level() instanceof ServerLevel level) {
						Cmd.sound(level, "minecraft:entity.horse.saddle", body.getX(), body.getY(), body.getZ(), 0.5f, 1.4f);
					}
					return true;
				}
			}
		}
		return false;
	}

	/** Off the ship: brain and body step onto the nearest solid ground. */
	static void leave(Merc m, Duty.State s, LivingEntity brain, LivingEntity body) {
		body.stopRiding();
		((Mob) brain).setNoAi(false);
		s.seated = false;
		if (brain.level() instanceof ServerLevel level) {
			BlockPos spot = Spots.find(level, body.blockPosition(), 4);
			if (spot != null) {
				Cmd.tp(level, brain.getUUID(), spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5);
				Cmd.tp(level, body.getUUID(), spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5);
			} else {
				brain.setPos(body.getX(), body.getY(), body.getZ());
			}
		}
	}
}
