package org.crafterscr.craftersstorm.mixin;

import net.minecraft.client.gui.GuiGraphics;
import org.crafterscr.craftersstorm.client.MatchHud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xaero.hud.minimap.module.MinimapSession;
import xaero.hud.render.module.ModuleRenderContext;

/** Hook only the successful render path, after the minimap restores its render state. */
@Pseudo
@Mixin(targets = "xaero.hud.minimap.module.MinimapRenderer", remap = false)
public abstract class MinimapHudMixin {
    @Inject(method = "render(Lxaero/hud/minimap/module/MinimapSession;Lxaero/hud/render/module/ModuleRenderContext;Lnet/minecraft/client/gui/GuiGraphics;F)V",
            at = @At(value = "INVOKE", target = "Lxaero/common/minimap/render/MinimapRendererHelper;restoreDefaultShaderBlendState()V",
                    ordinal = 1, shift = At.Shift.AFTER), remap = false)
    private void craftersstorm$hud(MinimapSession session, ModuleRenderContext c, GuiGraphics graphics, float partial, CallbackInfo ci) {
        MatchHud.render(graphics, c.x, c.y + c.w + 1, c.w);
    }
}
