package com.thatcoffeelock.tulpengekte;

import java.util.ArrayList;
import java.util.List;

import com.thatcoffeelock.market.MarketData;
import com.thatcoffeelock.market.Money;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.Entity;
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
 * The Bloemist's stall: a 3-row chest screen rendered on the server, so vanilla clients can use it.
 * <pre>
 *  WANTS  .  [want]  .  [want]  .  [want]  .  HELP
 *  SALE   .  [bulb]  [bulb]  [bulb]  [bulb]  .  BALANCE  .
 *  .      .  .       .  SELL ALL  .  .       .  .
 * </pre>
 * Every slot is a button. Click a want to sell him all of it he'll take today; click a bulb for sale to buy one,
 * shift-click to buy eight.
 */
final class BloemistMenu extends ChestMenu {
	static final int SIZE = 27;
	static final int[] WANTS = {2, 4, 6};
	static final int[] FOR_SALE = {11, 12, 13, 14};
	private static final int HELP = 8;
	private static final int BALANCE = 16;
	private static final int SELL_ALL = 22;

	private final SimpleContainer box;
	private final ServerPlayer viewer;
	private final Entity bloemist;
	private List<Variety> wants = List.of();
	private final List<Variety> forSale = Variety.of(Variety.Tier.COMMON);

	BloemistMenu(int syncId, ServerPlayer viewer, Entity bloemist) {
		this(syncId, viewer, bloemist, new SimpleContainer(SIZE));
	}

	private BloemistMenu(int syncId, ServerPlayer viewer, Entity bloemist, SimpleContainer box) {
		super(MenuType.GENERIC_9x3, syncId, viewer.getInventory(), box, 3);
		this.box = box;
		this.viewer = viewer;
		this.bloemist = bloemist;
		render();
	}

	private static ItemStack icon(Item item, Component name, List<Component> lore) {
		ItemStack stack = new ItemStack(item);
		stack.set(DataComponents.ITEM_NAME, name);
		stack.set(DataComponents.LORE, new ItemLore(lore));
		return stack;
	}

	private long day() {
		return Bloemist.day(((ServerLevel) viewer.level()).getServer());
	}

	private void render() {
		ItemStack filler = icon(TulpItems.item("minecraft:green_stained_glass_pane", Items.GLASS_PANE), Component.literal(" "), List.of());
		for (int i = 0; i < SIZE; i++) {
			box.setItem(i, filler.copy());
		}
		long day = day();
		wants = Bloemist.wants(day);
		box.setItem(0, icon(TulpItems.item("minecraft:pink_tulip", Items.PAPER), TulpItems.text("Today he wants", ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD),
			List.of(TulpItems.text("He pays the Market price plus " + Math.round(TulpConfig.get().bloemistPremium * 100) + "%", ChatFormatting.GRAY),
				TulpItems.text("for these, a few from each player a day.", ChatFormatting.GRAY),
				TulpItems.text("Tomorrow he wants something else.", ChatFormatting.DARK_GRAY))));
		for (int i = 0; i < WANTS.length; i++) {
			Variety v = wants.get(i);
			int left = Bloemist.left(day, viewer.getUUID(), v);
			int have = Bloemist.count(viewer, v);
			ItemStack stack = TulpItems.bulb(v, 1);
			List<Component> lore = new ArrayList<>();
			lore.add(TulpItems.text("Tulip bulb · " + v.tier.title, v.tier.format));
			lore.add(Component.empty());
			lore.add(TulpItems.text("Pays " + Money.format(Bloemist.price(v, viewer.getUUID())) + " each.", ChatFormatting.GOLD));
			lore.add(TulpItems.text(left > 0 ? "He'll take " + left + " more from you today." : "He's had all he wants from you today.",
				left > 0 ? ChatFormatting.GRAY : ChatFormatting.RED));
			lore.add(TulpItems.text("You have " + have + ".", ChatFormatting.GRAY));
			if (left > 0 && have > 0) {
				lore.add(TulpItems.text("Click: sell him " + Math.min(left, have) + ".", ChatFormatting.GREEN));
			}
			stack.set(DataComponents.LORE, new ItemLore(lore));
			box.setItem(WANTS[i], stack);
		}
		box.setItem(HELP, icon(Items.BOOK, TulpItems.text("Tulip trade", ChatFormatting.YELLOW), List.of(
			TulpItems.text("Plant bulbs on grass, dirt or farmland.", ChatFormatting.GRAY),
			TulpItems.text("A day later they flower. Lift (break) them", ChatFormatting.GRAY),
			TulpItems.text("for the bulb back plus offsets. Now and then", ChatFormatting.GRAY),
			TulpItems.text("an offset breaks into a rarer variety.", ChatFormatting.GRAY),
			TulpItems.text("Any bulb sells at the Market too.", ChatFormatting.DARK_GRAY))));

		box.setItem(9, icon(Items.WHEAT_SEEDS, TulpItems.text("Bulbs for sale", ChatFormatting.AQUA, ChatFormatting.BOLD),
			List.of(TulpItems.text("Common bulbs to start a bed with.", ChatFormatting.GRAY),
				TulpItems.text("Click: buy one. Shift-click: buy eight.", ChatFormatting.DARK_GRAY))));
		for (int i = 0; i < FOR_SALE.length; i++) {
			ItemStack stack = TulpItems.bulb(forSale.get(i), 1);
			stack.set(DataComponents.LORE, new ItemLore(List.of(TulpItems.text(forSale.get(i).story, ChatFormatting.GRAY), Component.empty(),
				TulpItems.text("Costs " + Money.format(Bloemist.bulbPrice()) + ".", ChatFormatting.GOLD),
				TulpItems.text("Click: buy one. Shift-click: buy eight.", ChatFormatting.GREEN))));
			box.setItem(FOR_SALE[i], stack);
		}
		box.setItem(BALANCE, icon(Items.SUNFLOWER, TulpItems.text("Your balance", ChatFormatting.GOLD, ChatFormatting.BOLD),
			List.of(TulpItems.text(Money.format(MarketData.balance(viewer)), ChatFormatting.GOLD))));
		box.setItem(SELL_ALL, icon(Items.EMERALD, TulpItems.text("Sell him everything he wants", ChatFormatting.GREEN, ChatFormatting.BOLD),
			List.of(TulpItems.text("All three of today's wants, as many", ChatFormatting.GRAY),
				TulpItems.text("as he'll take and you have.", ChatFormatting.GRAY))));
	}

	private void sell(List<Variety> varieties) {
		ServerLevel level = (ServerLevel) viewer.level();
		int sold = 0;
		for (Variety v : varieties) {
			sold += Bloemist.sell(viewer, v);
		}
		Cmd.sound(level, sold > 0 ? "minecraft:entity.villager.yes" : "minecraft:entity.villager.no",
			bloemist.getX(), bloemist.getY() + 1, bloemist.getZ(), 0.8f, 1f);
	}

	private void buy(Variety variety, int count) {
		ServerLevel level = (ServerLevel) viewer.level();
		if (Bloemist.buy(viewer, variety, count)) {
			Cmd.sound(level, "minecraft:entity.villager.yes", bloemist.getX(), bloemist.getY() + 1, bloemist.getZ(), 0.8f, 1f);
		} else {
			Cmd.sound(level, "minecraft:entity.villager.no", bloemist.getX(), bloemist.getY() + 1, bloemist.getZ(), 0.8f, 1f);
			viewer.sendSystemMessage(Component.literal("You can't afford that. He doesn't do credit. Not after 1637.").withStyle(ChatFormatting.RED));
		}
	}

	@Override
	public void clicked(int slotId, int button, ContainerInput clickType, Player player) {
		if (slotId >= 0 && slotId < SIZE) {
			if (clickType == ContainerInput.PICKUP || clickType == ContainerInput.QUICK_MOVE) {
				for (int i = 0; i < WANTS.length; i++) {
					if (slotId == WANTS[i]) {
						sell(List.of(wants.get(i)));
					}
				}
				for (int i = 0; i < FOR_SALE.length; i++) {
					if (slotId == FOR_SALE[i]) {
						buy(forSale.get(i), clickType == ContainerInput.QUICK_MOVE ? 8 : 1);
					}
				}
				if (slotId == SELL_ALL) {
					sell(wants);
				}
				render();
			}
			sendAllDataToRemote();
			return;
		}
		if (clickType == ContainerInput.QUICK_MOVE) {
			return; // nothing goes into the stall
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
	public boolean stillValid(Player player) {
		return Bloemist.is(bloemist) && player.distanceToSqr(bloemist) <= 64.0;
	}

	@Override
	public void removed(Player player) {
		box.clearContent();
		super.removed(player);
	}
}
