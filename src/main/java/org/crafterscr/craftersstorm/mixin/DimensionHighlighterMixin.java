package org.crafterscr.craftersstorm.mixin;

import org.crafterscr.craftersstorm.StormView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(
        targets = "xaero.common.minimap.highlight.DimensionHighlighterHandler",
        remap = false
)
public abstract class DimensionHighlighterMixin {

    @Inject(
            method = "getVersion",
            at = @At("RETURN"),
            cancellable = true,
            remap = false
    )
    private void craftersstorm$refresh(
            CallbackInfoReturnable<Integer> callback
    ) {
        callback.setReturnValue(
                callback.getReturnValue() + StormView.revision()
        );
    }
}