package org.crafterscr.craftersstorm.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.client.ClientRecipeBook;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.stats.StatsCounter;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import org.crafterscr.craftersstorm.match.*;

/** Read-only rendering model. Never added to the world, ticked as a player, or sent to the server. */
public final class SpectatorPresentation {
    private static Mirror mirror;
    private static ItemInHandRenderer hands;
    private static SpectatorInventoryPayload snapshot;
    private static int useAge;
    private static AbstractClientPlayer target() {
        Minecraft mc = Minecraft.getInstance();
        MatchPayload state = MatchView.get();
        SpectatorInventoryPayload p = SpectatorInventoryView.get();
        if (mc.player == null || !mc.player.isSpectator() || mc.level == null || !state.running()
                || !state.eliminated() || p == null || state.targetId() != p.targetId()) return null;
        var entity = mc.level.getEntity(p.targetId());
        return entity instanceof AbstractClientPlayer player && player.getUUID().equals(p.targetUuid())
                && mc.getCameraEntity() == player ? player : null;
    }
    public static boolean ready() {
        return mirror != null && hands != null && mirror.target == target();
    }
    public static void clear() { mirror = null; hands = null; snapshot = null; useAge = 0; }
    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        AbstractClientPlayer target = target();
        if (target == null) { clear(); return; }
        var p = SpectatorInventoryView.get();
        if (mirror == null || mirror.level() != mc.level || mirror.target != target) {
            mirror = new Mirror(mc, mc.level, mc.getConnection(), target);
            hands = new ItemInHandRenderer(mc, mc.getEntityRenderDispatcher(), mc.getItemRenderer());
        }
        useAge = snapshot == p ? useAge + 1 : 0;
        snapshot = p;
        mirror.update();
        LocalPlayer real = mc.player;
        try { mc.player = mirror; hands.tick(); }
        finally { mc.player = real; }
    }
    public static void renderHands(float partial, PoseStack poses, MultiBufferSource.BufferSource buffers) {
        if (!ready()) return;
        Minecraft mc = Minecraft.getInstance();
        mirror.update();
        LocalPlayer real = mc.player;
        // Vanilla skin/map helpers access Minecraft.player. Scope the visual model to this call only.
        try {
            mc.player = mirror;
            hands.renderHandsWithItems(partial, poses, buffers, mirror,
                    mc.getEntityRenderDispatcher().getPackedLightCoords(mirror.target, partial));
        } finally { mc.player = real; }
    }
    private static ResourceLocation sprite(String name) { return ResourceLocation.withDefaultNamespace("hud/" + name); }
    public static void renderHotbar(GuiGraphics g) {
        if (!ready()) return;
        Minecraft mc = Minecraft.getInstance();
        var p = SpectatorInventoryView.get();
        int center = g.guiWidth() / 2, y = g.guiHeight() - 22;
        RenderSystem.enableBlend();
        g.blitSprite(sprite("hotbar"), center - 91, y, 182, 22);
        g.blitSprite(sprite("hotbar_selection"), center - 92 + p.selected() * 20, y - 1, 24, 23);
        for (int i = 0; i < 9; i++) {
            int x = center - 88 + i * 20;
            g.renderItem(p.hotbar().get(i), x, y + 3);
            g.renderItemDecorations(mc.font, p.hotbar().get(i), x, y + 3);
        }
        if (!p.offhand().isEmpty()) {
            boolean left = mirror.target.getMainArm() == HumanoidArm.RIGHT;
            g.blitSprite(sprite(left ? "hotbar_offhand_left" : "hotbar_offhand_right"),
                    left ? center - 120 : center + 91, y - 1, 29, 24);
            int x = left ? center - 117 : center + 101;
            g.renderItem(p.offhand(), x, y + 3);
            g.renderItemDecorations(mc.font, p.offhand(), x, y + 3);
        }
        renderVitals(g, p, center);
        RenderSystem.disableBlend();
    }
    private static void renderVitals(GuiGraphics g, SpectatorInventoryPayload p, int center) {
        Minecraft mc = Minecraft.getInstance();
        int maxHearts = (int)Math.ceil(p.maxHealth() / 2.0);
        int goldHearts = (int)Math.ceil(p.absorption() / 2.0);
        int slots = (int)Math.min(100L, (long)maxHearts + goldHearts);
        int rows = (slots + 9) / 10;
        int bottom = g.guiHeight() - 39;
        for (int i = 0; i < slots; i++) {
            int x = center - 91 + (i % 10) * 8, y = bottom - (i / 10) * 10;
            g.blitSprite(sprite("heart/container"), x, y, 9, 9);
            boolean gold = i >= maxHearts;
            double points = gold ? Math.ceil(p.absorption()) - (i - maxHearts) * 2.0 : Math.ceil(p.health()) - i * 2.0;
            if (points > 0) g.blitSprite(sprite("heart/" + (gold ? "absorbing_" : "")
                    + (points >= 2 ? "full" : "half")), x, y, 9, 9);
        }
        int armorY = bottom - rows * 10;
        for (int i = 0; i < 10; i++) {
            int points = p.armorValue() - i * 2;
            g.blitSprite(sprite("armor_" + (points >= 2 ? "full" : points == 1 ? "half" : "empty")),
                    center - 91 + i * 8, armorY, 9, 9);
        }
        // Actual equipped pieces, helmet to boots; this view never opens an inventory menu.
        for (int i = 0; i < 4; i++) {
            ItemStack item = p.armor().get(3 - i);
            int x = center + 10 + i * 20;
            g.renderItem(item, x, bottom - 6);
            g.renderItemDecorations(mc.font, item, x, bottom - 6);
        }
        if ((long)maxHearts + goldHearts > 100 || p.armorValue() > 20)
            g.drawString(mc.font, "HP " + (int)Math.ceil(p.health()) + "/" + (int)Math.ceil(p.maxHealth())
                    + " +" + (int)Math.ceil(p.absorption()) + " | Armadura " + p.armorValue(),
                    center - 91, armorY - 11, 0xFFFFFFFF, true);
    }
    private static final class Mirror extends LocalPlayer {
        private final AbstractClientPlayer target;
        Mirror(Minecraft mc, ClientLevel level, ClientPacketListener connection, AbstractClientPlayer target) {
            super(mc, level, connection, new StatsCounter(), new ClientRecipeBook(), false, false);
            this.target = target;
        }
        void update() {
            var p = SpectatorInventoryView.get();
            getInventory().selected = p.selected();
            for (int i = 0; i < 9; i++) getInventory().setItem(i, p.hotbar().get(i).copy());
            getInventory().offhand.set(0, p.offhand().copy());
            setPos(target.getX(), target.getY(), target.getZ());
            setYRot(target.getYRot()); setXRot(target.getXRot());
            xRotO = target.xRotO; yRotO = target.yRotO;
            xBobO = target.xRotO; xBob = target.getXRot(); yBobO = target.yRotO; yBob = target.getYRot();
            swingingArm = target.swingingArm; tickCount = target.tickCount;
        }
        @Override public PlayerSkin getSkin() { return target == null ? super.getSkin() : target.getSkin(); }
        @Override public HumanoidArm getMainArm() { return target == null ? HumanoidArm.RIGHT : target.getMainArm(); }
        @Override public float getAttackAnim(float partial) { return target == null ? 0 : target.getAttackAnim(partial); }
        @Override public float getAttackStrengthScale(float partial) { return snapshot == null ? 1 : snapshot.attackStrength(); }
        @Override public boolean isInvisible() { return target != null && target.isInvisible(); }
        @Override public boolean isSpectator() { return false; }
        @Override public boolean isHandsBusy() { return false; }
        @Override public boolean isUsingItem() { return getUseItemRemainingTicks() > 0; }
        @Override public int getUseItemRemainingTicks() { return snapshot == null ? 0 : Math.max(0, snapshot.useTicks() - useAge); }
        @Override public InteractionHand getUsedItemHand() {
            return snapshot != null && snapshot.usingOffhand() ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
        }
        @Override public ItemStack getUseItem() { return isUsingItem() ? getItemInHand(getUsedItemHand()) : ItemStack.EMPTY; }
    }
}
