package org.crafterscr.craftersstorm.match;

/** Packet storage without client class references, safe to load on a dedicated server. */
public final class SpectatorInventoryView {
    private record Entry(SpectatorInventoryPayload payload, long received) {}
    private static volatile Entry entry;
    public static void receive(SpectatorInventoryPayload p) { entry = new Entry(p, System.nanoTime()); }
    public static void clear() { entry = null; }
    public static SpectatorInventoryPayload get() {
        Entry e = entry;
        return e == null || System.nanoTime() - e.received > 2_000_000_000L ? null : e.payload;
    }
}
