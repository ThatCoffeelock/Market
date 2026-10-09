package com.thatcoffeelock.sellswords;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import org.jetbrains.annotations.Nullable;

/** A 6-row chest screen drawn on the server: icons with click actions. Both menus build on it. */
abstract class Ui extends ChestMenu {
	static final int SIZE = 54;

	protected final ServerPlayer viewer;
	protected final SimpleContainer box;
	private final Map<Integer, Runnable> actions = new HashMap<>();

	protected Ui(int syncId, ServerPlayer viewer, SimpleContainer box) {
		super(MenuType.GENERIC_9x6, syncId, viewer.getInventory(), box, 6);
		this.viewer = viewer;
		this.box = box;
	}

	static MutableComponent t(String text, ChatFormatting... formats) {
		return Stations.text(text, formats);
	}

	static ItemStack icon(Item item, Component name, List<Component> lore) {
		ItemStack stack = new ItemStack(item);
		stack.set(DataComponents.ITEM_NAME, name);
		stack.set(DataComponents.LORE, new ItemLore(lore));
		return stack;
	}

	static ItemStack glow(ItemStack stack) {
		stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
		return stack;
	}

	protected void button(int slot, ItemStack icon, @Nullable Runnable action) {
		box.setItem(slot, icon);
		if (action == null) {
			actions.remove(slot);
		} else {
			actions.put(slot, action);
		}
	}

	protected void render() {
		actions.clear();
		for (int i = 0; i < SIZE; i++) {
			box.setItem(i, ItemStack.EMPTY);
		}
		draw();
		for (int i = 0; i < SIZE; i++) {
			if (box.getItem(i).isEmpty()) {
				box.setItem(i, icon(Items.BLACK_STAINED_GLASS_PANE, Component.literal(" "), List.of()));
			}
		}
	}

	protected abstract void draw();

	protected void click() {
		Cmd.sound((net.minecraft.server.level.ServerLevel) viewer.level(), "minecraft:ui.button.click", viewer.getX(), viewer.getY(), viewer.getZ(), 0.4f, 1f);
	}

	protected void close() {
		SellswordsMod.nextTick(viewer::closeContainer);
	}

	@Override
	public void clicked(int slotId, int button, ContainerInput clickType, Player player) {
		if (slotId >= 0 && slotId < SIZE) {
			Runnable action = actions.get(slotId);
			if (action != null && (clickType == ContainerInput.PICKUP || clickType == ContainerInput.QUICK_MOVE)) {
				action.run();
			}
			render();
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

	// ---------------------------------------------------------------- gold

	static int gold(Player player) {
		Inventory inv = player.getInventory();
		int n = 0;
		for (int i = 0; i < inv.getContainerSize(); i++) {
			ItemStack stack = inv.getItem(i);
			if (stack.is(Items.GOLD_INGOT)) {
				n += stack.getCount();
			}
		}
		return n;
	}

	/** Takes gold ingots from the inventory. False (and nothing taken) if there aren't enough. */
	static boolean takeGold(Player player, int count) {
		if (player.isCreative()) {
			return true;
		}
		if (gold(player) < count) {
			return false;
		}
		Inventory inv = player.getInventory();
		int left = count;
		for (int i = 0; i < inv.getContainerSize() && left > 0; i++) {
			ItemStack stack = inv.getItem(i);
			if (stack.is(Items.GOLD_INGOT)) {
				int take = Math.min(left, stack.getCount());
				stack.shrink(take);
				left -= take;
			}
		}
		inv.setChanged();
		return true;
	}

	/** "Following you", "Holding 120 64 -30", "Patrolling around the Mercenary Station". */
	static String ordersText(Merc m) {
		return switch (m.orders()) {
			case FOLLOW -> "Following " + m.ownerName;
			case GUARD -> "Holding " + m.postX + " " + m.postY + " " + m.postZ;
			case STATION -> {
				Station s = Stations.ALL.get(m.home);
				yield s == null ? "Idle" : "Patrolling around the " + s.name;
			}
		};
	}

	static String health(Merc m) {
		var brain = Mercs.brain(m);
		if (brain == null) {
			return "Health: (out of sight)";
		}
		return "Health: " + (int) Math.ceil(brain.getHealth()) + " / " + (int) Math.ceil(brain.getMaxHealth());
	}
}
