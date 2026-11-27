package event;

import model.Task;

import java.util.Map;
import java.util.TreeMap;

/** Counts how many times each event type happened. */
public class AuditTrailListener implements TaskEventListener {
    private final Map<String, Integer> counts = new TreeMap<>();

    @Override
    public synchronized void onEvent(String eventType, Task task) {
        counts.merge(eventType, 1, Integer::sum);
    }

    public synchronized int count(String eventType) {
        return counts.getOrDefault(eventType, 0);
    }

    public synchronized Map<String, Integer> getCounts() {
        return new TreeMap<>(counts);
    }
}
