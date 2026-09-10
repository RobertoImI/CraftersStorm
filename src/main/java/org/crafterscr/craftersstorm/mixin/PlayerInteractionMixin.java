package org.crafterscr.craftersstorm.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.level.border.WorldBorder;
import org.crafterscr.craftersstorm.CraftersStorm;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ServerGamePacketListenerImpl.class)
public abstract class PlayerInteractionMixin {

    @Shadow
    public ServerPlayer player;

    @Redirect(
            method = "handleInteract",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/border/WorldBorder;isWithinBounds(Lnet/minecraft/core/BlockPos;)Z"
            )
    )
    private boolean craftersstorm$allowAttacksAcrossBorder(WorldBorder border, BlockPos targetPosition) {
        return CraftersStorm.isStormActive(player.getServer()) || border.isWithinBounds(targetPosition);
    }
}
