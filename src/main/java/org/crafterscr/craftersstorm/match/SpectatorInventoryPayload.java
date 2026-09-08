package org.crafterscr.craftersstorm.match;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Only the server sends this snapshot, and only to eliminated participants following its owner. */
public record SpectatorInventoryPayload(int targetId, UUID targetUuid, int selected,
        List<ItemStack> hotbar, ItemStack offhand, int useTicks, boolean usingOffhand, float attackStrength,
        float health, float maxHealth, float absorption, int armorValue, List<ItemStack> armor)
        implements CustomPacketPayload {
    public SpectatorInventoryPayload {
        if (hotbar.size() != 9 || selected < 0 || selected > 8 || useTicks < 0)
            throw new IllegalArgumentException("Invalid spectator inventory");
        if (armor.size() != 4 || !Float.isFinite(health) || !Float.isFinite(maxHealth)
                || !Float.isFinite(absorption) || health < 0 || maxHealth <= 0 || absorption < 0 || armorValue < 0)
            throw new IllegalArgumentException("Invalid spectator health/armor");
        hotbar = List.copyOf(hotbar); armor = List.copyOf(armor);
    }
    public static final Type<SpectatorInventoryPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath("craftersstorm", "spectator_inventory"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SpectatorInventoryPayload> CODEC = new StreamCodec<>() {
        public SpectatorInventoryPayload decode(RegistryFriendlyByteBuf b) {
            int id = b.readInt(); UUID uuid = b.readUUID(); int slot = b.readUnsignedByte();
            List<ItemStack> items = new ArrayList<>(9);
            for (int i = 0; i < 9; i++) items.add(ItemStack.OPTIONAL_STREAM_CODEC.decode(b));
            ItemStack offhand = ItemStack.OPTIONAL_STREAM_CODEC.decode(b);
            int useTicks = b.readVarInt(); boolean off = b.readBoolean(); float attack = b.readFloat();
            float health = b.readFloat(), max = b.readFloat(), absorption = b.readFloat(); int armorValue = b.readVarInt();
            List<ItemStack> armor = new ArrayList<>(4);
            for (int i = 0; i < 4; i++) armor.add(ItemStack.OPTIONAL_STREAM_CODEC.decode(b));
            return new SpectatorInventoryPayload(id, uuid, slot, items, offhand,
                    useTicks, off, attack, health, max, absorption, armorValue, armor);
        }
        public void encode(RegistryFriendlyByteBuf b, SpectatorInventoryPayload p) {
            b.writeInt(p.targetId); b.writeUUID(p.targetUuid); b.writeByte(p.selected);
            for (ItemStack item : p.hotbar) ItemStack.OPTIONAL_STREAM_CODEC.encode(b, item);
            ItemStack.OPTIONAL_STREAM_CODEC.encode(b, p.offhand);
            b.writeVarInt(p.useTicks); b.writeBoolean(p.usingOffhand); b.writeFloat(p.attackStrength);
            b.writeFloat(p.health); b.writeFloat(p.maxHealth); b.writeFloat(p.absorption); b.writeVarInt(p.armorValue);
            for (ItemStack item : p.armor) ItemStack.OPTIONAL_STREAM_CODEC.encode(b, item);
        }
    };
    public static SpectatorInventoryPayload capture(ServerPlayer p) {
        List<ItemStack> items = new ArrayList<>(9);
        for (int i = 0; i < 9; i++) items.add(p.getInventory().getItem(i).copy());
        return new SpectatorInventoryPayload(p.getId(), p.getUUID(), p.getInventory().selected,
                items, p.getOffhandItem().copy(), p.isUsingItem() ? p.getUseItemRemainingTicks() : 0,
                p.getUsedItemHand() == InteractionHand.OFF_HAND, p.getAttackStrengthScale(1),
                p.getHealth(), p.getMaxHealth(), p.getAbsorptionAmount(), p.getArmorValue(),
                p.getInventory().armor.stream().map(ItemStack::copy).toList());
    }
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
