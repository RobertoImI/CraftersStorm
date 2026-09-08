package org.crafterscr.craftersstorm.match;

/** Common-side class: safe to reference from packet registration on dedicated servers. */
public final class MatchView {
    private record Entry(MatchPayload state, long time) {}
    private static volatile Entry entry = new Entry(MatchPayload.EMPTY, 0);
    private MatchView() {}
    public static void receive(MatchPayload p) { entry = new Entry(p, System.nanoTime()); }
    public static MatchPayload get() {
        Entry e = entry;
        return System.nanoTime() - e.time > 5_000_000_000L ? MatchPayload.EMPTY : e.state;
    }
}
