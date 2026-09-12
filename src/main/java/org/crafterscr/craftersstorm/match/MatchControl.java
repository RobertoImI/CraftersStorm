package org.crafterscr.craftersstorm.match;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** action 0: own camera; -1/1: spectator target; 2: hold self-revive; 3: release/cancel self-revive. */
public record MatchControl(int action, int perspective, int fov) implements CustomPacketPayload {
    public static final Type<MatchControl> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("craftersstorm", "match_control"));
    public static final StreamCodec<RegistryFriendlyByteBuf, MatchControl> CODEC = new StreamCodec<>() {
        public MatchControl decode(RegistryFriendlyByteBuf b) {
            return new MatchControl(b.readInt(), b.readInt(), b.readInt());
        }

        public void encode(RegistryFriendlyByteBuf b, MatchControl p) {
            b.writeInt(p.action);
            b.writeInt(p.perspective);
            b.writeInt(p.fov);
        }
    };

    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
