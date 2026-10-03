package com.thatcoffeelock.warehouse;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * A chest screen drawn entirely on the server, so vanilla clients can use it. The top part is ours: clicking a slot
 * there runs an action instead of moving items. The player's own inventory below works as usual.
 */
abstract class BaseMenu extends ChestMenu {
	@FunctionalInterface
	interface Action {
		void run(int button, ContainerInput type);
	}

	protected final ServerPlayer viewer;
	protected final SimpleContainer box;
	protected final int size;
	private final Map<Integer, Action> actions = new HashMap<>();

	protected BaseMenu(int syncId, ServerPlayer viewer, int rows) {
		this(syncId, viewer, rows, new SimpleContainer(rows * 9));
	}

	private BaseMenu(int syncId, ServerPlayer viewer, int rows, SimpleContainer box) {
		super(rows == 6 ? MenuType.GENERIC_9x6 : MenuType.GENERIC_9x3, syncId, viewer.getInventory(), box, rows);
		this.viewer = viewer;
		this.box = box;
		this.size = rows * 9;
	}

	protected void clearButtons() {
		actions.clear();
		for (int i = 0; i < size; i++) {
			box.setItem(i, ItemStack.EMPTY);
		}
	}

	protected void button(int slot, ItemStack icon, @Nullable Action action) {
		box.setItem(slot, icon);
		if (action == null) {
			actions.remove(slot);
		} else {
			actions.put(slot, action);
		}
	}

	protected void fill(int from, int to) {
		for (int i = from; i < to; i++) {
			if (box.getItem(i).isEmpty()) {
				box.setItem(i, Gui.filler());
			}
		}
	}

	/** Clicked one of our slots. Returns true if it was handled without an action (e.g. dropping an item in). */
	protected boolean clickedOwnSlot(int slot, int button, ContainerInput type) {
		return false;
	}

	protected void click() {
		sound(SoundEvents.UI_BUTTON_CLICK.value(), 0.4f, 1.0f);
	}

	protected void stored() {
		sound(SoundEvents.EXPERIENCE_ORB_PICKUP, 0.5f, 1.6f);
	}

	protected void nope(String why) {
		sound(SoundEvents.VILLAGER_NO, 0.6f, 1.0f);
		viewer.sendSystemMessage(Component.literal(why).withStyle(ChatFormatting.RED));
	}

	private void sound(SoundEvent sound, float volume, float pitch) {
		viewer.level().playSound(null, viewer.getX(), viewer.getY(), viewer.getZ(), sound, SoundSource.PLAYERS, volume, pitch);
	}

	@Override
	public void clicked(int slotId, int button, ContainerInput clickType, Player player) {
		if (slotId >= 0 && slotId < size) {
			if (!clickedOwnSlot(slotId, button, clickType)) {
				Action action = actions.get(slotId);
				if (action != null && (clickType == ContainerInput.PICKUP || clickType == ContainerInput.QUICK_MOVE)) {
					action.run(button, clickType);
				}
			}
			sendAllDataToRemote();
			return;
		}
		super.clicked(slotId, button, clickType, player);
	}

	@Override
	public ItemStack quickMoveStack(Player player, int index) {
		return ItemStack.EMPTY;
	}

	@Override
	public boolean canTakeItemForPickAll(ItemStack stack, Slot slot) {
		return slot.container != box && super.canTakeItemForPickAll(stack, slot);
	}

	@Override
	public boolean canDragTo(Slot slot) {
		return slot.container != box && super.canDragTo(slot);
	}

	@Override
	public void removed(Player player) {
		box.clearContent();
		super.removed(player);
	}
}
