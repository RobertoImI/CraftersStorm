package org.crafterscr.craftersstorm.client;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import org.crafterscr.craftersstorm.match.TeamPayload;
import org.crafterscr.craftersstorm.match.TeamView;
import xaero.common.HudMod;
import xaero.hud.minimap.player.tracker.system.*;
import java.util.Iterator;
import java.util.UUID;

/** Loaded only on clients with Xaero 25.3.13. Uses Xaero's player-head marker renderer. */
public final class XaeroTeamTracker implements IRenderedPlayerTracker<TeamPayload.Teammate>, ITrackedPlayerReader<TeamPayload.Teammate> {
    private static final XaeroTeamTracker INSTANCE = new XaeroTeamTracker();
    private static RenderedPlayerTrackerManager installed;
    public static void install() {
        if (HudMod.INSTANCE == null || !HudMod.INSTANCE.isLoadedClient()) return;
        var manager = HudMod.INSTANCE.getPlayerTracker();
        if (manager != null && manager != installed) {
            manager.register("craftersstorm_teams", INSTANCE); installed = manager;
        }
    }
    public ITrackedPlayerReader<TeamPayload.Teammate> getReader() { return this; }
    public Iterator<TeamPayload.Teammate> getTrackedPlayerIterator() { return TeamView.teammates().iterator(); }
    public UUID getId(TeamPayload.Teammate p) { return p.id(); }
    public double getX(TeamPayload.Teammate p) { return p.x(); }
    public double getY(TeamPayload.Teammate p) { return p.y(); }
    public double getZ(TeamPayload.Teammate p) { return p.z(); }
    public ResourceKey<Level> getDimension(TeamPayload.Teammate p) { return ResourceKey.create(Registries.DIMENSION, p.dimension()); }
}
