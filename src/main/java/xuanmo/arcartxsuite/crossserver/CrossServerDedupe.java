package xuanmo.arcartxsuite.crossserver;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

final class CrossServerDedupe {

    private final long ttlMs;
    private final ConcurrentHashMap<String, Long> recent = new ConcurrentHashMap<>();
    private volatile long lastCleanup = 0;
    private static final long CLEANUP_INTERVAL_MS = 60_000L;

    CrossServerDedupe(long ttlMs) {
        this.ttlMs = Math.max(1000L, ttlMs);
    }

    /** @return {@code true} 表示首次见到该 messageId，应处理 */
    boolean registerIfNew(String messageId) {
        if (messageId == null || messageId.isBlank()) {
            return false;
        }
        long now = System.currentTimeMillis();
        if (now - lastCleanup > CLEANUP_INTERVAL_MS || recent.size() >= 256) {
            purgeExpired(now);
            lastCleanup = now;
        }
        Long previous = recent.putIfAbsent(messageId, now);
        return previous == null;
    }

    private void purgeExpired(long now) {
        Iterator<Map.Entry<String, Long>> iterator = recent.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, Long> entry = iterator.next();
            if (now - entry.getValue() > ttlMs) {
                iterator.remove();
            }
        }
    }
}
