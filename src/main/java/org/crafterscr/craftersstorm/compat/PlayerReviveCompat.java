package org.crafterscr.craftersstorm.compat;

import net.minecraft.world.entity.player.Player;
import net.neoforged.fml.ModList;

import java.lang.reflect.Method;

/**
 * Optional bridge to PlayerRevive. Reflection keeps PlayerRevive optional at both
 * compile time and runtime: CraftersStorm still works normally when it is absent.
 */
public final class PlayerReviveCompat {
    private static boolean initialized;
    private static boolean available;
    private static Method getBleeding;
    private static Method isBleeding;
    private static Method revive;

    private PlayerReviveCompat() {}

    private static void init() {
        if (initialized) return;
        initialized = true;
        if (!ModList.get().isLoaded("playerrevive")) return;

        try {
            Class<?> server = Class.forName("team.creative.playerrevive.server.PlayerReviveServer");
            Class<?> bleeding = Class.forName("team.creative.playerrevive.api.IBleeding");
            getBleeding = server.getMethod("getBleeding", Player.class);
            isBleeding = bleeding.getMethod("isBleeding");
            revive = server.getMethod("revive", Player.class);
            available = true;
        } catch (ReflectiveOperationException ignored) {
            available = false;
        }
    }

    public static boolean installed() {
        init();
        return available;
    }

    public static boolean isBleeding(Player player) {
        init();
        if (!available || player == null) return false;
        try {
            Object state = getBleeding.invoke(null, player);
            return Boolean.TRUE.equals(isBleeding.invoke(state));
        } catch (ReflectiveOperationException ignored) {
            return false;
        }
    }

    public static boolean revive(Player player) {
        init();
        if (!available || player == null || !isBleeding(player)) return false;
        try {
            revive.invoke(null, player);
            return true;
        } catch (ReflectiveOperationException ignored) {
            return false;
        }
    }
}
