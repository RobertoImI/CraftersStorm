package org.crafterscr.craftersstorm.mixin;

import org.crafterscr.craftersstorm.match.MatchView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** During a match the BR panel replaces coordinate/biome info; settings are never changed. */
@Pseudo
@Mixin(targets = "xaero.hud.minimap.info.render.InfoDisplayRenderer", remap = false)
public abstract class MinimapInfoMixin {
    @Inject(method = "render", at = @At("HEAD"), cancellable = true, remap = false)
    private void craftersstorm$replaceInfo(CallbackInfo ci) {
        if (MatchView.get().running()) ci.cancel();
    }
}
