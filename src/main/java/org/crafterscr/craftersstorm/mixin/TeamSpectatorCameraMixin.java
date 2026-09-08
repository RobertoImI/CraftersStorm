package org.crafterscr.craftersstorm.mixin;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.crafterscr.craftersstorm.CraftersStorm;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Covers vanilla spectator target selection as well as the mod's arrow controls. */
@Mixin(ServerPlayer.class)
public abstract class TeamSpectatorCameraMixin {
    @Inject(method = "setCamera", at = @At("HEAD"), cancellable = true)
    private void craftersstorm$restrictCamera(Entity target, CallbackInfo ci) {
        var match = CraftersStorm.match();
        if (match != null && !match.allowSpectatorCamera((ServerPlayer)(Object)this, target)) ci.cancel();
    }
}
