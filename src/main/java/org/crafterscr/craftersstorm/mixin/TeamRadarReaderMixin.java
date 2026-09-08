package org.crafterscr.craftersstorm.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.crafterscr.craftersstorm.match.TeamView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import xaero.hud.minimap.radar.render.element.RadarRenderContext;

@Pseudo
@Mixin(targets = "xaero.hud.minimap.radar.render.element.RadarElementReader", remap = false)
public abstract class TeamRadarReaderMixin {
    @Inject(method = "isHidden(Lnet/minecraft/world/entity/Entity;Lxaero/hud/minimap/radar/render/element/RadarRenderContext;)Z",
            at = @At("HEAD"), cancellable = true, remap = false)
    private void craftersstorm$hidePlayers(Entity e, RadarRenderContext context, CallbackInfoReturnable<Boolean> ci) {
        // Teammates use the head tracker instead, so no duplicate dots/icons.
        if (TeamView.locked() && e instanceof Player && e != Minecraft.getInstance().player) ci.setReturnValue(true);
    }
}
