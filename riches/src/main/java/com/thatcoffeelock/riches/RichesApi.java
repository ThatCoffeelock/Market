package com.thatcoffeelock.riches;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;
import java.util.function.Function;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;

/**
 * For other mods (Colonycraft's bank and museum use it), through Fabric's ObjectShare as {@code riches:api}: a
 * {@code BiFunction<String, Map<String, Object>, Object>} taking an operation and its arguments, so nobody needs
 * Riches to compile. Operations:
 * <ul>
 * <li>{@code pools} (namespace, resolver: Function&lt;String, Long&gt;): a vault owned by {@code pool:<namespace>:<id>}
 * piles up whatever the resolver says {@code <id>} holds, in cents. A shared vault, say.</li>
 * <li>{@code vault} (level, pos, owner, owner_name): a Vault Ledger standing at pos.</li>
 * <li>{@code door} (level, pos, owner, owner_name): a Vault Door (its lower half) at pos. An empty owner opens for anyone.</li>
 * <li>{@code showcase} (level, pos, kind "case"/"pedestal", owner, owner_name): a Display Case or Pedestal at pos.</li>
 * <li>{@code remove} (level, pos): forgets whatever of ours is at pos (the blocks are the caller's business).
 * An exhibit in a case drops where it stood.</li>
 * <li>{@code relics} (level, positions): how many relics are on show in the cases at these positions.</li>
 * <li>{@code occupied} (level, positions): how many of those cases have anything in them.</li>
 * <li>{@code info} (level, pos): what's at pos, as a map ("kind": vault/door/case/pedestal, "owner"), or null.</li>
 * </ul>
 * Places that already exist are left as they are (with their exhibits), so registering again is harmless.
 */
public final class RichesApi {
	static final String KEY = "riches:api";
	/** Namespace -> what each of its pools holds, in cents. */
	static final Map<String, Function<String, Long>> POOLS = new ConcurrentHashMap<>();

	private RichesApi() {
	}

	static void publish() {
		FabricLoader.getInstance().getObjectShare().putIfAbsent(KEY, (BiFunction<String, Map<String, Object>, Object>) RichesApi::call);
	}

	/** What a pool owner ("pool:colonycraft:abc123") holds, in cents; -1 if it isn't a pool. */
	static long pool(String owner) {
		if (!owner.startsWith("pool:")) {
			return -1;
		}
		String rest = owner.substring(5);
		int colon = rest.indexOf(':');
		Function<String, Long> resolver = colon < 0 ? null : POOLS.get(rest.substring(0, colon));
		if (resolver == null) {
			return 0;
		}
		Long cents = resolver.apply(rest.substring(colon + 1));
		return cents == null ? 0 : Math.max(0, cents);
	}

	@SuppressWarnings("unchecked")
	static @Nullable Object call(String op, Map<String, Object> args) {
		if (op.equals("pools")) {
			if (args.get("namespace") instanceof String ns && args.get("resolver") instanceof Function<?, ?> f) {
				POOLS.put(ns, (Function<String, Long>) f);
				return true;
			}
			return false;
		}
		if (!(args.get("level") instanceof ServerLevel level)) {
			return null;
		}
		String dim = Places.dim(level);
		String owner = args.get("owner") instanceof String s ? s : "";
		String ownerName = args.get("owner_name") instanceof String s ? s : "";
		switch (op) {
			case "info" -> {
				if (!(args.get("pos") instanceof BlockPos pos)) {
					return null;
				}
				String key = Places.key(dim, pos);
				if (Places.VAULTS.get(key) instanceof Places.Vault v) {
					return Map.of("kind", "vault", "owner", v.owner);
				}
				if (Places.DOORS.get(key) instanceof Places.Door d) {
					return Map.of("kind", "door", "owner", d.owner);
				}
				if (Places.SHOWCASES.get(key) instanceof Places.Showcase s) {
					return Map.of("kind", s.kind == Places.Kind.CASE ? "case" : "pedestal", "owner", s.owner);
				}
				return null;
			}
			case "vault", "door", "showcase", "remove" -> {
				if (!(args.get("pos") instanceof BlockPos p)) {
					return null;
				}
				BlockPos pos = p.immutable();
				String key = Places.key(dim, pos);
				switch (op) {
					case "vault" -> Places.VAULTS.computeIfAbsent(key, k -> {
						Places.Vault v = new Places.Vault(dim, pos);
						v.owner = owner;
						v.ownerName = ownerName;
						return v;
					});
					case "door" -> Places.DOORS.computeIfAbsent(key, k -> {
						Places.Door d = new Places.Door(dim, pos);
						d.owner = owner;
						d.ownerName = ownerName;
						return d;
					});
					case "showcase" -> Places.SHOWCASES.computeIfAbsent(key, k -> {
						Places.Showcase s = new Places.Showcase(dim, pos, "pedestal".equals(args.get("kind")) ? Places.Kind.PEDESTAL : Places.Kind.CASE);
						s.owner = owner;
						s.ownerName = ownerName;
						s.drawn = true;
						return s;
					});
					default -> {
						Places.Vault v = Places.VAULTS.remove(key);
						if (v != null) {
							Vaults.clear(level, v);
						}
						Places.DOORS.remove(key);
						Places.Showcase s = Places.SHOWCASES.remove(key);
						if (s != null) {
							Showcases.clear(level, s);
							if (!s.item.isEmpty()) {
								net.minecraft.world.level.block.Block.popResource(level, pos, s.item);
							}
						}
					}
				}
				Store.changed();
				return true;
			}
			case "relics", "occupied" -> {
				int n = 0;
				if (args.get("positions") instanceof Collection<?> list) {
					for (Object o : list) {
						if (o instanceof BlockPos pos) {
							Places.Showcase s = Places.SHOWCASES.get(Places.key(dim, pos));
							if (s != null && !s.item.isEmpty() && (op.equals("occupied") || RichesItems.relicOf(s.item) != null)) {
								n++;
							}
						}
					}
				}
				return n;
			}
			default -> {
				return null;
			}
		}
	}
}
