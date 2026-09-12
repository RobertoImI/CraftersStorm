package org.crafterscr.craftersstorm.compat;

import net.minecraft.world.entity.player.Player;
import net.neoforged.fml.ModList;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

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
    private static Method bleedingRevive;
    private static Method revivingPlayers;
    private static Method cancelHelper;
    private static Method sendUpdatePacket;

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
            bleedingRevive = bleeding.getMethod("revive", Player.class);
            revivingPlayers = bleeding.getMethod("revivingPlayers");
            cancelHelper = server.getMethod("cancelHelper", Player.class, Player.class);
            sendUpdatePacket = server.getMethod("sendUpdatePacket", Player.class);
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

    /** Normal successful revive: effects, sounds and PlayerRevive events are preserved. */
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

    /**
     * Clears PlayerRevive's downed state after CraftersStorm converts the final death
     * directly into spectator mode. This deliberately avoids the normal "revived"
     * sound/effects because the player was eliminated, not rescued.
     */
    @SuppressWarnings("unchecked")
    public static void clearBleedingAfterElimination(Player player) {
        init();
        if (!available || player == null) return;
        try {
            Object state = getBleeding.invoke(null, player);
            if (!Boolean.TRUE.equals(isBleeding.invoke(state))) return;

            List<Player> helpers = new ArrayList<>((List<Player>) revivingPlayers.invoke(state));
            for (Player helper : helpers) cancelHelper.invoke(null, player, helper);
            ((List<Player>) revivingPlayers.invoke(state)).clear();

            player.getPersistentData().remove("playerrevive:bleeding");
            bleedingRevive.invoke(state, player);
            player.setForcedPose(null);
            sendUpdatePacket.invoke(null, player);
        } catch (ReflectiveOperationException ignored) {
            // Compatibility is intentionally fail-soft: the match must still run.
        }
    }
}
