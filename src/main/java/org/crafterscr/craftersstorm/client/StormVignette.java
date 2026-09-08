package org.crafterscr.craftersstorm.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.border.WorldBorder;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import org.crafterscr.craftersstorm.CraftersStorm;
import org.crafterscr.craftersstorm.StormPayload;
import org.crafterscr.craftersstorm.StormView;

@EventBusSubscriber(
        modid = CraftersStorm.MOD_ID,
        value = Dist.CLIENT
)
public final class StormVignette {
    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(
                    CraftersStorm.MOD_ID,
                    "dynamic/storm_vignette"
            );

    // Intensidad máxima: valores entre 0 y 1.
    private static final float MAX_OPACITY = 0.70F;

    private static boolean textureCreated;
    private static float opacity;
    private static long previousFrame;

    private StormVignette() {
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        StormView.receive(StormPayload.EMPTY);
        opacity = 0;
        previousFrame = 0;
    }

    @SubscribeEvent
    public static void render(RenderGuiEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();

        if (minecraft.player == null || minecraft.level == null) {
            opacity = 0;
            previousFrame = 0;
            return;
        }

        long now = System.nanoTime();

        double delta = previousFrame == 0
                ? 1.0 / 60.0
                : Math.min(0.1, (now - previousFrame) / 1_000_000_000.0);

        previousFrame = now;

        StormPayload state = StormView.get();

        boolean eligible =
                state.active()
                        && state.damaging()
                        && Level.OVERWORLD.equals(minecraft.level.dimension())
                        && minecraft.player.isAlive()
                        && !minecraft.player.isCreative()
                        && !minecraft.player.isSpectator();

        boolean outside = false;

        if (eligible) {
            // Usamos el borde del cliente, que recibe el movimiento
            // con mayor frecuencia que el estado del minimapa.
            WorldBorder border = minecraft.level.getWorldBorder();

            double half = border.getSize() / 2.0;
            double x = minecraft.player.getX();
            double z = minecraft.player.getZ();

            outside =
                    x < border.getCenterX() - half
                            || x > border.getCenterX() + half
                            || z < border.getCenterZ() - half
                            || z > border.getCenterZ() + half;
        }

        float target = outside ? MAX_OPACITY : 0;

        // Aparición y desaparición suaves, independientes de los FPS.
        float blend = (float) (1.0 - Math.exp(-10.0 * delta));
        opacity += (target - opacity) * blend;

        if (opacity < 0.005F) {
            opacity = 0;
            return;
        }

        createTexture(minecraft);

        GuiGraphics graphics = event.getGuiGraphics();
        int width = graphics.guiWidth();
        int height = graphics.guiHeight();

        graphics.flush();

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);

        try {
            graphics.setColor(1, 1, 1, opacity);

            graphics.blit(
                    TEXTURE,
                    0, 0,
                    0.0F, 0.0F,
                    width, height,
                    width, height
            );

            graphics.flush();
        } finally {
            graphics.setColor(1, 1, 1, 1);
            RenderSystem.depthMask(true);
            RenderSystem.enableDepthTest();
            RenderSystem.disableBlend();
        }
    }

    private static void createTexture(Minecraft minecraft) {
        if (textureCreated) {
            return;
        }

        int resolution = 256;
        NativeImage image = new NativeImage(
                resolution,
                resolution,
                false
        );

        for (int y = 0; y < resolution; y++) {
            for (int x = 0; x < resolution; x++) {
                double nx = 2.0 * x / (resolution - 1) - 1.0;
                double ny = 2.0 * y / (resolution - 1) - 1.0;

                double distance = Math.sqrt(nx * nx + ny * ny);

                // Centro transparente, rojo gradual hacia los bordes.
                double amount = Math.max(
                        0.0,
                        Math.min(1.0, (distance - 0.55) / 0.65)
                );

                amount = amount * amount * (3.0 - 2.0 * amount);

                int alpha = (int) Math.round(amount * 255);

                // NativeImage utiliza ABGR en esta versión.
                int color =
                        (alpha << 24)
                                | (10 << 16)
                                | (10 << 8)
                                | 210;

                image.setPixelRGBA(x, y, color);
            }
        }

        DynamicTexture texture = new DynamicTexture(image);
        minecraft.getTextureManager().register(TEXTURE, texture);
        texture.setFilter(true, false);

        textureCreated = true;
    }
}