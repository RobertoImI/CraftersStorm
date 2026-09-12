package org.crafterscr.craftersstorm.match;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record RevivePayload(boolean downed, boolean selfReviveAvailable, int selfReviveProgress,
                            boolean deploymentProtected) implements CustomPacketPayload {
    public static final RevivePayload EMPTY = new RevivePayload(false, false, 0, false);
    public static final Type<RevivePayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("craftersstorm", "revive"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RevivePayload> CODEC = new StreamCodec<>() {
        @Override
        public RevivePayload decode(RegistryFriendlyByteBuf b) {
            return new RevivePayload(b.readBoolean(), b.readBoolean(), b.readVarInt(), b.readBoolean());
        }

        @Override
        public void encode(RegistryFriendlyByteBuf b, RevivePayload p) {
            b.writeBoolean(p.downed());
            b.writeBoolean(p.selfReviveAvailable());
            b.writeVarInt(p.selfReviveProgress());
            b.writeBoolean(p.deploymentProtected());
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
