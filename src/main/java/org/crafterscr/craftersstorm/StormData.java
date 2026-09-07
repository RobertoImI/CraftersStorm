package org.crafterscr.craftersstorm;

import com.google.gson.Gson;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.List;

public final class StormData extends SavedData {
    private static final Gson GSON = new Gson();

    public Values values = new Values();

    public enum Mode {
        IDLE,
        WAITING,
        CLOSING,
        FINAL
    }

    public record Phase(
            double size,
            int waitSeconds,
            int closeSeconds,
            float damage
    ) {
    }

    public static final class Values {
        public boolean configured;
        public double initialX;
        public double initialZ;
        public double initialSize;

        public List<Phase> phases = new ArrayList<>();

        public Mode mode = Mode.IDLE;
        public boolean paused;
        public int phaseIndex;
        public long elapsed;
        public long duration;

        public double x;
        public double z;
        public double size;

        public double fromX;
        public double fromZ;
        public double fromSize;

        public double targetX;
        public double targetZ;
        public double targetSize;

        public double previousX;
        public double previousZ;
        public double previousSize;
        public double previousDamage;
        public double previousBuffer;
    }

    public static StormData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(
                        StormData::new,
                        StormData::load,
                        null
                ),
                "craftersstorm"
        );
    }

    private static StormData load(
            CompoundTag tag,
            HolderLookup.Provider registries
    ) {
        StormData data = new StormData();

        if (tag.contains("json")) {
            Values loaded = GSON.fromJson(tag.getString("json"), Values.class);

            if (loaded == null || loaded.phases == null || loaded.mode == null) {
                throw new IllegalStateException(
                        "Los datos guardados de CraftersStorm no son validos."
                );
            }

            data.values = loaded;
        }

        return data;
    }

    @Override
    public CompoundTag save(
            CompoundTag tag,
            HolderLookup.Provider registries
    ) {
        tag.putString("json", GSON.toJson(values));
        return tag;
    }
}