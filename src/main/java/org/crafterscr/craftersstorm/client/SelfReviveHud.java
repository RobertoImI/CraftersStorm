package org.crafterscr.craftersstorm.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.crafterscr.craftersstorm.match.RevivePayload;
import org.crafterscr.craftersstorm.match.ReviveView;

public final class SelfReviveHud {
    private SelfReviveHud() {}

    public static void render(GuiGraphics g) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) return;

        RevivePayload state = ReviveView.get();
        if (!state.downed() || !state.selfReviveAvailable()) return;

        int width = 170;
        int height = 10;
        int x = (g.guiWidth() - width) / 2;
        int y = g.guiHeight() - 72;
        int progress = Math.max(0, Math.min(100, state.selfReviveProgress()));
        int filled = Math.round((width - 2) * (progress / 100.0F));

        g.fill(x, y, x + width, y + height, 0xCC000000);
        g.fill(x + 1, y + 1, x + 1 + filled, y + height - 1, 0xFFE04B4B);

        String key = ClientKeys.SELF_REVIVE.getTranslatedKeyMessage().getString();
        Component text = Component.literal("Mantén [" + key + "] para autorreanimarte  " + progress + "%");
        int tx = (g.guiWidth() - mc.font.width(text)) / 2;
        g.drawString(mc.font, text, tx, y - 12, 0xFFFFFFFF, true);
    }
}
