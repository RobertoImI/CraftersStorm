package org.crafterscr.craftersstorm;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record StormPayload(
        boolean active,
        boolean damaging,
        double x,
        double z,
        double size,
        double targetX,
        double targetZ,
        double targetSize
) implements CustomPacketPayload {

    public static final StormPayload EMPTY =
            new StormPayload(false, false, 0, 0, 0, 0, 0, 0);

    public static final Type<StormPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(
                    CraftersStorm.MOD_ID,
                    "storm_state"
            )
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, StormPayload> CODEC =
            new StreamCodec<>() {
                @Override
                public StormPayload decode(RegistryFriendlyByteBuf buffer) {
                    return new StormPayload(
                            buffer.readBoolean(),
                            buffer.readBoolean(),
                            buffer.readDouble(),
                            buffer.readDouble(),
                            buffer.readDouble(),
                            buffer.readDouble(),
                            buffer.readDouble(),
                            buffer.readDouble()
                    );
                }

                @Override
                public void encode(
                        RegistryFriendlyByteBuf buffer,
                        StormPayload value
                ) {
                    buffer.writeBoolean(value.active());
                    buffer.writeBoolean(value.damaging());
                    buffer.writeDouble(value.x());
                    buffer.writeDouble(value.z());
                    buffer.writeDouble(value.size());
                    buffer.writeDouble(value.targetX());
                    buffer.writeDouble(value.targetZ());
                    buffer.writeDouble(value.targetSize());
                }
            };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}