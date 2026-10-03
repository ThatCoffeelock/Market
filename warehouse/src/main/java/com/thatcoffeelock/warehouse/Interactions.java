package com.thatcoffeelock.warehouse;

import java.util.Locale;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/** Placing, right-clicking and breaking cores, racks and docks. */
public final class Interactions {
	private Interactions() {
	}

	// ---------------------------------------------------------------- placing (called from the BlockItem mixin)

	/** A block was just placed from this stack. Remembers it if it's one of ours. */
	public static void onPlaced(Level world, @Nullable Player player, BlockPos pos, ItemStack stack) {
		if (!(world instanceof ServerLevel level)) {
			return;
		}
		ServerPlayer sp = player instanceof ServerPlayer p ? p : null;
		if (Parts.isCore(stack)) {
			placedCore(level, sp, pos.immutable(), stack.copyWithCount(1));
		} else if (Parts.isRack(stack)) {
			placedRack(level, sp, pos.immutable());
		} else if (Parts.isDock(stack)) {
			placedDock(level, sp, pos.immutable());
		}
	}

	static void placedCore(ServerLevel level, @Nullable ServerPlayer player, BlockPos pos, ItemStack stack) {
		Warehouse neighbour = Warehouses.touching(level, pos);
		if (neighbour != null) {
			// one manager per building: undo the placement and hand the core back
			WarehouseMod.nextTick(() -> {
				if (Parts.isCoreBlock(level.getBlockState(pos)) && Warehouses.coreAt(level, pos) == null) {
					level.removeBlock(pos, false);
					if (player == null) {
						Block.popResource(level, pos, stack);
					} else if (!player.isCreative()) {
						Parts.give(player, stack);
					}
				}
			});
			tell(player, "This building already has a manager: " + neighbour.name + ". Put the new core somewhere that doesn't touch it.", ChatFormatting.RED);
			return;
		}
		Component custom = stack.get(DataComponents.CUSTOM_NAME);
		String name = custom == null ? "" : Warehouses.clean(custom.getString());
		String packedId = Parts.packedId(stack);
		Warehouse packed = packedId.isEmpty() ? null : Warehouses.byId(packedId);
		if (packed != null && packed.packed) {
			if (!name.isEmpty()) {
				packed.name = name;
			}
			Warehouses.unpack(packed, level, pos);
			Warehouses.refresh();
			tell(player, packed.name + " is unpacked: " + Gui.n(packed.total()) + " items are back on the shelves.", ChatFormatting.GOLD);
			return;
		}
		String owner = player == null ? "" : player.getUUID().toString();
		String ownerName = player == null ? "" : player.getName().getString();
		if (name.isEmpty()) {
			name = ownerName.isEmpty() ? "Warehouse" : ownerName + "'s Warehouse";
		}
		Warehouse w = Warehouses.create(level, pos, owner, ownerName, name);
		Warehouses.refresh();
		tell(player, w.name + " is open for business. Room for " + Gui.n(w.capacity())
			+ " items; every Storage Rack touching it adds " + Gui.n(WarehouseConfig.get().rackCapacity) + ".", ChatFormatting.GOLD);
	}

	static void placedRack(ServerLevel level, @Nullable ServerPlayer player, BlockPos pos) {
		Warehouses.addRack(level, pos);
		Warehouses.refresh();
		Warehouse w = Warehouses.at(level, pos);
		if (w != null) {
			tell(player, "Storage Rack added to " + w.name + ": room for " + Gui.n(w.capacity()) + " items now.", ChatFormatting.AQUA);
		} else if (Warehouses.isDisputed(level, pos)) {
			tell(player, "This rack touches two warehouses, so it counts for neither.", ChatFormatting.RED);
		} else {
			tell(player, "This rack isn't connected to a warehouse yet. Put it against a Warehouse Core or a connected rack.", ChatFormatting.YELLOW);
		}
	}

	static void placedDock(ServerLevel level, @Nullable ServerPlayer player, BlockPos pos) {
		Warehouses.addDock(level, pos);
		int served = Warehouses.near(level, pos, WarehouseConfig.get().dockReach).size();
		tell(player, "Loading Dock built. It serves " + served + (served == 1 ? " warehouse" : " warehouses") + " within "
			+ WarehouseConfig.get().dockReach + " blocks. Sail a ship up to it!", ChatFormatting.YELLOW);
	}

	static void actionBar(ServerPlayer player, Component line) {
		player.connection.send(new ClientboundSetActionBarTextPacket(line));
	}

	private static void tell(@Nullable ServerPlayer player, String text, ChatFormatting color) {
		if (player != null) {
			player.sendSystemMessage(Component.literal(text).withStyle(color));
		}
	}

	// ---------------------------------------------------------------- right-clicking

	static InteractionResult use(ServerPlayer player, ServerLevel level, InteractionHand hand, BlockHitResult hit) {
		BlockPos pos = hit.getBlockPos();
		BlockState state = level.getBlockState(pos);
		Warehouse core = Parts.isCoreBlock(state) ? Warehouses.coreAt(level, pos) : null;
		boolean rack = core == null && Parts.isRackBlock(state) && Warehouses.isRack(level, pos);
		boolean dock = core == null && !rack && Parts.isDockBlock(state) && Warehouses.isDock(level, pos);
		if (core == null && !rack && !dock) {
			return InteractionResult.PASS;
		}
		ItemStack held = player.getItemInHand(hand);
		// sneak + right-click with one of our blocks: let it be placed against this one (building rack walls)
		if (player.isShiftKeyDown() && (Parts.isRack(held) || Parts.isCore(held) || Parts.isDock(held))) {
			return InteractionResult.PASS;
		}
		if (hand != InteractionHand.MAIN_HAND) {
			return InteractionResult.SUCCESS;
		}
		if (dock) {
			DockMenu.open(player, level, pos, WarehouseMod.ahoy() ? AhoyLink.nearestShip(level, pos, player) : null);
			return InteractionResult.SUCCESS;
		}
		Warehouse w = core != null ? core : Warehouses.at(level, pos);
		if (w == null) {
			player.sendSystemMessage(Component.literal(Warehouses.isDisputed(level, pos)
				? "This Storage Rack touches two warehouses, so it counts for neither. Move it."
				: "This Storage Rack isn't connected to a warehouse. Put it against a Warehouse Core or a connected rack.").withStyle(ChatFormatting.YELLOW));
			return InteractionResult.SUCCESS;
		}
		if (core != null && player.isShiftKeyDown() && !held.isEmpty()) {
			depositHeld(player, w, held);
			return InteractionResult.SUCCESS;
		}
		WarehouseMenu.open(player, w, pos, null);
		return InteractionResult.SUCCESS;
	}

	/** Sneak + right-click the core with something in your hand: straight onto the shelves. */
	private static void depositHeld(ServerPlayer player, Warehouse w, ItemStack held) {
		String what = held.getHoverName().getString();
		if (!w.accepts(held)) {
			tell(player, w.name + " only takes " + w.filter.title.toLowerCase(Locale.ROOT) + ".", ChatFormatting.RED);
			return;
		}
		int n = w.deposit(held);
		if (n == 0) {
			tell(player, w.name + " is full. Add Storage Racks!", ChatFormatting.RED);
			return;
		}
		actionBar(player, Component.literal("Stored " + n + " × " + what + " in " + w.name + ".").withStyle(ChatFormatting.GREEN));
	}

	// ---------------------------------------------------------------- breaking

	/** @return false to cancel the vanilla break (we handled it). */
	static boolean beforeBreak(ServerLevel level, Player player, BlockPos pos, BlockState state) {
		Warehouse core = Parts.isCoreBlock(state) ? Warehouses.coreAt(level, pos) : null;
		if (core != null) {
			if (core.locked && !core.mayManage(player)) {
				player.sendSystemMessage(Component.literal(core.name + " is locked. Only " + core.ownerName + " can pack it up.").withStyle(ChatFormatting.RED));
				return false;
			}
			boolean empty = core.total() == 0;
			ItemStack item = Warehouses.pack(core);
			level.removeBlock(pos, false);
			if (!player.isCreative() || !empty) {
				Block.popResource(level, pos, item);
			}
			if (!empty) {
				player.sendSystemMessage(Component.literal(core.name + " is packed up with " + Gui.n(core.total())
					+ " items inside the core. Place it again to unpack.").withStyle(ChatFormatting.GOLD));
			}
			return false;
		}
		if (Parts.isRackBlock(state) && Warehouses.isRack(level, pos)) {
			Warehouse w = Warehouses.at(level, pos);
			if (w != null && w.locked && !w.mayManage(player)) {
				player.sendSystemMessage(Component.literal("That rack belongs to " + w.name + ", which is locked.").withStyle(ChatFormatting.RED));
				return false;
			}
			if (level.getBlockEntity(pos) instanceof Container barrel) {
				if (w != null) {
					Warehouses.pull(w, barrel);
				}
				for (int i = 0; i < barrel.getContainerSize(); i++) {
					ItemStack left = barrel.getItem(i);
					if (!left.isEmpty()) {
						Block.popResource(level, pos, left.copy());
						barrel.setItem(i, ItemStack.EMPTY);
					}
				}
			}
			Warehouses.removeRack(level, pos);
			level.removeBlock(pos, false);
			if (!player.isCreative()) {
				Block.popResource(level, pos, Parts.rack());
			}
			if (w != null) {
				Warehouses.refresh();
				if (w.total() > w.capacity()) {
					player.sendSystemMessage(Component.literal(w.name + " is now over capacity. Nothing is lost, but it only lets things out until there's room again.")
						.withStyle(ChatFormatting.YELLOW));
				}
			}
			return false;
		}
		if (Parts.isDockBlock(state) && Warehouses.isDock(level, pos)) {
			Warehouses.removeDock(level, pos);
			level.removeBlock(pos, false);
			if (!player.isCreative()) {
				Block.popResource(level, pos, Parts.dock());
			}
			return false;
		}
		return true;
	}
}
