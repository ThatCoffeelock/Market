package com.thatcoffeelock.market.gui;

import java.util.ArrayList;
import java.util.List;

import com.thatcoffeelock.market.Gui;
import com.thatcoffeelock.market.MarketData;
import com.thatcoffeelock.market.MarketItems;
import com.thatcoffeelock.market.MarketMod;
import com.thatcoffeelock.market.Money;
import com.thatcoffeelock.market.PriceBook;
import com.thatcoffeelock.market.SkillsHook;
import com.thatcoffeelock.market.Vanity;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

/** Category list -> paged item list. Also hosts the luxury (vanity) shop. */
public final class BuyMenu extends MarketMenu {
	private static final int PER_PAGE = 45;
	private static final int[] CATEGORY_SLOTS = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34};
	private static final int[] VANITY_SLOTS = {20, 21, 22, 23, 24, 29, 30, 31, 32, 33};

	private final boolean luxury;
	private int category = -1;
	private int page;

	public BuyMenu(int syncId, ServerPlayer viewer, @Nullable BlockPos origin, boolean luxury) {
		super(syncId, viewer, origin);
		this.luxury = luxury;
		render();
	}

	private void render() {
		clearButtons();
		if (luxury) {
			renderLuxury();
		} else if (category < 0) {
			renderCategories();
		} else {
			renderItems();
		}
		fillEmptyButtons();
	}

	private void bottomBar(Runnable back) {
		button(45, Gui.icon(Items.ARROW, Gui.text("Back", ChatFormatting.YELLOW)), (b, t) -> {
			click();
			back.run();
		});
		button(49, Gui.icon(Items.SUNFLOWER, Gui.text("Balance", ChatFormatting.GOLD),
			Money.text(MarketData.balance(viewer)).withStyle(style -> style.withItalic(false))), null);
		button(53, Gui.icon(Items.BARRIER, Gui.text("Close", ChatFormatting.RED)), (b, t) -> MarketMod.nextTick(viewer::closeContainer));
	}

	private void toHub() {
		MarketMod.nextTick(() -> MarketGui.openHub(viewer, origin));
	}

	// ---------------------------------------------------------------- categories

	private void renderCategories() {
		List<PriceBook.Category> categories = PriceBook.categories();
		for (int i = 0; i < categories.size() && i < CATEGORY_SLOTS.length; i++) {
			PriceBook.Category cat = categories.get(i);
			int index = i;
			button(CATEGORY_SLOTS[i], Gui.icon(cat.icon(), Gui.text(cat.name(), ChatFormatting.AQUA, ChatFormatting.BOLD),
				Gui.text(cat.items().size() + " items", ChatFormatting.GRAY)), (b, t) -> {
				click();
				category = index;
				page = 0;
				render();
			});
		}
		bottomBar(this::toHub);
	}

	// ---------------------------------------------------------------- items

	private void renderItems() {
		List<PriceBook.Category> categories = PriceBook.categories();
		if (category >= categories.size()) {
			category = -1;
			render();
			return;
		}
		List<Item> items = new ArrayList<>();
		for (Item item : categories.get(category).items()) {
			if (PriceBook.unitBuy(item) > 0) {
				items.add(item);
			}
		}
		int pages = Math.max(1, (items.size() + PER_PAGE - 1) / PER_PAGE);
		page = Math.min(page, pages - 1);
		for (int i = 0; i < PER_PAGE; i++) {
			int index = page * PER_PAGE + i;
			if (index >= items.size()) {
				break;
			}
			Item item = items.get(index);
			button(i, shopIcon(item), (b, t) -> {
				int amount = t == ContainerInput.QUICK_MOVE ? item.getDefaultMaxStackSize() : b == 1 ? Math.min(8, item.getDefaultMaxStackSize()) : 1;
				buy(item, amount);
			});
		}
		bottomBar(() -> {
			category = -1;
			render();
		});
		if (page > 0) {
			button(48, Gui.icon(Items.PAPER, Gui.text("Previous page", ChatFormatting.YELLOW)), (b, t) -> {
				click();
				page--;
				render();
			});
		}
		if (page < pages - 1) {
			button(50, Gui.icon(Items.PAPER, Gui.text("Next page", ChatFormatting.YELLOW),
				Gui.text("Page " + (page + 1) + " of " + pages, ChatFormatting.GRAY)), (b, t) -> {
				click();
				page++;
				render();
			});
		}
	}

	private ItemStack shopIcon(Item item) {
		long buy = PriceBook.unitBuy(item);
		long sell = PriceBook.unitSell(item);
		int stack = item.getDefaultMaxStackSize();
		List<Component> lore = new ArrayList<>();
		lore.add(Gui.text("Buy: ", ChatFormatting.GRAY).append(Money.text(buy)).append(Gui.text(" each", ChatFormatting.GRAY)));
		if (sell > 0) {
			lore.add(Gui.text("Market pays: ", ChatFormatting.DARK_GRAY).append(Gui.text(Money.format(sell), ChatFormatting.DARK_GRAY)));
		}
		lore.add(Component.empty());
		lore.add(Gui.text("Left-click: buy 1", ChatFormatting.YELLOW));
		if (stack > 1) {
			lore.add(Gui.text("Right-click: buy " + Math.min(8, stack) + " (" + Money.format(buy * Math.min(8, stack)) + ")", ChatFormatting.YELLOW));
			lore.add(Gui.text("Shift-click: buy " + stack + " (" + Money.format(buy * stack) + ")", ChatFormatting.YELLOW));
		}
		return Gui.withLore(new ItemStack(item), lore);
	}

	private void buy(Item item, int amount) {
		long unit = PriceBook.unitBuy(item);
		if (unit <= 0 || amount <= 0) {
			return;
		}
		long cost = SkillsHook.buy(viewer, unit * amount, Math.max(0, PriceBook.unitSell(item)) * amount);
		if (!MarketData.withdraw(viewer, cost)) {
			nope();
			viewer.sendSystemMessage(Component.literal("You need ").withStyle(ChatFormatting.RED)
				.append(Money.text(cost))
				.append(Component.literal(" but only have ").withStyle(ChatFormatting.RED))
				.append(Money.text(MarketData.balance(viewer))));
			return;
		}
		MarketItems.give(viewer, new ItemStack(item, amount));
		kaching();
		render();
	}

	// ---------------------------------------------------------------- luxury

	private void renderLuxury() {
		Vanity.Type[] types = Vanity.Type.values();
		for (int i = 0; i < types.length && i < VANITY_SLOTS.length; i++) {
			Vanity.Type type = types[i];
			List<Component> lore = new ArrayList<>();
			lore.add(Gui.text(type.description, ChatFormatting.GRAY));
			lore.add(Component.empty());
			lore.add(Gui.text("Price: ", ChatFormatting.GRAY).append(Money.text(type.price())));
			lore.add(Gui.text("Click to buy.", ChatFormatting.YELLOW));
			button(VANITY_SLOTS[i], Gui.glow(Gui.icon(type.icon(), Gui.text(type.displayName, ChatFormatting.GOLD, ChatFormatting.BOLD), lore)),
				(b, t) -> buyVanity(type));
		}
		bottomBar(this::toHub);
	}

	private void buyVanity(Vanity.Type type) {
		long price = type.price();
		if (!MarketData.withdraw(viewer, price)) {
			nope();
			viewer.sendSystemMessage(Component.literal("The " + type.displayName + " costs ").withStyle(ChatFormatting.RED)
				.append(Money.text(price))
				.append(Component.literal(". Keep grinding.").withStyle(ChatFormatting.RED)));
			return;
		}
		MarketItems.give(viewer, MarketItems.vanity(type));
		kaching();
		viewer.sendSystemMessage(Component.literal("You bought a " + type.displayName + ". Flex responsibly.").withStyle(ChatFormatting.GOLD));
		render();
	}
}
