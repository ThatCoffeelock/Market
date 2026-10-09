package com.thatcoffeelock.colonycraft;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.ChatFormatting;
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

/**
 * The bank teller: a 3-row chest screen anyone can open at the bank's lectern. The top row pays into the vault, the
 * bottom row takes out of it, the middle shows what's in it and what you've got. It's a shared vault: whoever's
 * there can take what's in it. Pick your neighbours well.
 */
final class BankMenu extends ChestMenu {
	private static final int SIZE = 27;
	private static final long[] AMOUNTS = {10, 100, 1000};

	@FunctionalInterface
	private interface Action {
		void run();
	}

	private final ServerPlayer viewer;
	private final Colony.Building bank;
	private final SimpleContainer box;
	private final Map<Integer, Action> actions = new HashMap<>();

	static void open(ServerPlayer player, Colony.Building bank) {
		player.openMenu(new SimpleMenuProvider((id, inv, p) -> new BankMenu(id, player, bank, new SimpleContainer(SIZE)),
			Component.literal(bank.colony.name + " · Bank").withStyle(ChatFormatting.DARK_GREEN)));
	}

	private BankMenu(int syncId, ServerPlayer viewer, Colony.Building bank, SimpleContainer box) {
		super(MenuType.GENERIC_9x3, syncId, viewer.getInventory(), box, 3);
		this.viewer = viewer;
		this.bank = bank;
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

	private void button(int slot, ItemStack icon, Action action) {
		box.setItem(slot, icon);
		actions.put(slot, action);
	}

	private void render() {
		actions.clear();
		ItemStack filler = icon(TownHallMenu.item("minecraft:yellow_stained_glass_pane", Items.GLASS_PANE), Component.literal(" "), List.of());
		for (int i = 0; i < SIZE; i++) {
			box.setItem(i, filler.copy());
		}
		Item[] coins = {Items.GOLD_NUGGET, Items.GOLD_INGOT, Items.GOLD_BLOCK};
		for (int i = 0; i < AMOUNTS.length; i++) {
			long cents = Bank.cents(AMOUNTS[i]);
			button(2 + i * 2, icon(coins[i], t("Pay in " + Bank.format(cents), ChatFormatting.GREEN, ChatFormatting.BOLD),
				List.of(t("From your Market balance into the vault.", ChatFormatting.GRAY))), () -> {
				if (!Street.deposit(viewer.getUUID(), bank, cents)) {
					nope("You don't have " + Bank.format(cents) + ".");
					return;
				}
				kaching();
				tellOwner(viewer.getName().getString() + " paid " + Bank.format(cents) + " into the vault.");
			});
			button(20 + i * 2, icon(coins[i], t("Take out " + Bank.format(cents), ChatFormatting.GOLD, ChatFormatting.BOLD),
				List.of(t("From the vault onto your Market balance.", ChatFormatting.GRAY))), () -> {
				long got = Street.withdraw(viewer.getUUID(), viewer.getName().getString(), bank, cents);
				if (got <= 0) {
					nope("The vault is empty.");
					return;
				}
				kaching();
				tellOwner(viewer.getName().getString() + " took " + Bank.format(got) + " out of the vault.");
			});
		}
		box.setItem(13, icon(Items.GOLD_BLOCK, t("The vault holds " + Bank.format(bank.vault), ChatFormatting.GOLD, ChatFormatting.BOLD),
			List.of(t("A shared vault: anyone can pay in, anyone can take out.", ChatFormatting.GRAY),
				t("Your balance: " + Bank.format(Bank.balance(viewer)), ChatFormatting.GRAY),
				t(RichesLink.present() ? "Walk into the vault and wade through it." : "", ChatFormatting.DARK_GRAY))));
	}

	/** The colony's owner hears about money moving in their bank (unless it's them). */
	private void tellOwner(String text) {
		ServerPlayer owner = Colonies.owner(bank.colony);
		if (owner != null && owner != viewer) {
			owner.sendSystemMessage(Component.literal("[" + bank.colony.name + " Bank] " + text).withStyle(ChatFormatting.GRAY));
		}
	}

	private void kaching() {
		Cmd.sound((ServerLevel) viewer.level(), "minecraft:entity.experience_orb.pickup", viewer.getX(), viewer.getY(), viewer.getZ(), 0.6f, 1.3f);
	}

	private void nope(String why) {
		Cmd.sound((ServerLevel) viewer.level(), "minecraft:entity.villager.no", viewer.getX(), viewer.getY(), viewer.getZ(), 0.6f, 1.0f);
		viewer.sendSystemMessage(Component.literal(why).withStyle(ChatFormatting.RED));
	}

	@Override
	public void clicked(int slotId, int button, ContainerInput clickType, Player player) {
		if (slotId >= 0 && slotId < SIZE) {
			Action action = actions.get(slotId);
			if (action != null && (clickType == ContainerInput.PICKUP || clickType == ContainerInput.QUICK_MOVE)) {
				action.run();
				render();
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
	public boolean stillValid(Player player) {
		net.minecraft.core.BlockPos teller = bank.world(BuildingType.BANK_TELLER);
		return bank.colony.buildings.contains(bank) && player.distanceToSqr(teller.getX() + 0.5, teller.getY(), teller.getZ() + 0.5) < 64;
	}

	@Override
	public void removed(Player player) {
		box.clearContent();
		super.removed(player);
	}
}
