package com.thatcoffeelock.warehouse;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * Asking a player to type something (a search, a new name). Vanilla clients have no text box in a chest screen, so the
 * screen closes, the player types the answer in chat, and the screen opens again. The answer isn't shown to anyone.
 */
final class Prompts {
	private static final Map<UUID, Consumer<String>> WAITING = new HashMap<>();

	private Prompts() {
	}

	static void ask(ServerPlayer player, String question, Consumer<String> answer) {
		WAITING.put(player.getUUID(), answer);
		player.sendSystemMessage(Component.literal(question).withStyle(ChatFormatting.YELLOW));
	}

	/** Chat from a player. Returns false (swallow the message) if it answers a question. */
	static boolean onChat(ServerPlayer player, String text) {
		Consumer<String> answer = WAITING.remove(player.getUUID());
		if (answer == null) {
			return true;
		}
		WarehouseMod.nextTick(() -> answer.accept(text.trim()));
		return false;
	}

	static void forget(UUID player) {
		WAITING.remove(player);
	}

	static void clear() {
		WAITING.clear();
	}
}
