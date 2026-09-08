package org.crafterscr.craftersstorm.match;

import java.util.List;
import java.util.UUID;

public final class TeamView {
    private record Entry(TeamPayload packet, long received) {}
    private static volatile Entry entry = new Entry(new TeamPayload(false, List.of()), 0);
    private TeamView() {}
    public static void receive(TeamPayload p) { entry = new Entry(p, System.nanoTime()); }
    public static void clear() { receive(new TeamPayload(false, List.of())); }
    // A delayed packet must never unlock the opponents' radar: only an explicit stop/logout does.
    public static boolean locked() { return entry.packet.locked(); }
    public static List<TeamPayload.Teammate> teammates() {
        Entry e = entry;
        return e.packet.locked() && System.nanoTime() - e.received < 2_000_000_000L ? e.packet.teammates() : List.of();
    }
    public static boolean visible(UUID id) { return teammates().stream().anyMatch(t -> t.id().equals(id)); }
}
