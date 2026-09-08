package org.crafterscr.craftersstorm.mixin;

import org.crafterscr.craftersstorm.client.MatchHud;
import org.crafterscr.craftersstorm.match.MatchView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Reserve room in Xaero's layout so a bottom-anchored map also keeps its panel on screen. */
@Pseudo
@Mixin(targets = "xaero.hud.minimap.module.MinimapSession", remap = false)
public abstract class MinimapHeightMixin {
    @Inject(method = "getHeight", at = @At("RETURN"), cancellable = true, remap = false)
    private void craftersstorm$height(double scale, CallbackInfoReturnable<Integer> ci) {
        if (MatchView.get().running()) ci.setReturnValue(ci.getReturnValue() + MatchHud.HEIGHT + 1);
    }
}
