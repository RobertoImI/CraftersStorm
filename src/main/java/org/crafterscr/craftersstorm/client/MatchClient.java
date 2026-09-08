package org.crafterscr.craftersstorm.client;

import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.crafterscr.craftersstorm.match.*;
import org.lwjgl.glfw.GLFW;

@EventBusSubscriber(modid = "craftersstorm", value = Dist.CLIENT)
public final class MatchClient {
    private static CameraType savedPerspective;
    private static int savedFov;
    private static int ticks;
    private static int lastPerspective = -1;
    private static int lastFov = -1;
    private MatchClient() {}

    @SubscribeEvent
    public static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
        restore(); TeamView.clear(); SpectatorInventoryView.clear(); MatchView.receive(MatchPayload.EMPTY); ticks = 0;
        lastPerspective = -1; lastFov = -1;
    }
    private static void restore() {
        Minecraft mc = Minecraft.getInstance();
        SpectatorPresentation.clear();
        if (savedPerspective != null) {
            mc.options.setCameraType(savedPerspective);
            mc.options.fov().set(savedFov);
            if (mc.player != null) mc.setCameraEntity(mc.player);
            savedPerspective = null;
        }
    }
    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (ModList.get().isLoaded("xaerominimap")) XaeroTeamTracker.install();
        MatchPayload state = MatchView.get();
        if (mc.player == null || mc.level == null || !state.running()) { restore(); SpectatorInventoryView.clear(); return; }
        ticks++;
        if (state.participant() && !state.eliminated()) {
            restore();
            int perspective = mc.options.getCameraType().ordinal();
            int fov = mc.options.fov().get();
            if (ticks % 10 == 0 || perspective != lastPerspective || fov != lastFov) {
                PacketDistributor.sendToServer(new MatchControl(0, perspective, fov));
                lastPerspective = perspective; lastFov = fov;
            }
        } else if (state.eliminated() && state.targetId() >= 0) {
            var target = mc.level.getEntity(state.targetId());
            if (target == null) { restore(); return; } // Wait for tracked entity/chunks.
            if (savedPerspective == null) {
                savedPerspective = mc.options.getCameraType(); savedFov = mc.options.fov().get();
            }
            mc.options.setCameraType(CameraType.values()[Math.max(0, Math.min(2, state.perspective()))]);
            mc.options.fov().set(Math.max(30, Math.min(110, state.fov())));
            mc.setCameraEntity(target);
            SpectatorPresentation.tick();
            if (ticks % 20 == 0) mc.player.displayClientMessage(
                    Component.literal("←  " + state.targetName() + "  →"), true);
        } else restore();
    }
    @SubscribeEvent
    public static void key(InputEvent.Key event) {
        Minecraft mc = Minecraft.getInstance();
        MatchPayload state = MatchView.get();
        if (mc.screen != null || mc.player == null || !state.running() || !state.eliminated()
                || event.getAction() != GLFW.GLFW_PRESS) return;
        int direction = event.getKey() == GLFW.GLFW_KEY_LEFT ? -1 : event.getKey() == GLFW.GLFW_KEY_RIGHT ? 1 : 0;
        if (direction != 0) PacketDistributor.sendToServer(new MatchControl(direction, 0, 70));
    }
    @SubscribeEvent
    public static void fallback(RenderGuiEvent.Post event) {
        if (!ModList.get().isLoaded("xaerominimap") && MatchView.get().running()) {
            var g = event.getGuiGraphics();
            MatchHud.render(g, Math.max(0, g.guiWidth() - 200), 8, 192);
        }
    }
}
