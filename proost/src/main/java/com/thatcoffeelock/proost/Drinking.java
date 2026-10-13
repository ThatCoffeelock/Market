package com.thatcoffeelock.proost;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Drinking. Right-click a bottle to take a swig: its effects, and one point of drunkenness. Four swigs and you're
 * left holding an empty glass bottle. Drunkenness wears off by one point every {@link #SOBER_TICKS}:
 * <ul>
 * <li>{@link #TIPSY}+: tipsy. Nothing bad yet. Dutch Courage, as they say.</li>
 * <li>{@link #DRUNK}+: drunk. The world spins (Nausea) and you stagger about.</li>
 * <li>{@link #PASS_OUT}+: you pass out for a few seconds, and wake up a bit less drunk and a lot less dignified.</li>
 * </ul>
 */
final class Drinking {
	private static final Random RANDOM = new Random();
	/** Ticks between swigs. */
	static final int COOLDOWN = 20;
	/** One point of drunkenness wears off per this many ticks (30 seconds). */
	static final int SOBER_TICKS = 600;
	static final double TIPSY = 3;
	static final double DRUNK = 5;
	static final double PASS_OUT = 8;
	/** How hard a stagger shoves you. */
	static final double STAGGER = 0.3;

	private static final String[] FINISHED = {
		"You drain the bottle. The bottle had it coming.",
		"Empty. You hold the bottle up to the light, just in case. Still empty.",
		"Finished. Somewhere in Schiedam, a distiller sheds a proud tear.",
		"That's the bottle done. Your liver files for overtime.",
	};
	private static final String[] PASSED_OUT = {
		"You pass out. When you come to, someone has drawn a moustache on you. It's an improvement.",
		"Lights out. You dream of windmills. Big, judgmental windmills.",
		"You lie down for a quick nap on the floor. The floor is very understanding.",
		"Down you go. A passing chicken looks deeply disappointed in you.",
	};

	private static final Map<UUID, Double> DRUNKENNESS = new HashMap<>();
	private static final Map<UUID, Integer> LAST_SWIG = new HashMap<>();

	private Drinking() {
	}

	static void reset() {
		DRUNKENNESS.clear();
		LAST_SWIG.clear();
	}

	private static String pick(String[] lines) {
		return lines[RANDOM.nextInt(lines.length)];
	}

	private static void actionBar(ServerPlayer player, String text, ChatFormatting color) {
		player.connection.send(new ClientboundSetActionBarTextPacket(Component.literal(text).withStyle(color)));
	}

	// ---------------------------------------------------------------- drunkenness

	static double drunkenness(UUID player) {
		return DRUNKENNESS.getOrDefault(player, 0.0);
	}

	static void setDrunkenness(UUID player, double value) {
		if (value <= 0) {
			DRUNKENNESS.remove(player);
		} else {
			DRUNKENNESS.put(player, value);
		}
	}

	/** One step of sobering up: {@code ticks} worth. */
	static void soberUp(int ticks) {
		double off = (double) ticks / SOBER_TICKS;
		for (UUID player : new ArrayList<>(DRUNKENNESS.keySet())) {
			setDrunkenness(player, drunkenness(player) - off);
		}
	}

	static String describe(double drunk) {
		if (drunk >= DRUNK) {
			return "drunk";
		}
		if (drunk >= TIPSY) {
			return "tipsy";
		}
		return drunk > 0 ? "merry" : "sober";
	}

	// ---------------------------------------------------------------- drinking

	/** A status effect by registry id, e.g. "strength". */
	record Effect(String id, int seconds, int amplifier) {
	}

	/** The effects one swig of this bottle gives. */
	static List<Effect> effects(ItemStack bottle) {
		boolean aged = ProostItems.isAged(bottle);
		List<Effect> effects = new ArrayList<>();
		switch (ProostItems.drink(bottle)) {
			case GENEVER -> {
				effects.add(new Effect("strength", aged ? 60 : 30, 0));
				if (aged) {
					effects.add(new Effect("resistance", 30, 0));
				}
			}
			case WHISKY -> {
				effects.add(new Effect("resistance", aged ? 60 : 30, 0));
				if (aged) {
					effects.add(new Effect("regeneration", 8, 0));
				}
			}
			case RUM -> {
				effects.add(new Effect("water_breathing", aged ? 120 : 60, 0));
				if (aged) {
					effects.add(new Effect("dolphins_grace", 30, 0));
				}
			}
			case BRANDY -> {
				effects.add(new Effect("haste", aged ? 90 : 45, 0));
				if (aged) {
					effects.add(new Effect("absorption", 60, 0));
				}
			}
		}
		return effects;
	}

	/** Right-click with a bottle: open it if it's still sealed, and take a swig. */
	static InteractionResult use(ServerPlayer player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		if (!ProostItems.isSpirit(stack)) {
			return InteractionResult.PASS;
		}
		int now = ProostMod.ticks();
		Integer last = LAST_SWIG.get(player.getUUID());
		if (last != null && now - last < COOLDOWN) {
			return InteractionResult.SUCCESS;
		}
		LAST_SWIG.put(player.getUUID(), now);
		if (!ProostItems.isOpen(stack)) {
			ItemStack rest = stack.copy();
			rest.shrink(1);
			player.setItemInHand(hand, ProostItems.opened(stack));
			if (!rest.isEmpty()) {
				ProostItems.give(player, rest);
			}
			ServerLevel level = (ServerLevel) player.level();
			Cmd.sound(level, "minecraft:entity.item.pickup", player.getX(), player.getEyeY(), player.getZ(), 0.6f, 0.5f);
		}
		swig(player, hand);
		return InteractionResult.SUCCESS;
	}

	static void swig(ServerPlayer player, InteractionHand hand) {
		ServerLevel level = (ServerLevel) player.level();
		ItemStack bottle = player.getItemInHand(hand);
		UUID uuid = player.getUUID();
		String who = uuid.toString();
		double longer = 1.0 + SkillsLink.bonus(uuid, "connoisseur/passive");
		for (Effect effect : effects(bottle)) {
			Cmd.effect(level, who, effect.id(), (int) Math.round(effect.seconds() * longer), effect.amplifier());
		}
		SkillsLink.xp(uuid, "connoisseur", 1);
		Cmd.sound(level, "minecraft:entity.generic.drink", player.getX(), player.getEyeY(), player.getZ(), 0.8f, 0.9f);

		// Iron Lungs (and liver): sometimes it just doesn't go to your head
		double before = drunkenness(uuid);
		double after = SkillsLink.roll(uuid, "connoisseur/iron_lungs") ? before : before + 1;
		setDrunkenness(uuid, after);

		int drunk = bottle.getDamageValue() + 1;
		if (drunk >= ProostItems.SWIGS) {
			player.setItemInHand(hand, new ItemStack(Items.GLASS_BOTTLE));
			SkillsLink.xp(uuid, "connoisseur", ProostItems.isAged(bottle) ? 6 : 3);
			player.sendSystemMessage(Component.literal(pick(FINISHED)).withStyle(ChatFormatting.GOLD));
		} else {
			bottle.setDamageValue(drunk);
			ProostItems.updateLore(bottle);
		}

		if (after >= PASS_OUT) {
			passOut(player);
		} else if (after >= DRUNK && before < DRUNK) {
			Cmd.effect(level, who, "nausea", 10, 0);
			player.sendSystemMessage(Component.literal("Whoa. The ground's gone wobbly. You're drunk.").withStyle(ChatFormatting.LIGHT_PURPLE));
		} else if (after >= TIPSY && before < TIPSY) {
			player.sendSystemMessage(Component.literal("A warm glow. You feel brave, handsome and slightly Dutch.").withStyle(ChatFormatting.GOLD));
		} else if (drunk < ProostItems.SWIGS) {
			actionBar(player, "Proost! " + (ProostItems.SWIGS - drunk) + " swigs left. You're " + describe(after) + ".", ChatFormatting.GRAY);
		}
	}

	private static void passOut(ServerPlayer player) {
		ServerLevel level = (ServerLevel) player.level();
		String who = player.getUUID().toString();
		Cmd.effect(level, who, "blindness", 5, 0);
		Cmd.effect(level, who, "slowness", 6, 4);
		Cmd.effect(level, who, "nausea", 12, 0);
		Cmd.sound(level, "minecraft:entity.player.hurt", player.getX(), player.getY(), player.getZ(), 0.6f, 0.6f);
		setDrunkenness(player.getUUID(), DRUNK - 1);
		player.sendSystemMessage(Component.literal(pick(PASSED_OUT)).withStyle(ChatFormatting.LIGHT_PURPLE));
	}

	/** Every two seconds: drunk players sway and stagger. */
	static void ambient(MinecraftServer server) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (drunkenness(player.getUUID()) < DRUNK || player.isCreative() || player.isSpectator()) {
				continue;
			}
			ServerLevel level = (ServerLevel) player.level();
			Cmd.effect(level, player.getUUID().toString(), "nausea", 5, 0);
			if (player.onGround() && RANDOM.nextDouble() < 0.35) {
				double angle = RANDOM.nextDouble() * Math.PI * 2;
				player.setDeltaMovement(player.getDeltaMovement().add(Math.cos(angle) * STAGGER, 0.05, Math.sin(angle) * STAGGER));
				player.connection.send(new ClientboundSetEntityMotionPacket(player)); // players move themselves: tell their client
			}
		}
	}

	static void onDisconnect(ServerPlayer player) {
		LAST_SWIG.remove(player.getUUID());
	}
}
