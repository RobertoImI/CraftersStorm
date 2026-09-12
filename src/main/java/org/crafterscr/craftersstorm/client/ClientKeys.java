package org.crafterscr.craftersstorm.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import org.crafterscr.craftersstorm.CraftersStorm;
import org.lwjgl.glfw.GLFW;

@EventBusSubscriber(modid = CraftersStorm.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class ClientKeys {
    public static final KeyMapping SELF_REVIVE = new KeyMapping(
            "key.craftersstorm.self_revive",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_H,
            "key.categories.craftersstorm"
    );

    private ClientKeys() {}

    @SubscribeEvent
    public static void register(RegisterKeyMappingsEvent event) {
        event.register(SELF_REVIVE);
    }
}
