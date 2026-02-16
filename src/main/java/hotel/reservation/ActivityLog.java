package hotel.reservation;

import java.util.*;

/**
 * Shared activity log for tracking agent-to-agent messages.
 * Used to display CNP protocol interactions on the dashboard.
 */
public final class ActivityLog {

    public record Entry(long timestamp, String from, String to, String type, String detail) {}

    private static final List<Entry> entries = Collections.synchronizedList(new ArrayList<>());

    public static void log(String from, String to, String type, String detail) {
        entries.add(new Entry(System.currentTimeMillis(), from, to, type, detail));
    }

    public static List<Entry> getEntries() {
        return new ArrayList<>(entries);
    }

    public static List<Entry> getEntriesSince(long sinceTimestamp) {
        return entries.stream()
            .filter(e -> e.timestamp() > sinceTimestamp)
            .toList();
    }

    public static void clear() {
        entries.clear();
    }
}
