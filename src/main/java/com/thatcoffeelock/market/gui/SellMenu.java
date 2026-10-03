package com.thatcoffeelock.market.gui;

import com.thatcoffeelock.market.Gui;
import com.thatcoffeelock.market.MarketConfig;
import com.thatcoffeelock.market.MarketData;
import com.thatcoffeelock.market.MarketMod;
import com.thatcoffeelock.market.Money;
import com.thatcoffeelock.market.PriceBook;
import com.thatcoffeelock.market.SkillsHook;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

/**
 * Top 5 rows are a normal chest you put items in. The bottom row shows the running total and a
 * confirm button. Anything left inside (or unsellable) goes back to the player when the screen closes.
 */
public final class SellMenu extends MarketMenu {
	private static final int GRID = 45;
	private static final int CONFIRM = 49;
	private boolean updating;
	private long lastSnapshot = Long.MIN_VALUE;

	public SellMenu(int syncId, ServerPlayer viewer, @Nullable BlockPos origin) {
		super(syncId, viewer, origin);
		render();
	}

	@Override
	protected boolean isButton(int slot) {
		return slot >= GRID && slot < SIZE;
	}

	private void render() {
		clearButtons();
		button(45, Gui.icon(Items.ARROW, Gui.text("Back", ChatFormatting.YELLOW)), (b, t) -> {
			click();
			MarketMod.nextTick(() -> MarketGui.openHub(viewer, origin));
		});
		button(47, Gui.icon(Items.CHEST, Gui.text("Add My Inventory", ChatFormatting.AQUA, ChatFormatting.BOLD),
			Gui.text("Moves every sellable item from your", ChatFormatting.GRAY),
			Gui.text("main inventory in here. Hotbar, tools,", ChatFormatting.GRAY),
			Gui.text("armor and enchanted gear are left alone.", ChatFormatting.GRAY)), (b, t) -> {
			click();
			addInventory();
		});
		button(51, Gui.icon(Items.BOOK, Gui.text("How selling works", ChatFormatting.YELLOW),
			Gui.text("Put items in the grid above.", ChatFormatting.GRAY),
			Gui.text("Damaged gear is worth less, enchantments more.", ChatFormatting.GRAY),
			Gui.text("Unsellable items are handed back.", ChatFormatting.GRAY)), null);
		fillEmptyButtons();
		updateTotal();
	}

	/** Refresh the confirm button whenever the grid (or balance) changes. Runs every tick while open. */
	@Override
	public void broadcastChanges() {
		long snapshot = MarketData.balance(viewer);
		for (int i = 0; i < GRID; i++) {
			ItemStack stack = box.getItem(i);
			snapshot = snapshot * 31 + (stack.isEmpty() ? 0 : stack.getItem().hashCode() * 64L + stack.getCount() + 7L * stack.getDamageValue());
		}
		if (snapshot != lastSnapshot) {
			lastSnapshot = snapshot;
			updateTotal();
		}
		super.broadcastChanges();
	}

	private void updateTotal() {
		if (updating) {
			return;
		}
		updating = true;
		try {
			long total = 0;
			int items = 0;
			int rejected = 0;
			for (int i = 0; i < GRID; i++) {
				ItemStack stack = box.getItem(i);
				if (stack.isEmpty()) {
					continue;
				}
				long value = PriceBook.stackSellValue(stack);
				if (value > 0) {
					total += value;
					items += stack.getCount();
				} else {
					rejected++;
				}
			}
			ItemStack icon = Gui.icon(total > 0 ? Items.EMERALD_BLOCK : Items.COAL_BLOCK,
				Gui.text("Confirm Sale", ChatFormatting.GREEN, ChatFormatting.BOLD),
				Gui.text(items + " item(s) for ", ChatFormatting.GRAY).append(Money.text(total)),
				rejected > 0 ? Gui.text(rejected + " stack(s) the market won't take", ChatFormatting.RED) : Component.empty(),
				Component.empty(),
				Gui.text("Balance: ", ChatFormatting.GRAY).append(Money.text(MarketData.balance(viewer))));
			button(CONFIRM, total > 0 ? Gui.glow(icon) : icon, (b, t) -> sell());
		} finally {
			updating = false;
		}
	}

	private void sell() {
		if (viewer.isCreative() && !MarketConfig.get().allowCreativeSelling) {
			nope();
			viewer.sendSystemMessage(Component.literal("Nice try. Creative-mode items can't be sold.").withStyle(ChatFormatting.RED));
			return;
		}
		long total = 0;
		int items = 0;
		updating = true;
		try {
			for (int i = 0; i < GRID; i++) {
				ItemStack stack = box.getItem(i);
				long value = PriceBook.stackSellValue(stack);
				if (value > 0) {
					total += value;
					items += stack.getCount();
					box.setItem(i, ItemStack.EMPTY);
				}
			}
		} finally {
			updating = false;
		}
		if (items == 0) {
			nope();
			viewer.sendSystemMessage(Component.literal("Nothing in there the market wants to buy.").withStyle(ChatFormatting.RED));
		} else {
			total = SkillsHook.sell(viewer, total);
			MarketData.deposit(viewer, total);
			kaching();
			viewer.sendSystemMessage(Component.literal("Sold " + items + " item(s) for ").withStyle(ChatFormatting.GREEN)
				.append(Money.text(total))
				.append(Component.literal(". Balance: ").withStyle(ChatFormatting.GREEN))
				.append(Money.text(MarketData.balance(viewer))));
		}
		updateTotal();
	}

	private void addInventory() {
		Inventory inventory = viewer.getInventory();
		for (int i = 9; i < 36; i++) {
			ItemStack stack = inventory.getItem(i);
			if (stack.isEmpty() || stack.isDamageableItem() || stack.isEnchanted() || PriceBook.stackSellValue(stack) <= 0) {
				continue;
			}
			ItemStack rest = box.addItem(stack.copy());
			inventory.setItem(i, rest);
		}
	}

	@Override
	public ItemStack quickMoveStack(Player player, int index) {
		Slot slot = this.slots.get(index);
		if (!slot.hasItem()) {
			return ItemStack.EMPTY;
		}
		ItemStack stack = slot.getItem();
		ItemStack original = stack.copy();
		if (index < GRID) {
			if (!moveItemStackTo(stack, SIZE, this.slots.size(), true)) {
				return ItemStack.EMPTY;
			}
		} else if (index >= SIZE) {
			if (!moveItemStackTo(stack, 0, GRID, false)) {
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
	public void removed(Player player) {
		updating = true;
		for (int i = GRID; i < SIZE; i++) {
			box.setItem(i, ItemStack.EMPTY);
		}
		super.removed(player);
		clearContainer(player, box);
	}
}
