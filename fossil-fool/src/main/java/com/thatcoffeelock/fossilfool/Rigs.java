package com.thatcoffeelock.fossilfool;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Interaction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/** Every Drill Rig: placing them, ticking them, finding their model again when it loads, and clicks on it. */
final class Rigs {
	static final Map<String, Rig> BY_ID = new LinkedHashMap<>();
	/** Model roots that loaded, waiting to be matched with their rig at the start of the next tick. */
	private static final List<Entity> PENDING = new ArrayList<>();

	private Rigs() {
	}

	/** Forgets every rig (before loading them from disk). Model roots that already loaded stay pending, so they still match up. */
	static void reset() {
		BY_ID.clear();
	}

	static boolean isRemoved(Rig rig) {
		return BY_ID.get(rig.id) != rig;
	}

	static @Nullable Rig near(String dim, BlockPos pos, int radius) {
		for (Rig rig : BY_ID.values()) {
			if (rig.dimension.equals(dim) && Math.abs(rig.cx - pos.getX()) <= radius && Math.abs(rig.cz - pos.getZ()) <= radius) {
				return rig;
			}
		}
		return null;
	}

	// ---------------------------------------------------------------- the model coming and going

	static void onLoad(Entity entity, ServerLevel level) {
		if (entity.hasAttached(FossilFoolMod.RIG)) {
			PENDING.add(entity);
		}
	}

	static void onUnload(Entity entity, ServerLevel level) {
		String id = entity.getAttached(FossilFoolMod.RIG);
		Rig rig = id == null ? null : BY_ID.get(id);
		if (rig != null && rig.root == entity) {
			rig.root = null;
			rig.headMoved();
		}
	}

	/** Builds the derrick's model. */
	static boolean summon(ServerLevel level, Rig rig) {
		Cmd.run(level, "kill @e[tag=" + rig.tag() + "]");
		UUID id = UUID.randomUUID();
		Cmd.run(level, rig.summonCommand(id));
		Entity root = level.getEntity(id);
		if (root == null) {
			FossilFoolMod.LOG.error("Could not summon the Drill Rig model at {} {} {}", rig.modelX(), rig.modelY(), rig.modelZ());
			return false;
		}
		root.setAttached(FossilFoolMod.RIG, rig.id);
		// passengers come out in the order they were summoned: hitbox, frame, string, head
		List<Entity> parts = root.getPassengers();
		int string = 1 + Rig.FRAME.size();
		if (string < parts.size()) {
			parts.get(string).setAttached(FossilFoolMod.RIG_PART, Rig.STRING_PART);
		}
		for (int i = 0; i < Rig.HEAD.size() && string + 1 + i < parts.size(); i++) {
			parts.get(string + 1 + i).setAttached(FossilFoolMod.RIG_PART, Rig.HEAD_PART + i);
		}
		PENDING.remove(root);
		rig.root = root;
		rig.missing = 0;
		rig.headMoved();
		rig.showHead(level);
		return true;
	}

	static void tick() {
		if (!PENDING.isEmpty()) {
			List<Entity> pending = new ArrayList<>(PENDING);
			PENDING.clear();
			for (Entity root : pending) {
				String id = root.getAttached(FossilFoolMod.RIG);
				Rig rig = id == null ? null : BY_ID.get(id);
				if (root.isRemoved()) {
					continue;
				}
				if (rig == null) {
					// a model whose rig is gone (packed up while unloaded, or a world copied without fossilfool.json)
					for (Entity part : new ArrayList<>(root.getPassengers())) {
						part.discard();
					}
					root.discard();
				} else if (rig.root != root) {
					if (rig.root != null && !rig.root.isRemoved()) {
						for (Entity part : new ArrayList<>(root.getPassengers())) {
							part.discard();
						}
						root.discard();
					} else {
						rig.root = root;
						rig.missing = 0;
						rig.headMoved();
					}
				}
			}
		}
		FossilConfig c = FossilConfig.get();
		for (Rig rig : new ArrayList<>(BY_ID.values())) {
			ServerLevel level = Machines.level(rig.dimension);
			if (level == null) {
				continue;
			}
			BlockPos center = rig.center();
			if (c.keepChunksLoaded && rig.on && rig.state != Rig.State.DONE && rig.age % 20 == 0) {
				level.getChunkSource().addTicketWithRadius(TicketType.ENDER_PEARL, new ChunkPos(rig.cx >> 4, rig.cz >> 4), 2);
			}
			if (!level.isLoaded(center)) {
				rig.age++;
				continue;
			}
			if (rig.root == null || rig.root.isRemoved()) {
				rig.root = null;
				// entities load a little after their chunk; if the model still isn't there after a while, rebuild it
				if (level.isPositionEntityTicking(center) && ++rig.missing > 100) {
					summon(level, rig);
				}
			} else {
				rig.showHead(level);
			}
			try {
				rig.tick(level);
			} catch (RuntimeException e) {
				FossilFoolMod.LOG.error("Drill Rig {} crashed while ticking", rig.id, e);
			}
		}
	}

	static void shutdown() {
		for (Rig rig : BY_ID.values()) {
			rig.root = null;
		}
		PENDING.clear();
	}

	// ---------------------------------------------------------------- placing and packing up

	/** Sets up a rig over the 5×5 square centred on this ground block. Null (and a message) if it can't go there. */
	static @Nullable Rig place(ServerLevel level, @Nullable ServerPlayer player, BlockPos ground) {
		String dim = Machines.dim(level);
		Rig other = near(dim, ground, 7);
		if (other != null) {
			tell(player, "Too close to another Drill Rig. Shafts need at least 8 blocks between their middles.");
			return null;
		}
		if (ground.getY() - 1 < level.getMinY() || ground.getY() >= level.getMaxY() - 8) {
			tell(player, "A Drill Rig can't stand here.");
			return null;
		}
		for (int dy = 1; dy <= 2; dy++) {
			if (!level.getBlockState(ground.above(dy)).canBeReplaced()) {
				tell(player, "Clear the space above the middle of the shaft first.");
				return null;
			}
		}
		Rig rig = new Rig(UUID.randomUUID().toString().substring(0, 8), dim, ground.getX(), ground.getY(), ground.getZ());
		if (player != null) {
			rig.owner = player.getUUID().toString();
			rig.ownerName = player.getName().getString();
		}
		BY_ID.put(rig.id, rig);
		if (!summon(level, rig)) {
			BY_ID.remove(rig.id);
			tell(player, "Something went wrong building the rig. Check the server log.");
			return null;
		}
		Store.changed();
		return rig;
	}

	/** Takes the rig down: the model goes, the rig item and everything inside it go to the player (or the ground). */
	static void packUp(Rig rig, @Nullable ServerPlayer player) {
		ServerLevel level = Machines.level(rig.dimension);
		BY_ID.remove(rig.id);
		Store.changed();
		if (level == null) {
			return;
		}
		Cmd.run(level, "kill @e[tag=" + rig.tag() + "]");
		List<ItemStack> things = new ArrayList<>();
		things.add(OilItems.rig());
		for (Container box : List.of(rig.firebox, rig.ores, rig.stone)) {
			for (int i = 0; i < box.getContainerSize(); i++) {
				ItemStack stack = box.getItem(i);
				if (!stack.isEmpty()) {
					things.add(stack.copy());
					box.setItem(i, ItemStack.EMPTY);
				}
			}
		}
		for (ItemStack stack : things) {
			if (player != null) {
				OilItems.give(player, stack);
			} else {
				Block.popResource(level, rig.center().above(), stack);
			}
		}
		if (player != null) {
			Cmd.sound(level, "minecraft:entity.item.pickup", player.getX(), player.getY() + 1, player.getZ(), 0.6f, 0.8f);
			player.sendSystemMessage(Component.literal("Drill Rig packed up. The shaft stays.").withStyle(ChatFormatting.GOLD));
		}
	}

	private static void tell(@Nullable ServerPlayer player, String text) {
		if (player != null) {
			player.sendSystemMessage(Component.literal(text).withStyle(ChatFormatting.RED));
		}
	}

	// ---------------------------------------------------------------- clicks

	/** Right-clicking the ground with a rig item sets it up there. */
	static InteractionResult useKit(ServerPlayer player, ServerLevel level, InteractionHand hand, BlockHitResult hit) {
		ItemStack held = player.getItemInHand(hand);
		if (!OilItems.isRig(held)) {
			return InteractionResult.PASS;
		}
		BlockPos clicked = hit.getBlockPos();
		BlockPos ground = level.getBlockState(clicked).canBeReplaced() ? clicked.below() : clicked;
		Rig rig = place(level, player, ground);
		if (rig == null) {
			return InteractionResult.SUCCESS;
		}
		if (!player.isCreative()) {
			held.shrink(1);
		}
		Cmd.sound(level, "minecraft:block.anvil.place", rig.modelX(), rig.modelY(), rig.modelZ(), 0.7f, 0.6f);
		player.sendSystemMessage(Component.literal("The Drill Rig is up. ").withStyle(ChatFormatting.GOLD)
			.append(Component.literal("Right-click it and put fuel in its firebox: coal, lava, crude or diesel.").withStyle(ChatFormatting.YELLOW)));
		return InteractionResult.SUCCESS;
	}

	/** Right-clicking the derrick opens its screen. */
	static InteractionResult useHitbox(ServerPlayer player, ServerLevel level, InteractionHand hand, Entity hitbox) {
		if (!(hitbox instanceof Interaction) || hitbox.getVehicle() == null) {
			return InteractionResult.PASS;
		}
		String id = hitbox.getVehicle().getAttached(FossilFoolMod.RIG);
		Rig rig = id == null ? null : BY_ID.get(id);
		if (rig == null) {
			return InteractionResult.PASS;
		}
		if (hand != InteractionHand.MAIN_HAND) {
			return InteractionResult.SUCCESS;
		}
		if (!rig.isOwner(player) && !player.isCreative()) {
			player.sendSystemMessage(Component.literal("That's " + rig.ownerName + "'s Drill Rig. Go dig your own hole.").withStyle(ChatFormatting.RED));
			return InteractionResult.SUCCESS;
		}
		RigMenu.open(player, rig, level);
		return InteractionResult.SUCCESS;
	}
}
