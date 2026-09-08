package org.crafterscr.craftersstorm.mixin;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import org.crafterscr.craftersstorm.client.SpectatorPresentation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Gui.class)
public abstract class SpectatorHotbarMixin {
    @Inject(method = "renderHotbar", at = @At("HEAD"), cancellable = true)
    private void craftersstorm$hotbar(GuiGraphics graphics, DeltaTracker delta, CallbackInfo ci) {
        if (SpectatorPresentation.ready()) {
            SpectatorPresentation.renderHotbar(graphics); ci.cancel();
        }
    }
    @Inject(method = "maybeRenderSpectatorTooltip", at = @At("HEAD"), cancellable = true)
    private void craftersstorm$tooltip(GuiGraphics graphics, DeltaTracker delta, CallbackInfo ci) {
        if (SpectatorPresentation.ready()) ci.cancel();
    }
}
