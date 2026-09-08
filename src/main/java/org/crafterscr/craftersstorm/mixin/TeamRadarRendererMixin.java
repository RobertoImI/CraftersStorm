package org.crafterscr.craftersstorm.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.crafterscr.craftersstorm.match.TeamView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import xaero.hud.minimap.element.render.MinimapElementRenderInfo;

@Pseudo
@Mixin(targets = "xaero.hud.minimap.radar.render.element.RadarRenderer", remap = false)
public abstract class TeamRadarRendererMixin {
    @Inject(method = "renderElement(Lnet/minecraft/world/entity/Entity;ZZDFDDLxaero/hud/minimap/element/render/MinimapElementRenderInfo;Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/client/renderer/MultiBufferSource$BufferSource;)Z",
            at = @At("HEAD"), cancellable = true, remap = false)
    private void craftersstorm$filter(Entity e, boolean highlighted, boolean out, double depth, float scale,
            double x, double y, MinimapElementRenderInfo info, GuiGraphics g, MultiBufferSource.BufferSource buffers,
            CallbackInfoReturnable<Boolean> ci) {
        if (TeamView.locked() && e instanceof Player && e != Minecraft.getInstance().player) ci.setReturnValue(false);
    }
}
