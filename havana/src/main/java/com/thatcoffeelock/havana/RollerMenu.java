package com.thatcoffeelock.havana;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;

/**
 * The Cigar Roller: a 3-row chest screen rendered on the server, so vanilla clients can use it.
 * <pre>
 *  .  [T label]  .  [F label]  .  .  .  .  .
 *  .  TOBACCO    .  FLAVOR     .  .  ROLL  .  HELP
 *  .  .          .  .          .  .  .     .  .
 * </pre>
 * Only the two input slots are real. The roll slot shows the cigar you'd get: click it to roll one, shift-click
 * to roll as many as you can. Whatever is left in the inputs goes back to the player when the screen closes.
 */
final class RollerMenu extends ChestMenu {
	static final int SIZE = 27;
	static final int TOBACCO = 10;
	static final int FLAVOR = 12;
	static final int ROLL = 15;
	private static final int HELP = 17;

	final SimpleContainer box;
	private final ServerPlayer viewer;
	private final ServerLevel level;
	private final BlockPos table;
	private String shown = "";

	RollerMenu(int syncId, ServerPlayer viewer, ServerLevel level, BlockPos table) {
		this(syncId, viewer, level, table, new SimpleContainer(SIZE));
	}

	private RollerMenu(int syncId, ServerPlayer viewer, ServerLevel level, BlockPos table, SimpleContainer box) {
		super(MenuType.GENERIC_9x3, syncId, viewer.getInventory(), box, 3);
		this.box = box;
		this.viewer = viewer;
		this.level = level;
		this.table = table;
		ItemStack filler = icon(HavanaItems.item("minecraft:black_stained_glass_pane", Items.GLASS_PANE), Component.literal(" "));
		for (int i = 0; i < SIZE; i++) {
			if (isButton(i)) {
				box.setItem(i, filler.copy());
			}
		}
		box.setItem(TOBACCO - 9, icon(HavanaItems.item("minecraft:brown_stained_glass_pane", Items.GLASS_PANE),
			HavanaItems.text("▼ Tobacco ▼", ChatFormatting.GOLD),
			HavanaItems.text("Cured or Aged Tobacco, " + Rolling.LEAVES_PER_CIGAR + " per cigar.", ChatFormatting.GRAY)));
		box.setItem(FLAVOR - 9, icon(HavanaItems.item("minecraft:orange_stained_glass_pane", Items.GLASS_PANE),
			HavanaItems.text("▼ Flavor (optional) ▼", ChatFormatting.YELLOW),
			HavanaItems.text("One per cigar:", ChatFormatting.GRAY),
			HavanaItems.text("Honey Bottle, Cocoa Beans, Sweet Berries,", ChatFormatting.GRAY),
			HavanaItems.text("Glow Berries or Blaze Powder.", ChatFormatting.GRAY)));
		box.setItem(HELP, icon(Items.BOOK, HavanaItems.text("How to roll", ChatFormatting.YELLOW),
			HavanaItems.text("1. Grow tobacco. 2. Cure the leaves in a Curing Barrel.", ChatFormatting.GRAY),
			HavanaItems.text("3. Put " + Rolling.LEAVES_PER_CIGAR + " cured tobacco per cigar in the tobacco slot.", ChatFormatting.GRAY),
			HavanaItems.text("Aged tobacco makes Gran Reserva cigars.", ChatFormatting.LIGHT_PURPLE),
			HavanaItems.text("Click the cigar to roll one. Shift-click: roll them all.", ChatFormatting.DARK_GRAY)));
		render();
	}

	private static ItemStack icon(Item item, Component name, Component... lore) {
		ItemStack stack = new ItemStack(item);
		stack.set(DataComponents.ITEM_NAME, name);
		stack.set(DataComponents.LORE, new ItemLore(List.of(lore)));
		return stack;
	}

	private static boolean isButton(int slot) {
		return slot >= 0 && slot < SIZE && slot != TOBACCO && slot != FLAVOR;
	}

	/** Updates the roll button for what's in the inputs. */
	private void render() {
		Rolling.Plan one = Rolling.plan(box.getItem(TOBACCO), box.getItem(FLAVOR), 1);
		if (!one.ok()) {
			box.setItem(ROLL, icon(HavanaItems.item("minecraft:barrier", Items.PAPER), HavanaItems.text("Nothing to roll", ChatFormatting.RED),
				HavanaItems.text(one.problem(), ChatFormatting.GRAY)));
			return;
		}
		int max = Rolling.plan(box.getItem(TOBACCO), box.getItem(FLAVOR), 64).cigars().getCount();
		ItemStack preview = one.cigars().copy();
		List<Component> lore = new ArrayList<>(preview.getOrDefault(DataComponents.LORE, ItemLore.EMPTY).lines());
		lore.add(Component.empty());
		lore.add(HavanaItems.text("Click: roll one.", ChatFormatting.GREEN));
		lore.add(HavanaItems.text("Shift-click: roll all " + max + ".", ChatFormatting.GREEN));
		preview.set(DataComponents.LORE, new ItemLore(lore));
		preview.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
		box.setItem(ROLL, preview);
	}

	/** Rolls up to {@code wanted} cigars into the player's inventory. Returns how many. */
	int roll(int wanted) {
		Rolling.Plan plan = Rolling.plan(box.getItem(TOBACCO), box.getItem(FLAVOR), wanted);
		if (!plan.ok()) {
			Cmd.sound(level, "minecraft:entity.villager.no", viewer.getX(), viewer.getY(), viewer.getZ(), 0.6f, 1f);
			return 0;
		}
		box.getItem(TOBACCO).shrink(plan.tobaccoUsed());
		if (plan.flavorUsed() > 0) {
			box.getItem(FLAVOR).shrink(plan.flavorUsed());
		}
		box.setChanged();
		HavanaItems.give(viewer, plan.cigars().copy());
		if (!plan.remainder().isEmpty()) {
			HavanaItems.give(viewer, plan.remainder().copy());
		}
		Cmd.sound(level, "minecraft:item.book.page_turn", viewer.getX(), viewer.getY(), viewer.getZ(), 1f, 0.8f);
		Cmd.sound(level, "minecraft:ui.cartography_table.take_result", viewer.getX(), viewer.getY(), viewer.getZ(), 0.7f, 1.2f);
		render();
		return plan.cigars().getCount();
	}

	/** Re-renders whenever the inputs change. Runs every tick while the screen is open. */
	@Override
	public void broadcastChanges() {
		String now = describe(box.getItem(TOBACCO)) + "|" + describe(box.getItem(FLAVOR));
		if (!now.equals(shown)) {
			shown = now;
			render();
		}
		super.broadcastChanges();
	}

	private static String describe(ItemStack stack) {
		return stack.isEmpty() ? "-" : HavanaItems.id(stack.getItem()) + ":" + HavanaItems.kind(stack) + "x" + stack.getCount();
	}

	@Override
	public void clicked(int slotId, int button, ContainerInput clickType, Player player) {
		if (isButton(slotId)) {
			if (slotId == ROLL && (clickType == ContainerInput.PICKUP || clickType == ContainerInput.QUICK_MOVE)) {
				roll(clickType == ContainerInput.QUICK_MOVE ? 64 : 1);
			}
			sendAllDataToRemote();
			return;
		}
		super.clicked(slotId, button, clickType, player);
	}

	/** Shift-click: tobacco goes to the tobacco slot, flavors to the flavor slot, and back out again. */
	@Override
	public ItemStack quickMoveStack(Player player, int index) {
		Slot slot = this.slots.get(index);
		if (!slot.hasItem()) {
			return ItemStack.EMPTY;
		}
		ItemStack stack = slot.getItem();
		ItemStack original = stack.copy();
		if (index == TOBACCO || index == FLAVOR) {
			if (!moveItemStackTo(stack, SIZE, this.slots.size(), true)) {
				return ItemStack.EMPTY;
			}
		} else if (index >= SIZE) {
			int to = HavanaItems.isTobacco(stack) ? TOBACCO : HavanaItems.Flavor.of(stack) != null ? FLAVOR : -1;
			if (to < 0 || !moveItemStackTo(stack, to, to + 1, false)) {
				return ItemStack.EMPTY;
			}
		} else {
			return ItemStack.EMPTY;
		}
		if (stack.isEmpty()) {
			slot.set(ItemStack.EMPTY);
		} else {
			slot.setChanged();
		}
		return original;
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
		return Crops.blockId(level.getBlockState(table)).equals("crafting_table")
			&& player.distanceToSqr(table.getX() + 0.5, table.getY() + 0.5, table.getZ() + 0.5) <= 64.0;
	}

	@Override
	public void removed(Player player) {
		for (int i = 0; i < SIZE; i++) {
			if (isButton(i)) {
				box.setItem(i, ItemStack.EMPTY);
			}
		}
		super.removed(player);
		clearContainer(player, box);
	}
}
