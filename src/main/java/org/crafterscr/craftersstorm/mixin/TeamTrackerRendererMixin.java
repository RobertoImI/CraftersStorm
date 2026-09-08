package org.crafterscr.craftersstorm.mixin;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;
import org.crafterscr.craftersstorm.match.TeamView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import xaero.hud.minimap.element.render.MinimapElementRenderInfo;
import xaero.hud.minimap.element.render.MinimapElementRenderLocation;
import xaero.hud.minimap.player.tracker.PlayerTrackerMinimapElement;

@Pseudo
@Mixin(targets = "xaero.hud.minimap.player.tracker.PlayerTrackerMinimapElementRenderer", remap = false)
public abstract class TeamTrackerRendererMixin {
    @Inject(method = "renderElement(Lxaero/hud/minimap/player/tracker/PlayerTrackerMinimapElement;ZZDFDDLxaero/hud/minimap/element/render/MinimapElementRenderInfo;Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/client/renderer/MultiBufferSource$BufferSource;)Z",
            at = @At("HEAD"), cancellable = true, remap = false)
    private void craftersstorm$filter(PlayerTrackerMinimapElement<?> e, boolean highlighted, boolean out, double depth,
            float scale, double x, double y, MinimapElementRenderInfo info, GuiGraphics g,
            MultiBufferSource.BufferSource buffers, CallbackInfoReturnable<Boolean> ci) {
        if (TeamView.locked() && (!TeamView.visible(e.getPlayerId()) || info.location == MinimapElementRenderLocation.IN_WORLD))
            ci.setReturnValue(false);
    }
    @Inject(method = "shouldRender(Lxaero/hud/minimap/element/render/MinimapElementRenderLocation;)Z",
            at = @At("HEAD"), cancellable = true, remap = false)
    private void craftersstorm$enableHeads(MinimapElementRenderLocation location, CallbackInfoReturnable<Boolean> ci) {
        if (TeamView.locked()) ci.setReturnValue(location != MinimapElementRenderLocation.IN_WORLD);
    }
    @ModifyConstant(method = "renderElement(Lxaero/hud/minimap/player/tracker/PlayerTrackerMinimapElement;ZZDFDDLxaero/hud/minimap/element/render/MinimapElementRenderInfo;Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/client/renderer/MultiBufferSource$BufferSource;)Z",
            constant = @Constant(doubleValue = 10.0, ordinal = 0), remap = false)
    private double craftersstorm$nearbyHeads(double original) { return TeamView.locked() ? 0.0 : original; }
}
