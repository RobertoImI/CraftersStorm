package org.crafterscr.craftersstorm.mixin;

import org.crafterscr.craftersstorm.match.TeamView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import xaero.hud.minimap.player.tracker.PlayerTrackerMinimapElement;
import xaero.hud.minimap.player.tracker.PlayerTrackerMinimapElementRenderContext;

@Pseudo
@Mixin(targets = "xaero.hud.minimap.player.tracker.PlayerTrackerMinimapElementReader", remap = false)
public abstract class TeamTrackerReaderMixin {
    @Inject(method = "isHidden(Lxaero/hud/minimap/player/tracker/PlayerTrackerMinimapElement;Lxaero/hud/minimap/player/tracker/PlayerTrackerMinimapElementRenderContext;)Z",
            at = @At("HEAD"), cancellable = true, remap = false)
    private void craftersstorm$filter(PlayerTrackerMinimapElement<?> e, PlayerTrackerMinimapElementRenderContext context,
            CallbackInfoReturnable<Boolean> ci) {
        if (TeamView.locked() && !TeamView.visible(e.getPlayerId())) ci.setReturnValue(true);
    }
}
