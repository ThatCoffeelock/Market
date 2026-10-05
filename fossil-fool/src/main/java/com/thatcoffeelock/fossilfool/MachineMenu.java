package com.thatcoffeelock.fossilfool;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * A chest screen drawn on the server, so vanilla clients can use it. The top row is buttons; the rows below are real
 * slots that show a machine's own containers (its firebox, its holds), so items move in and out the normal way.
 */
abstract class MachineMenu extends ChestMenu {
	/** Every open machine screen, so they can be redrawn while the machine works. */
	private static final List<MachineMenu> OPEN = new ArrayList<>();

	@FunctionalInterface
	interface Action {
		void run(boolean shift);
	}

	/** One container shown in the screen, starting at a slot, and whether players may put things into it. */
	record Section(Container box, int start, boolean insert) {
	}

	/** The container behind the screen: buttons in the top row, the machine's containers below. */
	static final class View extends SimpleContainer {
		final ItemStack[] icons = new ItemStack[9];
		private final List<Section> sections;
		private final BooleanSupplier valid;

		View(int rows, List<Section> sections, BooleanSupplier valid) {
			super(rows * 9);
			this.sections = sections;
			this.valid = valid;
			java.util.Arrays.fill(icons, ItemStack.EMPTY);
		}

		private @Nullable Section section(int slot) {
			for (Section s : sections) {
				if (slot >= s.start() && slot < s.start() + s.box().getContainerSize()) {
					return s;
				}
			}
			return null;
		}

		@Override
		public ItemStack getItem(int slot) {
			if (slot < 9) {
				return icons[slot];
			}
			Section s = section(slot);
			return s == null ? ItemStack.EMPTY : s.box().getItem(slot - s.start());
		}

		@Override
		public void setItem(int slot, ItemStack stack) {
			Section s = slot < 9 ? null : section(slot);
			if (s != null) {
				s.box().setItem(slot - s.start(), stack);
			}
		}

		@Override
		public ItemStack removeItem(int slot, int count) {
			Section s = slot < 9 ? null : section(slot);
			return s == null ? ItemStack.EMPTY : s.box().removeItem(slot - s.start(), count);
		}

		@Override
		public ItemStack removeItemNoUpdate(int slot) {
			Section s = slot < 9 ? null : section(slot);
			return s == null ? ItemStack.EMPTY : s.box().removeItemNoUpdate(slot - s.start());
		}

		@Override
		public boolean canPlaceItem(int slot, ItemStack stack) {
			Section s = slot < 9 ? null : section(slot);
			return s != null && s.insert() && s.box().canPlaceItem(slot - s.start(), stack);
		}

		@Override
		public boolean isEmpty() {
			return false;
		}

		@Override
		public void setChanged() {
			for (Section s : sections) {
				s.box().setChanged();
			}
		}

		@Override
		public boolean stillValid(Player player) {
			return valid.getAsBoolean();
		}

		/** Never: the screen closing must not empty the machine. */
		@Override
		public void clearContent() {
		}
	}

	protected final ServerPlayer viewer;
	protected final View view;
	private final Action[] actions = new Action[9];

	protected MachineMenu(int syncId, ServerPlayer viewer, int rows, View view) {
		super(type(rows), syncId, viewer.getInventory(), view, rows);
		this.viewer = viewer;
		this.view = view;
		OPEN.add(this);
	}

	private static MenuType<?> type(int rows) {
		return switch (rows) {
			case 2 -> MenuType.GENERIC_9x2;
			case 3 -> MenuType.GENERIC_9x3;
			case 4 -> MenuType.GENERIC_9x4;
			case 5 -> MenuType.GENERIC_9x5;
			default -> MenuType.GENERIC_9x6;
		};
	}

	/** Redraws every open machine screen (called a couple of times a second). */
	static void refreshAll() {
		for (MachineMenu menu : new ArrayList<>(OPEN)) {
			try {
				menu.render();
			} catch (RuntimeException e) {
				FossilFoolMod.LOG.warn("Could not redraw a machine screen", e);
			}
		}
	}

	static void closeAll() {
		OPEN.clear();
	}

	/** Draws the button row. Subclasses call {@link #button} for each button. */
	abstract void render();

	protected void button(int slot, ItemStack icon, @Nullable Action action) {
		view.icons[slot] = icon;
		actions[slot] = action;
	}

	protected void fillRow() {
		for (int i = 0; i < 9; i++) {
			if (view.icons[i].isEmpty()) {
				view.icons[i] = Gui.filler();
			}
		}
	}

	protected void click() {
		sound(SoundEvents.UI_BUTTON_CLICK.value(), 0.4f, 1.0f);
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
		if (slotId >= 0 && slotId < 9) {
			Action action = actions[slotId];
			if (action != null && (clickType == ContainerInput.PICKUP || clickType == ContainerInput.QUICK_MOVE)) {
				action.run(clickType == ContainerInput.QUICK_MOVE);
				render();
			}
			sendAllDataToRemote();
			return;
		}
		super.clicked(slotId, button, clickType, player);
	}

	@Override
	public boolean canTakeItemForPickAll(ItemStack stack, Slot slot) {
		return slot.index >= 9 && super.canTakeItemForPickAll(stack, slot);
	}

	@Override
	public boolean canDragTo(Slot slot) {
		return slot.index >= 9 && super.canDragTo(slot);
	}

	@Override
	public void removed(Player player) {
		OPEN.remove(this);
		super.removed(player);
	}
}
