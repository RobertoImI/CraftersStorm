package org.crafterscr.craftersstorm.match;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Personalized: never sends opponent coordinates. */
public record TeamPayload(boolean locked, List<Teammate> teammates) implements CustomPacketPayload {
    public record Teammate(UUID id, double x, double y, double z, ResourceLocation dimension) {}
    public TeamPayload {
        if (teammates.size() > 3) throw new IllegalArgumentException("Too many teammates");
        teammates = List.copyOf(teammates);
    }
    public static final Type<TeamPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("craftersstorm", "team"));
    public static final StreamCodec<RegistryFriendlyByteBuf, TeamPayload> CODEC = new StreamCodec<>() {
        public TeamPayload decode(RegistryFriendlyByteBuf b) {
            boolean locked = b.readBoolean(); int count = b.readUnsignedByte();
            if (count > 3) throw new IllegalArgumentException("Too many teammates");
            List<Teammate> list = new ArrayList<>(count);
            for (int i=0; i<count; i++) list.add(new Teammate(b.readUUID(), b.readDouble(), b.readDouble(), b.readDouble(), b.readResourceLocation()));
            return new TeamPayload(locked, list);
        }
        public void encode(RegistryFriendlyByteBuf b, TeamPayload p) {
            b.writeBoolean(p.locked); b.writeByte(p.teammates.size());
            for (Teammate t : p.teammates) {
                b.writeUUID(t.id); b.writeDouble(t.x); b.writeDouble(t.y); b.writeDouble(t.z); b.writeResourceLocation(t.dimension);
            }
        }
    };
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
