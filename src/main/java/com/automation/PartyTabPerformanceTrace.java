package com.automation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

final class PartyTabPerformanceTrace {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String PARTY_SECTION = "Party Info (P)";

    private final List<Event> events = new ArrayList<>();

    private String flowLabel = "UNKNOWN";
    private String activeSection = "";
    private long partyStartEpochMs = -1L;
    private long partyCompleteEpochMs = -1L;
    private long nextNavigationStartEpochMs = -1L;
    private long nextTabStartEpochMs = -1L;
    private String nextTabName = "";

    void reset(String flowLabel) {
        this.flowLabel = blank(flowLabel) ? "UNKNOWN" : flowLabel.trim();
        this.activeSection = "";
        this.partyStartEpochMs = -1L;
        this.partyCompleteEpochMs = -1L;
        this.nextNavigationStartEpochMs = -1L;
        this.nextTabStartEpochMs = -1L;
        this.nextTabName = "";
        this.events.clear();
    }

    void enterSection(String sectionName) {
        activeSection = blank(sectionName) ? "" : sectionName.trim();
        if (PARTY_SECTION.equalsIgnoreCase(activeSection) && partyStartEpochMs < 0L) {
            partyStartEpochMs = System.currentTimeMillis();
            addMarker("party_start", activeSection, partyStartEpochMs, "Party tab became active.");
        }
    }

    void leaveSection(String sectionName) {
        if (!blank(sectionName) && sectionName.equalsIgnoreCase(activeSection)) {
            activeSection = "";
        }
    }

    void markPartyComplete() {
        if (partyStartEpochMs < 0L || partyCompleteEpochMs >= 0L) {
            return;
        }
        partyCompleteEpochMs = System.currentTimeMillis();
        addMarker("party_complete", PARTY_SECTION, partyCompleteEpochMs, "Party field population completed.");
    }

    void markNextNavigationStart(String detail) {
        if (partyCompleteEpochMs < 0L || nextNavigationStartEpochMs >= 0L) {
            return;
        }
        nextNavigationStartEpochMs = System.currentTimeMillis();
        addMarker("next_navigation_start", nextTabName, nextNavigationStartEpochMs, detail);
    }

    boolean awaitingNextTabStart() {
        return partyCompleteEpochMs >= 0L && nextTabStartEpochMs < 0L;
    }

    void markNextTabStart(String sectionName) {
        if (!awaitingNextTabStart()) {
            return;
        }
        nextTabStartEpochMs = System.currentTimeMillis();
        nextTabName = blank(sectionName) ? "" : sectionName.trim();
        addMarker("next_tab_start", nextTabName, nextTabStartEpochMs, "Next tab processing started.");
    }

    boolean isCaptureActive() {
        return PARTY_SECTION.equalsIgnoreCase(activeSection) || awaitingNextTabStart();
    }

    void recordDuration(String category, String name, String detail, long startedAtEpochMs, long durationMs) {
        if (!isCaptureActive()) {
            return;
        }
        events.add(new Event(
                "span",
                safe(category),
                safe(name),
                safe(detail),
                startedAtEpochMs,
                Math.max(0L, durationMs)));
    }

    <T> T time(String category, String name, String detail, Supplier<T> operation) {
        long startedAtEpochMs = System.currentTimeMillis();
        try {
            return operation.get();
        } finally {
            recordDuration(category, name, detail, startedAtEpochMs, System.currentTimeMillis() - startedAtEpochMs);
        }
    }

    void time(String category, String name, String detail, Runnable operation) {
        time(category, name, detail, () -> {
            operation.run();
            return null;
        });
    }

    ObjectNode toJson() {
        ObjectNode root = OBJECT_MAPPER.createObjectNode();
        root.put("flowLabel", flowLabel);
        root.put("partySectionName", PARTY_SECTION);
        putTimestamp(root, "partyStartTime", partyStartEpochMs);
        putTimestamp(root, "partyCompleteTime", partyCompleteEpochMs);
        putTimestamp(root, "nextNavigationStartTime", nextNavigationStartEpochMs);
        putTimestamp(root, "nextTabStartTime", nextTabStartEpochMs);
        if (!blank(nextTabName)) {
            root.put("nextTabName", nextTabName);
        }
        putDuration(root, "partyStartToCompleteMs", partyStartEpochMs, partyCompleteEpochMs);
        putDuration(root, "partyCompleteToNextNavigationMs", partyCompleteEpochMs, nextNavigationStartEpochMs);
        putDuration(root, "partyCompleteToNextTabMs", partyCompleteEpochMs, nextTabStartEpochMs);
        putDuration(root, "nextNavigationToNextTabMs", nextNavigationStartEpochMs, nextTabStartEpochMs);

        ArrayNode eventArray = root.putArray("events");
        for (Event event : events) {
            ObjectNode eventNode = eventArray.addObject();
            eventNode.put("type", event.type());
            eventNode.put("category", event.category());
            eventNode.put("name", event.name());
            if (!blank(event.detail())) {
                eventNode.put("detail", event.detail());
            }
            eventNode.put("startedAtEpochMs", event.startedAtEpochMs());
            eventNode.put("startedAt", Instant.ofEpochMilli(event.startedAtEpochMs()).toString());
            eventNode.put("durationMs", event.durationMs());
        }

        ArrayNode slowOperations = root.putArray("topOperations");
        aggregateOperations().stream()
                .sorted(Comparator.comparingLong(OperationSummary::totalDurationMs).reversed())
                .limit(30)
                .forEach(summary -> {
                    ObjectNode summaryNode = slowOperations.addObject();
                    summaryNode.put("category", summary.category());
                    summaryNode.put("name", summary.name());
                    summaryNode.put("count", summary.count());
                    summaryNode.put("totalDurationMs", summary.totalDurationMs());
                    summaryNode.put("maxDurationMs", summary.maxDurationMs());
                    if (!blank(summary.exampleDetail())) {
                        summaryNode.put("exampleDetail", summary.exampleDetail());
                    }
                });
        return root;
    }

    private List<OperationSummary> aggregateOperations() {
        Map<String, MutableSummary> summaries = new LinkedHashMap<>();
        for (Event event : events) {
            if (!"span".equals(event.type())) {
                continue;
            }
            String key = event.category() + "|" + event.name();
            MutableSummary summary = summaries.computeIfAbsent(
                    key,
                    ignored -> new MutableSummary(event.category(), event.name(), event.detail()));
            summary.count++;
            summary.totalDurationMs += event.durationMs();
            summary.maxDurationMs = Math.max(summary.maxDurationMs, event.durationMs());
        }

        List<OperationSummary> result = new ArrayList<>();
        for (MutableSummary summary : summaries.values()) {
            result.add(new OperationSummary(
                    summary.category,
                    summary.name,
                    summary.count,
                    summary.totalDurationMs,
                    summary.maxDurationMs,
                    summary.exampleDetail));
        }
        return result;
    }

    private void addMarker(String category, String name, long timestampEpochMs, String detail) {
        events.add(new Event("marker", safe(category), safe(name), safe(detail), timestampEpochMs, 0L));
    }

    private void putTimestamp(ObjectNode root, String fieldName, long epochMs) {
        if (epochMs < 0L) {
            return;
        }
        ObjectNode node = root.putObject(fieldName);
        node.put("epochMs", epochMs);
        node.put("iso", Instant.ofEpochMilli(epochMs).toString());
    }

    private void putDuration(ObjectNode root, String fieldName, long startEpochMs, long endEpochMs) {
        if (startEpochMs < 0L || endEpochMs < 0L || endEpochMs < startEpochMs) {
            return;
        }
        root.put(fieldName, endEpochMs - startEpochMs);
    }

    private String safe(String value) {
        return blank(value) ? "" : value.trim();
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private record Event(
            String type,
            String category,
            String name,
            String detail,
            long startedAtEpochMs,
            long durationMs) {
    }

    private record OperationSummary(
            String category,
            String name,
            long count,
            long totalDurationMs,
            long maxDurationMs,
            String exampleDetail) {
    }

    private static final class MutableSummary {
        private final String category;
        private final String name;
        private final String exampleDetail;
        private long count;
        private long totalDurationMs;
        private long maxDurationMs;

        private MutableSummary(String category, String name, String exampleDetail) {
            this.category = category;
            this.name = name;
            this.exampleDetail = exampleDetail;
        }
    }
}
