package com.thatcoffeelock.colonycraft;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
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

/**
 * A cell's holding block: a 3-row chest screen. The middle slot is the lock. Put full shackles in and the
 * prisoner appears in the cell; take them out and the prisoner goes back into the shackles. The buttons
 * ransom whoever's inside, execute them in the cell for the bounty, or send them to the scaffold for more.
 */
final class HoldingMenu extends ChestMenu {
	private static final int SIZE = 27;
	private static final int LOCK = 13;
	private static final int RANSOM = 11;
	private static final int EXECUTE = 15;
	private static final int PUBLIC = 16;
	private static final int CLOSE = 22;

	private final ServerPlayer viewer;
	private final Colony.Building cellblock;
	private final int cell;
	private final SimpleContainer box;
	/** The button that's been clicked once and waits for the second click, or -1. */
	private int confirm = -1;

	static void open(ServerPlayer player, Colony.Building cellblock, int cell) {
		player.openMenu(new SimpleMenuProvider((id, inv, p) -> new HoldingMenu(id, player, cellblock, cell),
			Component.literal(cellblock.colony.name + " · Cell " + (cell + 1)).withStyle(ChatFormatting.DARK_GRAY)));
	}

	private HoldingMenu(int syncId, ServerPlayer viewer, Colony.Building cellblock, int cell) {
		this(syncId, viewer, cellblock, cell, new SimpleContainer(SIZE));
	}

	private HoldingMenu(int syncId, ServerPlayer viewer, Colony.Building cellblock, int cell, SimpleContainer box) {
		super(MenuType.GENERIC_9x3, syncId, viewer.getInventory(), box, 3);
		this.viewer = viewer;
		this.cellblock = cellblock;
		this.cell = cell;
		this.box = box;
		render();
	}

	private static ItemStack icon(Item item, Component name, List<Component> lore) {
		ItemStack stack = new ItemStack(item);
		stack.set(DataComponents.ITEM_NAME, name);
		stack.set(DataComponents.LORE, new ItemLore(lore));
		return stack;
	}

	private static Component t(String text, ChatFormatting... formats) {
		return Blueprints.text(text, formats);
	}

	private ServerLevel level() {
		return (ServerLevel) viewer.level();
	}

	private @Nullable Prison.Prisoner inmate() {
		return Prison.inmate(level(), cellblock, cell);
	}

	private void render() {
		ItemStack filler = icon(TownHallMenu.item("minecraft:gray_stained_glass_pane", Items.GLASS_PANE), Component.literal(" "), List.of());
		for (int i = 0; i < SIZE; i++) {
			box.setItem(i, filler.copy());
		}
		Prison.Prisoner p = inmate();
		List<Component> info = new ArrayList<>();
		if (p == null) {
			info.add(t("Empty. Put full shackles in the middle", ChatFormatting.GRAY));
			info.add(t("slot to lock their prisoner up here.", ChatFormatting.GRAY));
		} else {
			long days = Math.max(0, level().getGameTime() - p.since()) / Colonies.DAY + 1;
			info.add(t(p.name() + ", " + Prison.kind(p.type()), ChatFormatting.WHITE));
			info.add(t("Day " + days + " behind bars.", ChatFormatting.GRAY));
			info.add(t("Upkeep: " + Bank.format(Bank.cents(Prison.UPKEEP)) + " a day, with the wages.", ChatFormatting.DARK_GRAY));
		}
		box.setItem(4, icon(Items.IRON_BARS, t("Cell " + (cell + 1) + " of the " + cellblock.title(), ChatFormatting.GOLD, ChatFormatting.BOLD), info));

		if (p == null) {
			box.setItem(LOCK, icon(TownHallMenu.item("minecraft:light_gray_stained_glass_pane", Items.GLASS_PANE), t("Put shackles here", ChatFormatting.YELLOW),
				List.of(t("Click with full shackles, or shift-click", ChatFormatting.GRAY), t("them in from your inventory.", ChatFormatting.GRAY))));
		} else {
			ItemStack held = Prison.emptyShackles();
			held.set(DataComponents.ITEM_NAME, Component.literal("Shackles (" + p.name() + ")").withStyle(ChatFormatting.GOLD));
			held.set(DataComponents.LORE, new ItemLore(List.of(t("Locked in this cell.", ChatFormatting.WHITE),
				t("Take the shackles out to take the", ChatFormatting.GRAY), t("prisoner with you (to another cell).", ChatFormatting.GRAY))));
			held.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
			box.setItem(LOCK, held);
			long bounty = Bank.cents(Prison.BOUNTY.getOrDefault(p.type(), 0.0));
			long now = level().getGameTime();
			long ransom = Prison.ransomValue(p.type(), Prison.daysHeld(p, now));
			long max = Prison.ransomValue(p.type(), Long.MAX_VALUE / Colonies.DAY);
			box.setItem(RANSOM, icon(confirm == RANSOM ? Items.EMERALD_BLOCK : Items.EMERALD,
				t(confirm == RANSOM ? "Click again to accept the ransom" : "Ransom", ChatFormatting.GREEN, ChatFormatting.BOLD),
				List.of(t("The illagers offer " + Bank.format(ransom) + " today.", ChatFormatting.GOLD),
					t("Their offer grows every day you hold them,", ChatFormatting.GRAY),
					t("up to " + Bank.format(max) + ". Meanwhile they eat.", ChatFormatting.GRAY),
					t("An envoy takes them home. They're gone for good.", ChatFormatting.DARK_GRAY))));
			box.setItem(EXECUTE, icon(confirm == EXECUTE ? Items.WITHER_SKELETON_SKULL : Items.IRON_AXE,
				t(confirm == EXECUTE ? "Click again to execute" : "Execute in the cell", ChatFormatting.RED, ChatFormatting.BOLD),
				List.of(t("Bounty: " + Bank.format(bounty), ChatFormatting.GOLD), t("Quick and quiet. Nothing drops.", ChatFormatting.GRAY),
					t("You get your shackles back, empty.", ChatFormatting.GRAY))));
			String why = Prison.whyNotPublic(level(), cellblock.colony);
			Colony.Building scaffold = Prison.scaffold(cellblock.colony);
			List<Component> lore = new ArrayList<>();
			if (why == null && scaffold != null) {
				lore.add(t("Bounty and ticket sales: " + Bank.format(Bank.cents(Prison.BOUNTY.getOrDefault(p.type(), 0.0)
					* Prison.publicFactor(scaffold.tier))), ChatFormatting.GOLD));
				lore.add(t("Up the scaffold, the whole server is told,", ChatFormatting.GRAY));
				lore.add(t("the bell tolls, the crowd cheers.", ChatFormatting.GRAY));
			} else {
				lore.add(t(why == null ? "No scaffold." : why, ChatFormatting.RED));
			}
			box.setItem(PUBLIC, icon(confirm == PUBLIC ? Items.BELL : Items.WITHER_SKELETON_SKULL,
				t(confirm == PUBLIC ? "Click again: to the scaffold!" : "Public execution", ChatFormatting.DARK_RED, ChatFormatting.BOLD), lore));
		}
		box.setItem(CLOSE, icon(Items.BARRIER, t("Close", ChatFormatting.RED), List.of()));
	}

	// ---------------------------------------------------------------- actions

	private void lock(ItemStack shackles, Runnable taken) {
		String why = Prison.lockUp(level(), cellblock, cell, shackles);
		if (why != null) {
			nope(why);
			return;
		}
		taken.run();
		confirm = -1;
	}

	/** Buttons that do something drastic need a second click. True when this is it. */
	private boolean confirmed(int button) {
		if (confirm != button) {
			confirm = button;
			return false;
		}
		confirm = -1;
		return true;
	}

	private void execute() {
		Prison.Prisoner p = inmate();
		if (p == null || !confirmed(EXECUTE)) {
			return;
		}
		long bounty = Prison.execute(level(), cellblock, cell);
		if (bounty < 0) {
			nope("Nobody to execute here.");
			return;
		}
		Blueprints.give(viewer, Prison.emptyShackles());
		viewer.sendSystemMessage(Component.literal("Justice is served. ").withStyle(ChatFormatting.DARK_RED)
			.append(Component.literal(p.name() + Prison.epitaph(p.type()) + " Bounty: ").withStyle(ChatFormatting.GRAY))
			.append(Bank.text(bounty)));
	}

	private void ransom() {
		Prison.Prisoner p = inmate();
		if (p == null || !confirmed(RANSOM)) {
			return;
		}
		long paid = Prison.ransom(level(), cellblock, cell);
		if (paid < 0) {
			nope("Nobody to ransom here.");
			return;
		}
		Blueprints.give(viewer, Prison.emptyShackles());
		viewer.sendSystemMessage(Component.literal("An illager envoy rides in under a white flag, counts out ").withStyle(ChatFormatting.GREEN)
			.append(Bank.text(paid)).append(Component.literal(" and takes " + p.name() + " home. Pleasure doing business.")
				.withStyle(ChatFormatting.GREEN)));
	}

	private void publicExecution() {
		Prison.Prisoner p = inmate();
		if (p == null) {
			return;
		}
		String why = Prison.whyNotPublic(level(), cellblock.colony);
		if (why != null) {
			nope(why);
			return;
		}
		if (!confirmed(PUBLIC)) {
			return;
		}
		long paid = Prison.publicExecution(level(), cellblock, cell);
		if (paid < 0) {
			nope("The show can't go on right now.");
			return;
		}
		Blueprints.give(viewer, Prison.emptyShackles());
		viewer.sendSystemMessage(Component.literal(p.name() + " is marched up the scaffold. Bounty and ticket sales: ").withStyle(ChatFormatting.GOLD)
			.append(Bank.text(paid)));
	}

	private void nope(String why) {
		Cmd.sound(level(), "minecraft:entity.villager.no", viewer.getX(), viewer.getY(), viewer.getZ(), 0.6f, 1.0f);
		viewer.sendSystemMessage(Component.literal(why).withStyle(ChatFormatting.RED));
	}

	// ---------------------------------------------------------------- slots

	@Override
	public void clicked(int slotId, int button, ContainerInput clickType, Player player) {
		boolean click = clickType == ContainerInput.PICKUP || clickType == ContainerInput.QUICK_MOVE;
		if (slotId == LOCK && click) {
			ItemStack carried = getCarried();
			if (inmate() != null) {
				if (!carried.isEmpty()) {
					nope("Put down what you're holding first.");
				} else {
					ItemStack out = Prison.takeOut(level(), cellblock, cell);
					if (out != null) {
						if (clickType == ContainerInput.QUICK_MOVE) {
							Blueprints.give(viewer, out);
						} else {
							setCarried(out);
						}
						confirm = -1;
					}
				}
			} else if (!carried.isEmpty()) {
				lock(carried, () -> setCarried(ItemStack.EMPTY));
			}
		} else if (slotId == EXECUTE && click) {
			execute();
		} else if (slotId == RANSOM && click) {
			ransom();
		} else if (slotId == PUBLIC && click) {
			publicExecution();
		} else if (slotId == CLOSE && click) {
			ColonycraftMod.nextTick(viewer::closeContainer);
		} else if (slotId >= SIZE && slotId < slots.size() && clickType == ContainerInput.QUICK_MOVE) {
			Slot from = slots.get(slotId);
			ItemStack stack = from.getItem();
			if (Prison.isShackles(stack)) {
				lock(stack, () -> from.set(ItemStack.EMPTY));
			}
		} else if (slotId < 0 || slotId >= SIZE) {
			super.clicked(slotId, button, clickType, player);
			return;
		}
		render();
		sendAllDataToRemote();
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
	public boolean stillValid(Player player) {
		BlockPos at = cellblock.world(BuildingType.holding(cell));
		return cellblock.colony.buildings.contains(cellblock)
			&& player.distanceToSqr(at.getX() + 0.5, at.getY() + 0.5, at.getZ() + 0.5) <= 64;
	}

	@Override
	public void removed(Player player) {
		box.clearContent();
		super.removed(player);
	}
}
