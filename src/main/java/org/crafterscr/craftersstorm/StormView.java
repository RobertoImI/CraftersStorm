package org.crafterscr.craftersstorm;

public final class StormView {
    private record Entry(
            StormPayload payload,
            long receivedAt,
            int revision
    ) {
    }

    private static volatile Entry current =
            new Entry(StormPayload.EMPTY, 0, 0);

    private StormView() {
    }

    public static void receive(StormPayload payload) {
        Entry previous = current;

        current = new Entry(
                payload,
                System.nanoTime(),
                previous.revision() + 1
        );
    }

    private static boolean expired(Entry entry) {
        return System.nanoTime() - entry.receivedAt() > 4_000_000_000L;
    }

    public static StormPayload get() {
        Entry entry = current;

        return expired(entry) ? StormPayload.EMPTY : entry.payload();
    }

    public static int revision() {
        Entry entry = current;

        return entry.revision() * 2 + (expired(entry) ? 1 : 0);
    }
}