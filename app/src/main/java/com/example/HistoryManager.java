package com.example;

import java.util.ArrayList;
import java.util.List;

public class HistoryManager {

    private static final int MAX_HISTORY_ITEMS = 5;
    private static final List<HistoryEntry> historyList = new ArrayList<>();

    public static class HistoryEntry {
        private final long timestamp;
        private final String description;

        public HistoryEntry(String description) {
            this.timestamp = System.currentTimeMillis();
            this.description = description;
        }

        public String getDescription() {
            return description;
        }

        public long getTimestamp() {
            return timestamp;
        }
    }

    public static synchronized void addEntry(String description) {
        if (description == null || description.trim().isEmpty()) return;
        historyList.add(new HistoryEntry(description.trim()));
        while (historyList.size() > MAX_HISTORY_ITEMS) {
            historyList.remove(0);
        }
    }

    public static synchronized String getRecentHistoryContext() {
        if (historyList.isEmpty()) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("Recent discoveries from this session:\n");
        long now = System.currentTimeMillis();
        for (int i = 0; i < historyList.size(); i++) {
            HistoryEntry entry = historyList.get(i);
            long minutesAgo = (now - entry.getTimestamp()) / (1000 * 60);
            sb.append("- ").append(i + 1).append(") ").append(entry.getDescription());
            if (minutesAgo > 0) {
                sb.append(" (").append(minutesAgo).append("m ago)");
            }
            sb.append("\n");
        }
        return sb.toString().trim();
    }

    public static synchronized void clearHistory() {
        historyList.clear();
    }
}
