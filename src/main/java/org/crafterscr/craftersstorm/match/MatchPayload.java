package org.crafterscr.craftersstorm.match;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record MatchPayload(boolean running, boolean participant, boolean eliminated,
        int alive, int kills, int seconds, String timer, int targetId, int perspective, int fov, String targetName)
        implements CustomPacketPayload {
    public static final MatchPayload EMPTY = new MatchPayload(false,false,false,0,0,0,"",-1,0,70,"");
    public static final Type<MatchPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("craftersstorm","match"));
    public static final StreamCodec<RegistryFriendlyByteBuf,MatchPayload> CODEC = new StreamCodec<>() {
        public MatchPayload decode(RegistryFriendlyByteBuf b) {
            return new MatchPayload(b.readBoolean(),b.readBoolean(),b.readBoolean(),b.readVarInt(),b.readVarInt(),
                    b.readVarInt(),b.readUtf(32),b.readInt(),b.readVarInt(),b.readVarInt(),b.readUtf(64));
        }
        public void encode(RegistryFriendlyByteBuf b, MatchPayload p) {
            b.writeBoolean(p.running); b.writeBoolean(p.participant); b.writeBoolean(p.eliminated);
            b.writeVarInt(p.alive); b.writeVarInt(p.kills); b.writeVarInt(p.seconds); b.writeUtf(p.timer,32);
            b.writeInt(p.targetId); b.writeVarInt(p.perspective); b.writeVarInt(p.fov); b.writeUtf(p.targetName,64);
        }
    };
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
