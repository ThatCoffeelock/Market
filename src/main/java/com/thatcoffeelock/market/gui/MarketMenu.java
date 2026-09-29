package com.thatcoffeelock.market.gui;

import java.util.HashMap;
import java.util.Map;

import com.thatcoffeelock.market.Gui;
import com.thatcoffeelock.market.MarketData;
import net.minecraft.core.BlockPos;
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
 * A 6-row chest screen rendered entirely on the server, so vanilla clients can use it.
 * Slots marked as buttons can't be taken from; clicking them runs an action instead.
 */
public abstract class MarketMenu extends ChestMenu {
	public static final int SIZE = 54;

	@FunctionalInterface
	public interface Action {
		void run(int button, ContainerInput type);
	}

	protected final ServerPlayer viewer;
	protected final SimpleContainer box;
	protected final @Nullable BlockPos origin;
	private final Map<Integer, Action> actions = new HashMap<>();

	protected MarketMenu(int syncId, ServerPlayer viewer, @Nullable BlockPos origin) {
		this(syncId, viewer, origin, new SimpleContainer(SIZE));
	}

	private MarketMenu(int syncId, ServerPlayer viewer, @Nullable BlockPos origin, SimpleContainer box) {
		super(MenuType.GENERIC_9x6, syncId, viewer.getInventory(), box, 6);
		this.viewer = viewer;
		this.box = box;
		this.origin = origin;
	}

	/** Button slots are GUI-only. Everything else behaves like a normal chest slot. */
	protected boolean isButton(int slot) {
		return slot >= 0 && slot < SIZE;
	}

	protected void clearButtons() {
		actions.clear();
		for (int i = 0; i < SIZE; i++) {
			if (isButton(i)) {
				box.setItem(i, ItemStack.EMPTY);
			}
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

	protected void fillEmptyButtons() {
		for (int i = 0; i < SIZE; i++) {
			if (isButton(i) && box.getItem(i).isEmpty()) {
				box.setItem(i, Gui.filler());
			}
		}
	}

	protected void click() {
		sound(SoundEvents.UI_BUTTON_CLICK.value(), 0.4f, 1.0f);
	}

	protected void kaching() {
		sound(SoundEvents.EXPERIENCE_ORB_PICKUP, 0.6f, 1.3f);
	}

	protected void nope() {
		sound(SoundEvents.VILLAGER_NO, 0.6f, 1.0f);
	}

	private void sound(SoundEvent sound, float volume, float pitch) {
		viewer.level().playSound(null, viewer.getX(), viewer.getY(), viewer.getZ(), sound, SoundSource.PLAYERS, volume, pitch);
	}

	@Override
	public void clicked(int slotId, int button, ContainerInput clickType, Player player) {
		if (isButton(slotId)) {
			Action action = actions.get(slotId);
			if (action != null && (clickType == ContainerInput.PICKUP || clickType == ContainerInput.QUICK_MOVE)) {
				action.run(button, clickType);
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
		return !isButtonSlot(slot) && super.canTakeItemForPickAll(stack, slot);
	}

	@Override
	public boolean canDragTo(Slot slot) {
		return !isButtonSlot(slot) && super.canDragTo(slot);
	}

	private boolean isButtonSlot(Slot slot) {
		return slot.container == box && isButton(slot.getContainerSlot());
	}

	@Override
	public boolean stillValid(Player player) {
		if (origin == null) {
			return true;
		}
		return MarketData.isMarket(player.level(), origin)
			&& player.distanceToSqr(origin.getX() + 0.5, origin.getY() + 0.5, origin.getZ() + 0.5) <= 64.0;
	}
}
