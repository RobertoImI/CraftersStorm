package org.crafterscr.craftersstorm.match;

public final class ReviveView {
    private static RevivePayload current = RevivePayload.EMPTY;

    private ReviveView() {}

    public static void receive(RevivePayload payload) {
        current = payload == null ? RevivePayload.EMPTY : payload;
    }

    public static RevivePayload get() {
        return current;
    }

    public static void clear() {
        current = RevivePayload.EMPTY;
    }
}
