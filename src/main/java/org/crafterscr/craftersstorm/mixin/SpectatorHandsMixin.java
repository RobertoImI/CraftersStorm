package org.crafterscr.craftersstorm.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.level.GameType;
import org.crafterscr.craftersstorm.client.SpectatorPresentation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(GameRenderer.class)
public abstract class SpectatorHandsMixin {
    @Redirect(method = "renderItemInHand", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/multiplayer/MultiPlayerGameMode;getPlayerMode()Lnet/minecraft/world/level/GameType;"))
    private GameType craftersstorm$allowHands(MultiPlayerGameMode mode) {
        return SpectatorPresentation.ready() ? GameType.SURVIVAL : mode.getPlayerMode();
    }
    @Redirect(method = "renderItemInHand", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/ItemInHandRenderer;renderHandsWithItems(FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource$BufferSource;Lnet/minecraft/client/player/LocalPlayer;I)V"))
    private void craftersstorm$renderHands(ItemInHandRenderer renderer, float partial, PoseStack poses,
            MultiBufferSource.BufferSource buffers, LocalPlayer player, int light) {
        if (SpectatorPresentation.ready()) SpectatorPresentation.renderHands(partial, poses, buffers);
        else renderer.renderHandsWithItems(partial, poses, buffers, player, light);
    }
}
