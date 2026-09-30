package com.thatcoffeelock.burlapsack;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Random;
import java.util.UUID;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/** Bagging villagers and letting them out again. */
public final class Sacks {
	/** How far away iron golems notice a kidnapping. */
	static final double WITNESS_RANGE = 24;
	/** The grudge a captive holds against whoever bagged them: vanilla "minor_negative" gossip, which fades over a few days. */
	static final int GRUDGE = 50;

	/** Saved data that must not travel with the captive: identity, position and memories of their old village. */
	private static final List<String> STRIP = List.of("UUID", "Pos", "Motion", "Rotation", "FallDistance", "fall_distance",
		"Fire", "OnGround", "PortalCooldown", "Brain", "leash", "sleeping_pos", "SleepingX", "SleepingY", "SleepingZ");

	/**
	 * Villager methods called by name, so a renamed class package in a Minecraft update can't break the build.
	 * The smoke test checks they still exist.
	 */
	static final String RELEASE_POIS = "releaseAllPois";
	static final String IS_TRADING = "isTrading";

	private static final Random RANDOM = new Random();
	private static final String[] BAGGED = {
		" goes in the sack. The sack is not happy about it either.",
		" is now luggage.",
		" has been relocated against their will.",
		" is in the bag. Literally.",
	};
	private static final String[] RELEASED = {
		" climbs out of the sack, looking betrayed.",
		" tumbles out and pretends this is fine.",
		" crawls out. They will be telling their therapist about this.",
		" is free! Well. Free-ish.",
	};

	private Sacks() {
	}

	static final String VILLAGER = "minecraft:villager";
	static final String WANDERING_TRADER = "minecraft:wandering_trader";
	static final String IRON_GOLEM = "minecraft:iron_golem";

	/** The entity's type id, e.g. minecraft:villager. */
	static String typeId(Entity entity) {
		return EntityType.getKey(entity.getType()).toString();
	}

	static boolean fits(Entity entity) {
		String type = typeId(entity);
		return type.equals(VILLAGER) || type.equals(WANDERING_TRADER);
	}

	/** Right-click on an entity. */
	static InteractionResult useEntity(ServerPlayer player, InteractionHand hand, Entity entity) {
		if (entity.isRemoved()) {
			return InteractionResult.PASS; // the second packet of the click that just bagged them
		}
		ItemStack stack = player.getItemInHand(hand);
		if (!SackItems.isEmptySack(stack)) {
			if (SackItems.isFullSack(stack) && fits(entity)) {
				actionBar(player, "The sack is full. One hostage at a time.");
				return InteractionResult.SUCCESS;
			}
			return InteractionResult.PASS;
		}
		if (!fits(entity)) {
			if (entity instanceof LivingEntity) {
				actionBar(player, "Only villagers and wandering traders fit in the sack.");
			}
			return InteractionResult.PASS;
		}
		if (!(entity.level() instanceof ServerLevel level) || !entity.isAlive()) {
			return InteractionResult.PASS;
		}
		if (callBool(entity, IS_TRADING)) {
			actionBar(player, "Wait until they're done trading. Kidnapping mid-sale is just rude.");
			return InteractionResult.SUCCESS;
		}
		CompoundTag captive = save(entity);
		if (isColonyWorker(captive)) {
			actionBar(player, "They work for a colony. Poaching staff is beneath you.");
			return InteractionResult.SUCCESS;
		}

		String type = typeId(entity);
		String name = entity.getName().getString();
		grudge(captive, player.getUUID());
		double x = entity.getX();
		double y = entity.getY();
		double z = entity.getZ();
		bag(entity);
		player.setItemInHand(hand, SackItems.full(type, name, captive));

		Cmd.sound(level, "minecraft:entity.villager.hurt", x, y, z, 1f, 1.3f);
		Cmd.sound(level, "minecraft:item.bundle.insert", x, y, z, 1f, 0.6f);
		Cmd.particles(level, "minecraft:poof", x, y + 1, z, 0.3, 0.02, 12);
		player.sendSystemMessage(Component.literal(name).withStyle(ChatFormatting.GOLD)
			.append(Component.literal(BAGGED[RANDOM.nextInt(BAGGED.length)]).withStyle(ChatFormatting.GRAY)));
		int golems = player.isCreative() ? 0 : alertGolems(level, player, x, y, z);
		if (golems > 0) {
			player.sendSystemMessage(Component.literal(golems == 1 ? "An iron golem saw that. Run." : golems + " iron golems saw that. Run.")
				.withStyle(ChatFormatting.RED));
		}
		BurlapSackMod.LOG.info("{} bagged {} ({}) at {} {} {}", player.getName().getString(), name, type, (int) x, (int) y, (int) z);
		return InteractionResult.SUCCESS;
	}

	/** Right-click on a block. */
	static InteractionResult useBlock(ServerPlayer player, ServerLevel level, InteractionHand hand, BlockHitResult hit) {
		ItemStack stack = player.getItemInHand(hand);
		if (!SackItems.isFullSack(stack)) {
			return InteractionResult.PASS;
		}
		BlockPos clicked = hit.getBlockPos();
		BlockPos pos = level.getBlockState(clicked).canBeReplaced() ? clicked : clicked.relative(hit.getDirection());
		if (!roomAt(level, pos)) {
			actionBar(player, "No room to let them out here.");
			return InteractionResult.SUCCESS;
		}
		String name = SackItems.name(stack);
		Entity freed = release(level, stack, pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, player.getYRot() + 180f);
		if (freed == null) {
			player.sendSystemMessage(Component.literal("The sack won't open. " + name + " stays inside for now.").withStyle(ChatFormatting.RED));
			return InteractionResult.SUCCESS;
		}
		player.setItemInHand(hand, SackItems.empty());
		Cmd.sound(level, "minecraft:item.bundle.drop_contents", freed.getX(), freed.getY(), freed.getZ(), 1f, 0.7f);
		Cmd.sound(level, "minecraft:entity.villager.no", freed.getX(), freed.getY(), freed.getZ(), 1f, 1f);
		Cmd.particles(level, "minecraft:poof", freed.getX(), freed.getY() + 1, freed.getZ(), 0.3, 0.02, 12);
		player.sendSystemMessage(Component.literal(name).withStyle(ChatFormatting.GOLD)
			.append(Component.literal(RELEASED[RANDOM.nextInt(RELEASED.length)]).withStyle(ChatFormatting.GRAY)));
		return InteractionResult.SUCCESS;
	}

	/** The entity's full saved data, minus what must not come along. */
	static CompoundTag save(Entity entity) {
		TagValueOutput out = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, entity.level().registryAccess());
		entity.saveWithoutId(out);
		CompoundTag tag = out.buildResult();
		for (String key : STRIP) {
			tag.remove(key);
		}
		if (typeId(entity).equals(WANDERING_TRADER)) {
			tag.putInt("DespawnDelay", 0); // 0 = never leaves: they live here now
		}
		tag.putBoolean("PersistenceRequired", true);
		return tag;
	}

	/** Takes the entity out of the world: frees its bed and workstation in the old village first. */
	static void bag(Entity entity) {
		call(entity, RELEASE_POIS);
		entity.stopRiding();
		entity.ejectPassengers();
		entity.discard();
	}

	/** Summons the captive back into the world. Null if that failed (the sack stays full). */
	static @Nullable Entity release(ServerLevel level, ItemStack sack, double x, double y, double z, float yaw) {
		String type = SackItems.type(sack);
		if (type.isEmpty()) {
			return null;
		}
		UUID id = UUID.randomUUID();
		String saved = SackItems.captive(sack).toString();
		String body = saved.length() > 2 ? "," + saved.substring(1, saved.length() - 1) : "";
		Cmd.run(level, "summon " + type + " " + Cmd.pos(x, y, z)
			+ " {" + Cmd.uuidNbt(id) + ",Rotation:[" + Cmd.f(yaw) + "f,0f]" + body + "}");
		Entity freed = level.getEntity(id);
		if (freed == null) {
			BurlapSackMod.LOG.error("Could not let a {} out of a sack at {} {} {}", type, x, y, z);
		}
		return freed;
	}

	/** Colonycraft tags its workers; they belong to someone's colony and aren't up for grabs. */
	static boolean isColonyWorker(CompoundTag saved) {
		ListTag tags = saved.getListOrEmpty("Tags");
		for (int i = 0; i < tags.size(); i++) {
			if ("colonycraft".equals(tags.getStringOr(i, ""))) {
				return true;
			}
		}
		return false;
	}

	/** Captives remember who did it: their prices for that player go up, and it fades over a few in-game days. */
	static void grudge(CompoundTag saved, UUID kidnapper) {
		ListTag gossips = saved.getListOrEmpty("Gossips").copy();
		CompoundTag entry = new CompoundTag();
		entry.putIntArray("Target", UUIDUtil.uuidToIntArray(kidnapper));
		entry.putString("Type", "minor_negative");
		entry.putInt("Value", GRUDGE);
		gossips.add(entry);
		saved.put("Gossips", gossips);
	}

	/** Iron golems nearby go for the kidnapper. Returns how many noticed. */
	static int alertGolems(ServerLevel level, LivingEntity culprit, double x, double y, double z) {
		AABB near = new AABB(x - WITNESS_RANGE, y - WITNESS_RANGE / 2, z - WITNESS_RANGE, x + WITNESS_RANGE, y + WITNESS_RANGE / 2, z + WITNESS_RANGE);
		int n = 0;
		for (Mob golem : level.getEntitiesOfClass(Mob.class, near, m -> typeId(m).equals(IRON_GOLEM) && m.isAlive())) {
			golem.setLastHurtByMob(culprit);
			golem.setTarget(culprit);
			n++;
		}
		return n;
	}

	private static boolean roomAt(ServerLevel level, BlockPos pos) {
		return level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()
			&& level.getBlockState(pos.above()).getCollisionShape(level, pos.above()).isEmpty();
	}

	static void actionBar(ServerPlayer player, String text) {
		player.connection.send(new ClientboundSetActionBarTextPacket(Component.literal(text).withStyle(ChatFormatting.YELLOW)));
	}

	/** Finds a no-argument method by name on the object's class or its superclasses. */
	static @Nullable Method method(Object target, String name) {
		for (Class<?> c = target.getClass(); c != null; c = c.getSuperclass()) {
			try {
				Method m = c.getDeclaredMethod(name);
				m.setAccessible(true);
				return m;
			} catch (NoSuchMethodException ignored) {
				// try the superclass
			}
		}
		return null;
	}

	private static @Nullable Object call(Object target, String name) {
		Method m = method(target, name);
		if (m == null) {
			BurlapSackMod.LOG.warn("{} has no {}(); skipping it", target.getClass().getSimpleName(), name);
			return null;
		}
		try {
			return m.invoke(target);
		} catch (ReflectiveOperationException | RuntimeException e) {
			BurlapSackMod.LOG.warn("Calling {}() failed", name, e);
			return null;
		}
	}

	private static boolean callBool(Object target, String name) {
		return call(target, name) instanceof Boolean b && b;
	}
}
