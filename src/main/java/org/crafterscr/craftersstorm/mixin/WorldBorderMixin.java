package org.crafterscr.craftersstorm.mixin;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.phys.AABB;
import org.crafterscr.craftersstorm.CraftersStorm;
import org.crafterscr.craftersstorm.StormView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(WorldBorder.class)
public abstract class WorldBorderMixin {

    @Inject(
            method = "isInsideCloseToBorder",
            at = @At("HEAD"),
            cancellable = true
    )
    private void craftersstorm$allowCrossing(
            Entity entity,
            AABB bounds,
            CallbackInfoReturnable<Boolean> callback
    ) {
        if (!Level.OVERWORLD.equals(entity.level().dimension())) {
            return;
        }

        if ((Object) this != entity.level().getWorldBorder()) {
            return;
        }

        boolean active = entity.level().isClientSide()
                ? StormView.get().active()
                : CraftersStorm.isStormActive(entity.getServer());

        if (active) {
            callback.setReturnValue(false);
        }
    }
}