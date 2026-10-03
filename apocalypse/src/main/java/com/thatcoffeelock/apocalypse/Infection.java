package com.thatcoffeelock.apocalypse;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import org.jetbrains.annotations.Nullable;

/**
 * Zombie bites can infect. The infection gets worse over twenty minutes (hunger, then weakness and nausea,
 * then slowness), and then you turn. A golden apple cures it. A totem of undying burns it out of you.
 * Die infected (or get eaten) and you get back up as a zombie with your name on it, and it'll help itself to your stuff.
 */
final class Infection {
	enum Stage {
		NONE, BITTEN, FEVERISH, ROTTING, TURNING
	}

	private Infection() {
	}

	static boolean infected(ServerPlayer player) {
		return player.getAttached(ApocalypseMod.INFECTION) != null;
	}

	/** Seconds since the bite, or -1. */
	static int seconds(ServerPlayer player) {
		Integer s = player.getAttached(ApocalypseMod.INFECTION);
		return s == null ? -1 : s;
	}

	static int total() {
		return ApocalypseConfig.get().infectionMinutes * 60;
	}

	static Stage stage(int seconds, int total) {
		if (seconds < 0) {
			return Stage.NONE;
		}
		double f = (double) seconds / total;
		return f < 0.25 ? Stage.BITTEN : f < 0.5 ? Stage.FEVERISH : f < 0.8 ? Stage.ROTTING : Stage.TURNING;
	}

	static void afterDamage(LivingEntity victim, DamageSource source, float damageTaken) {
		ApocalypseConfig cfg = ApocalypseConfig.get();
		if (!cfg.infection || damageTaken <= 0 || !(victim instanceof ServerPlayer player) || player.isCreative() || infected(player)) {
			return;
		}
		if (!Undead.isZombie(source.getEntity()) || source.getDirectEntity() != source.getEntity()) {
			return; // a bite, not a trident
		}
		double chance = cfg.infectionChance * (HordeNight.active() ? 2 : 1);
		if (Hordes.RANDOM.nextDouble() < chance) {
			infect(player);
		}
	}

	static void infect(ServerPlayer player) {
		player.setAttached(ApocalypseMod.INFECTION, 0);
		player.sendSystemMessage(Component.literal("☣ You've been bitten. ").withStyle(ChatFormatting.DARK_GREEN, ChatFormatting.BOLD)
			.append(Component.literal("That's... not great. Eat a golden apple in the next " + ApocalypseConfig.get().infectionMinutes
				+ " minutes, or start practising your groan.").withStyle(ChatFormatting.GREEN)));
		Cmd.sound((ServerLevel) player.level(), "minecraft:entity.zombie_villager.converted", player.getX(), player.getY(), player.getZ(), 0.8f, 1.4f);
	}

	static void cure(ServerPlayer player, String how) {
		if (!infected(player)) {
			return;
		}
		player.removeAttached(ApocalypseMod.INFECTION);
		player.sendSystemMessage(Component.literal("✚ Cured. ").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
			.append(Component.literal(how).withStyle(ChatFormatting.YELLOW)));
		Cmd.sound((ServerLevel) player.level(), "minecraft:entity.zombie_villager.cure", player.getX(), player.getY(), player.getZ(), 0.7f, 1.2f);
	}

	/** Once a second. */
	static void tick(MinecraftServer server) {
		boolean enabled = ApocalypseConfig.get().infection;
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			int s = seconds(player);
			if (s < 0) {
				continue;
			}
			if (!enabled) {
				player.removeAttached(ApocalypseMod.INFECTION);
				continue;
			}
			if (ateGoldenApple(player)) {
				cure(player, "The golden apple did it. Gold: is there anything it can't do?");
				continue;
			}
			if (!player.isAlive() || player.isCreative() || player.isSpectator()) {
				continue;
			}
			s++;
			player.setAttached(ApocalypseMod.INFECTION, s);
			symptoms(player, s);
		}
	}

	/** Golden apples (both kinds) give absorption, and nothing else infected players can get does. */
	private static boolean ateGoldenApple(ServerPlayer player) {
		for (MobEffectInstance effect : player.getActiveEffects()) {
			if (effect.getEffect().getRegisteredName().equals("minecraft:absorption")) {
				return true;
			}
		}
		return false;
	}

	private static void symptoms(ServerPlayer player, int s) {
		ServerLevel level = (ServerLevel) player.level();
		int total = total();
		String who = player.getUUID().toString();
		Stage stage = stage(s, total);
		if (s >= total) {
			turn(player);
			return;
		}
		if (s % 10 == 0) {
			int left = total - s;
			player.connection.send(new ClientboundSetActionBarTextPacket(Component.literal("☣ Infected: " + left / 60 + ":" + String.format("%02d", left % 60)
				+ " until you turn. Eat a golden apple.").withStyle(stage == Stage.TURNING ? ChatFormatting.DARK_RED : ChatFormatting.DARK_GREEN)));
		}
		switch (stage) {
			case FEVERISH -> {
				if (s % 30 == 0) {
					Cmd.run(level, "effect give " + who + " minecraft:hunger 12 0 true");
				}
				if (s % 45 == 0) {
					Cmd.run(level, "effect give " + who + " minecraft:weakness 20 0 true");
				}
			}
			case ROTTING -> {
				if (s % 20 == 0) {
					Cmd.run(level, "effect give " + who + " minecraft:hunger 15 1 true");
					Cmd.run(level, "effect give " + who + " minecraft:weakness 25 0 true");
				}
				if (s % 60 == 0) {
					Cmd.run(level, "effect give " + who + " minecraft:nausea 8 0 true");
					player.sendSystemMessage(Component.literal("You feel... hungry. Not for food.").withStyle(ChatFormatting.DARK_GREEN, ChatFormatting.ITALIC));
				}
			}
			case TURNING -> {
				if (s % 15 == 0) {
					Cmd.run(level, "effect give " + who + " minecraft:hunger 20 2 true");
					Cmd.run(level, "effect give " + who + " minecraft:weakness 20 1 true");
					Cmd.run(level, "effect give " + who + " minecraft:slowness 20 0 true");
					Cmd.run(level, "effect give " + who + " minecraft:mining_fatigue 20 0 true");
				}
				if (s % 40 == 0) {
					Cmd.run(level, "effect give " + who + " minecraft:nausea 10 0 true");
					Cmd.sound(level, "minecraft:entity.zombie.ambient", player.getX(), player.getY(), player.getZ(), 0.6f, 1.3f);
					player.sendSystemMessage(Component.literal("Somebody nearby smells delicious. It's everybody.").withStyle(ChatFormatting.DARK_RED, ChatFormatting.ITALIC));
				}
			}
			default -> {
			}
		}
	}

	/** Time's up. */
	private static void turn(ServerPlayer player) {
		ServerLevel level = (ServerLevel) player.level();
		player.sendSystemMessage(Component.literal("☠ You turned.").withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD));
		Cmd.run(level, "damage " + player.getUUID() + " 1000 minecraft:wither");
		if (player.isAlive()) {
			// a totem of undying, probably. That burns it out.
			cure(player, "Your totem of undying burned the rot right out of you. Worth it.");
		}
	}

	/** Infected players, and anyone a zombie kills, get back up. */
	static void afterDeath(LivingEntity entity, DamageSource source) {
		if (!(entity instanceof ServerPlayer player) || !ApocalypseConfig.get().riseAsZombie) {
			return;
		}
		boolean bitten = infected(player);
		boolean eaten = Undead.isZombie(source.getEntity());
		player.removeAttached(ApocalypseMod.INFECTION);
		if (!bitten && !eaten) {
			return;
		}
		ServerLevel level = (ServerLevel) player.level();
		BlockPos at = player.blockPosition();
		String name = player.getName().getString();
		level.getServer().getPlayerList().broadcastSystemMessage(Component.literal("☠ " + name + " didn't stay dead.").withStyle(ChatFormatting.DARK_GREEN), false);
		ApocalypseMod.later(60, () -> rise(level, at, name));
	}

	/** A zombie with the dead player's name gets up where they fell, and picks up whatever they dropped. */
	static @Nullable Mob rise(ServerLevel level, BlockPos at, String name) {
		Mob zombie = Undead.spawn(level, EntityTypes.ZOMBIE, at, Hordes.RANDOM.nextFloat() * 360f);
		if (zombie == null) {
			return null;
		}
		zombie.setCustomName(Component.literal(name).withStyle(ChatFormatting.DARK_GREEN));
		zombie.setPersistenceRequired();
		zombie.setCanPickUpLoot(true);
		Cmd.particles(level, "minecraft:soul", at.getX() + 0.5, at.getY() + 1, at.getZ() + 0.5, 0.4, 0.02, 20);
		Cmd.sound(level, "minecraft:entity.zombie_villager.converted", at.getX() + 0.5, at.getY() + 1, at.getZ() + 0.5, 1.0f, 0.7f);
		return zombie;
	}
}
