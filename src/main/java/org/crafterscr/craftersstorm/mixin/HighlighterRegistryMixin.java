package org.crafterscr.craftersstorm.mixin;

import org.crafterscr.craftersstorm.client.StormHighlighter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xaero.common.minimap.highlight.HighlighterRegistry;

@Pseudo
@Mixin(
        targets = "xaero.common.minimap.highlight.HighlighterRegistry",
        remap = false
)
public abstract class HighlighterRegistryMixin {

    @Inject(method = "end", at = @At("HEAD"), remap = false)
    private void craftersstorm$register(CallbackInfo callback) {
        HighlighterRegistry registry = (HighlighterRegistry) (Object) this;
        registry.register(new StormHighlighter());
    }
}