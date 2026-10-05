package com.thatcoffeelock.havana;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Lighting and smoking cigars. Right-click a lit cigar to take a puff: smoke, a short effect, and one tick off its
 * durability bar. Eight puffs and it's done. Puff three times in a row too quickly and you cough.
 */
final class Smoking {
	private static final Random RANDOM = new Random();
	/** Ticks between puffs. */
	static final int COOLDOWN = 20;
	/** Puffs closer together than this count as chain-smoking. */
	static final int RUSHED = 50;
	/** Rushed puffs in a row before you cough. */
	static final int COUGH_AFTER = 3;
	/** Blocks you can light a cigar on. */
	private static final Set<String> FLAMES = Set.of("campfire", "soul_campfire", "fire", "soul_fire", "torch", "wall_torch",
		"soul_torch", "soul_wall_torch", "lantern", "soul_lantern", "magma_block", "lava", "lava_cauldron", "furnace", "smoker",
		"blast_furnace", "candle", "copper_torch", "copper_wall_torch");

	private static final String[] FINISHED = {
		"You stub it out like a 1920s mob boss.",
		"Ahh. Your doctor would hate this. Your doctor isn't here.",
		"The creepers respect you now. (They don't.)",
		"You flick the stub away with the confidence of a man who owns a boat.",
		"Somewhere, a villager just raised their prices out of spite.",
		"That one went straight to your moustache. You don't have a moustache. You do now, spiritually.",
	};
	private static final String[] COUGHS = {
		"*cough cough* Easy, tiger. It's a cigar, not a snorkel.",
		"*HACK* You puffed it like a vape. Shameful.",
		"*wheeze* Your lungs file a formal complaint.",
		"*cough* The smoke is supposed to go in your mouth, then OUT of your mouth.",
	};
	private static final String[] LIT = {
		"You light up. Instantly 40% more distinguished.",
		"Lit. Now look out of a window thoughtfully.",
		"Lit. You feel the urge to buy a racehorse.",
	};

	private static final Map<UUID, Integer> LAST_PUFF = new HashMap<>();
	private static final Map<UUID, Integer> RUSH = new HashMap<>();

	private Smoking() {
	}

	static void reset() {
		LAST_PUFF.clear();
		RUSH.clear();
	}

	private static String pick(String[] lines) {
		return lines[RANDOM.nextInt(lines.length)];
	}

	private static void actionBar(ServerPlayer player, String text, ChatFormatting color) {
		player.connection.send(new ClientboundSetActionBarTextPacket(Component.literal(text).withStyle(color)));
	}

	private static InteractionHand other(InteractionHand hand) {
		return hand == InteractionHand.MAIN_HAND ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
	}

	// ---------------------------------------------------------------- lighting

	/** Right-click with a cigar: light it (if you've got a light in the other hand), or take a puff. */
	static InteractionResult use(ServerPlayer player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		if (!HavanaItems.isCigar(stack)) {
			return InteractionResult.PASS;
		}
		if (HavanaItems.isLit(stack)) {
			puff(player, hand);
			return InteractionResult.SUCCESS;
		}
		ItemStack fire = player.getItemInHand(other(hand));
		ServerLevel level = (ServerLevel) player.level();
		if (fire.is(Items.FLINT_AND_STEEL)) {
			if (!player.isCreative()) {
				fire.setDamageValue(fire.getDamageValue() + 1);
				if (fire.getDamageValue() >= fire.getMaxDamage()) {
					fire.shrink(1);
					Cmd.sound(level, "minecraft:entity.item.break", player.getX(), player.getY(), player.getZ(), 0.8f, 1f);
				}
			}
			Cmd.sound(level, "minecraft:item.flintandsteel.use", player.getX(), player.getEyeY(), player.getZ(), 1f, 1.1f);
			light(player, hand);
		} else if (fire.is(Items.FIRE_CHARGE)) {
			if (!player.isCreative()) {
				fire.shrink(1);
			}
			Cmd.sound(level, "minecraft:item.firecharge.use", player.getX(), player.getEyeY(), player.getZ(), 0.6f, 1.4f);
			light(player, hand);
		} else {
			actionBar(player, "You need a light: flint and steel in your other hand, or right-click a campfire, torch or lantern.", ChatFormatting.YELLOW);
		}
		return InteractionResult.SUCCESS;
	}

	/** Right-click a flame (campfire, torch, lantern, fire, lava...) with an unlit cigar to light it. */
	static InteractionResult lightOnBlock(ServerPlayer player, ServerLevel level, InteractionHand hand, BlockHitResult hit) {
		ItemStack stack = player.getItemInHand(hand);
		if (!HavanaItems.isCigar(stack) || HavanaItems.isLit(stack)) {
			return InteractionResult.PASS;
		}
		BlockPos pos = hit.getBlockPos();
		BlockState state = level.getBlockState(pos);
		String id = Crops.blockId(state);
		boolean flame = FLAMES.contains(id) || id.endsWith("_candle");
		if (flame && (id.contains("candle") || id.contains("campfire") || id.contains("furnace") || id.equals("smoker"))) {
			// these have an off state: only lit ones count
			flame = state.toString().contains("lit=true");
		}
		if (!flame) {
			return InteractionResult.PASS;
		}
		Cmd.sound(level, "minecraft:block.fire.ambient", pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 1f, 1.3f);
		light(player, hand);
		return InteractionResult.SUCCESS;
	}

	static void light(ServerPlayer player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		ItemStack rest = stack.copy();
		rest.shrink(1);
		player.setItemInHand(hand, HavanaItems.lit(stack));
		if (!rest.isEmpty()) {
			HavanaItems.give(player, rest);
		}
		Vec3 tip = tip(player);
		Cmd.particles((ServerLevel) player.level(), "minecraft:flame", tip.x, tip.y, tip.z, 0.02, 0.01, 3);
		actionBar(player, pick(LIT), ChatFormatting.GOLD);
	}

	// ---------------------------------------------------------------- smoking

	/** Where the end of the cigar is: just in front of the player's mouth. */
	private static Vec3 tip(ServerPlayer player) {
		Vec3 look = player.getLookAngle();
		return new Vec3(player.getX() + look.x * 0.45, player.getEyeY() - 0.15 + look.y * 0.45, player.getZ() + look.z * 0.45);
	}

	/** A status effect by registry id, e.g. "regeneration". */
	record Effect(String id, int seconds, int amplifier) {
	}

	/** The effects one puff of this cigar gives. */
	static List<Effect> effects(ItemStack cigar) {
		List<Effect> effects = new ArrayList<>();
		if (HavanaItems.grade(cigar) == HavanaItems.Grade.AGED) {
			effects.add(new Effect("regeneration", 6, 0));
			effects.add(new Effect("resistance", 15, 0));
		} else {
			effects.add(new Effect("regeneration", 5, 0));
		}
		HavanaItems.Flavor flavor = HavanaItems.flavor(cigar);
		if (flavor != HavanaItems.Flavor.NONE) {
			effects.add(new Effect(flavor.effect, flavor.seconds, 0));
		}
		return effects;
	}

	static void puff(ServerPlayer player, InteractionHand hand) {
		int now = HavanaMod.ticks();
		UUID uuid = player.getUUID();
		Integer last = LAST_PUFF.get(uuid);
		if (last != null && now - last < COOLDOWN) {
			return;
		}
		int rush = last != null && now - last < RUSHED ? RUSH.getOrDefault(uuid, 0) + 1 : 0;
		LAST_PUFF.put(uuid, now);

		ServerLevel level = (ServerLevel) player.level();
		ItemStack cigar = player.getItemInHand(hand);
		String who = uuid.toString();
		double longer = 1.0 + SkillsLink.bonus(uuid, "connoisseur/passive");
		for (Effect effect : effects(cigar)) {
			Cmd.effect(level, who, effect.id(), (int) Math.round(effect.seconds() * longer), effect.amplifier());
		}
		SkillsLink.xp(uuid, "connoisseur", 1);
		Vec3 tip = tip(player);
		Cmd.particles(level, "minecraft:flame", tip.x, tip.y, tip.z, 0.01, 0.005, 1);
		Cmd.run(level, "particle minecraft:campfire_cosy_smoke " + Cmd.pos(tip.x, tip.y + 0.1, tip.z) + " 0.05 0.05 0.05 0.008 2 force");
		Cmd.particles(level, "minecraft:smoke", tip.x, tip.y, tip.z, 0.08, 0.02, 8);
		Cmd.sound(level, "minecraft:block.fire.extinguish", tip.x, tip.y, tip.z, 0.15f, 1.9f);
		Cmd.sound(level, "minecraft:entity.player.breath", tip.x, tip.y, tip.z, 0.5f, 0.7f);

		// Iron Lungs: sometimes you just don't cough
		if (rush >= COUGH_AFTER - 1 && SkillsLink.roll(uuid, "connoisseur/iron_lungs")) {
			rush = 0;
		}
		if (rush >= COUGH_AFTER - 1) {
			rush = 0;
			Cmd.effect(level, who, "nausea", 6, 0);
			Cmd.sound(level, "minecraft:entity.panda.sneeze", player.getX(), player.getEyeY(), player.getZ(), 0.8f, 0.6f);
			player.sendSystemMessage(Component.literal(pick(COUGHS)).withStyle(ChatFormatting.GRAY));
		}
		RUSH.put(uuid, rush);

		int smoked = cigar.getDamageValue() + 1;
		if (smoked >= HavanaItems.PUFFS) {
			finish(player, hand, cigar);
		} else {
			cigar.setDamageValue(smoked);
			HavanaItems.updateLore(cigar);
			actionBar(player, "Puff. " + (HavanaItems.PUFFS - smoked) + " to go.", ChatFormatting.GRAY);
		}
	}

	private static void finish(ServerPlayer player, InteractionHand hand, ItemStack cigar) {
		ServerLevel level = (ServerLevel) player.level();
		boolean granReserva = HavanaItems.grade(cigar) == HavanaItems.Grade.AGED;
		player.setItemInHand(hand, ItemStack.EMPTY);
		SkillsLink.xp(player.getUUID(), "connoisseur", granReserva ? 10 : 5);
		Cmd.sound(level, "minecraft:block.candle.extinguish", player.getX(), player.getEyeY(), player.getZ(), 1f, 0.8f);
		Vec3 tip = tip(player);
		Cmd.particles(level, "minecraft:ash", tip.x, tip.y, tip.z, 0.15, 0.02, 12);
		if (granReserva) {
			Cmd.effect(level, player.getUUID().toString(), "hero_of_the_village", 120, 0);
			player.sendSystemMessage(Component.literal(pick(FINISHED) + " Villagers can smell the money on you.").withStyle(ChatFormatting.LIGHT_PURPLE));
		} else {
			player.sendSystemMessage(Component.literal(pick(FINISHED)).withStyle(ChatFormatting.GOLD));
		}
	}

	/** Every half second: lit cigars smoulder, and go out underwater. */
	static void ambient(MinecraftServer server) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			for (InteractionHand hand : InteractionHand.values()) {
				ItemStack stack = player.getItemInHand(hand);
				if (!HavanaItems.isLit(stack)) {
					continue;
				}
				ServerLevel level = (ServerLevel) player.level();
				if (player.isUnderWater()) {
					player.setItemInHand(hand, HavanaItems.extinguished(stack));
					Cmd.sound(level, "minecraft:block.fire.extinguish", player.getX(), player.getEyeY(), player.getZ(), 0.5f, 1.5f);
					actionBar(player, "Your cigar fizzles out. Smooth move, Cousteau.", ChatFormatting.AQUA);
					continue;
				}
				Vec3 tip = tip(player);
				Cmd.particles(level, "minecraft:smoke", tip.x, tip.y + 0.05, tip.z, 0.01, 0.005, 1);
			}
		}
	}
}
