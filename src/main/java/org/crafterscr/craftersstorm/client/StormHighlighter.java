package org.crafterscr.craftersstorm.client;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import org.crafterscr.craftersstorm.StormPayload;
import org.crafterscr.craftersstorm.StormView;
import xaero.common.minimap.highlight.AbstractHighlighter;
import xaero.hud.minimap.info.render.compile.InfoDisplayCompiler;

public final class StormHighlighter extends AbstractHighlighter {
    private static final int STORM = color(140, 45, 220, 105);
    private static final int CURRENT = color(40, 210, 255, 240);
    private static final int NEXT = color(255, 255, 255, 245);

    public StormHighlighter() {
        super(true);
    }

    private static int color(int red, int green, int blue, int alpha) {
        // Formato utilizado por el resaltado de esta versión de Xaero.
        return (blue << 24) | (green << 16) | (red << 8) | alpha;
    }

    private static boolean enabled(ResourceKey<Level> dimension) {
        return Level.OVERWORLD.equals(dimension) && StormView.get().active();
    }

    @Override
    public boolean regionHasHighlights(
            ResourceKey<Level> dimension,
            int regionX,
            int regionZ
    ) {
        return enabled(dimension);
    }

    @Override
    public boolean chunkIsHighlit(
            ResourceKey<Level> dimension,
            int chunkX,
            int chunkZ
    ) {
        return enabled(dimension);
    }

    @Override
    public int[] getChunkHighlitColor(
            ResourceKey<Level> dimension,
            int chunkX,
            int chunkZ
    ) {
        StormPayload view = StormView.get();

        if (!Level.OVERWORLD.equals(dimension) || !view.active()) {
            return null;
        }

        int[] colors = new int[256];

        for (int z = 0; z < 16; z++) {
            for (int x = 0; x < 16; x++) {
                double worldX = chunkX * 16.0 + x + 0.5;
                double worldZ = chunkZ * 16.0 + z + 0.5;

                double currentDistance = squareDistance(
                        worldX, worldZ,
                        view.x(), view.z(), view.size()
                );

                double nextDistance = squareDistance(
                        worldX, worldZ,
                        view.targetX(), view.targetZ(), view.targetSize()
                );

                int value = currentDistance > 0 ? STORM : 0;

                if (Math.abs(currentDistance) <= 0.75) {
                    value = CURRENT;
                }

                if (Math.abs(nextDistance) <= 0.75) {
                    value = NEXT;
                }

                colors[(z << 4) | x] = value;
            }
        }

        return colors;
    }

    private static double squareDistance(
            double x,
            double z,
            double centerX,
            double centerZ,
            double size
    ) {
        return Math.max(
                Math.abs(x - centerX),
                Math.abs(z - centerZ)
        ) - size / 2.0;
    }

    @Override
    public void addBlockHighlightTooltips(
            InfoDisplayCompiler compiler,
            ResourceKey<Level> dimension,
            int blockX,
            int blockZ,
            int width
    ) {
        // No añadimos texto adicional debajo del minimapa.
    }
}