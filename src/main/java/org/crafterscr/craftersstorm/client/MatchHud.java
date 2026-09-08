package org.crafterscr.craftersstorm.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import org.crafterscr.craftersstorm.match.MatchPayload;
import org.crafterscr.craftersstorm.match.MatchView;
import java.util.Locale;

public final class MatchHud {
    public static final int HEIGHT = 18;
    private MatchHud() {}
    public static void render(GuiGraphics g, int x, int y, int width) {
        MatchPayload p = MatchView.get();
        if (!p.running() || width <= 0) return;
        var font = Minecraft.getInstance().font;
        String time = p.timer().equals("Final") ? "Final" : p.timer() + " " + String.format(Locale.ROOT, "%02d:%02d", p.seconds()/60, p.seconds()%60);
        String text = time + "  |  Vivos " + p.alive() + "  |  Kills " + p.kills();
        float scale = Math.min(1.0F, Math.max(1, width - 8) / (float)Math.max(1, font.width(text)));
        g.pose().pushPose();
        try {
            g.pose().translate(0, 0, 400);
            g.fill(x, y, x + width, y + HEIGHT - 2, 0xCC10131B);
            g.fill(x, y, x + width, y + 1, 0xFF9C65EA);
            float tx = x + (width - font.width(text)*scale)/2;
            float ty = y + (HEIGHT-2-font.lineHeight*scale)/2;
            g.pose().translate(tx, ty, 0);
            g.pose().scale(scale, scale, 1);
            g.drawString(font, text, 0, 0, 0xFFFFFFFF, true);
        } finally { g.pose().popPose(); }
    }
}
