package nz.ac.aut.comp713.consumer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class ConsumerStoreTest {
    @TempDir Path temporary;

    private final String payload = """
            {"eventId":"event-123","type":"task.submitted","taskId":42,"projectId":7,
             "title":"Inspect equipment","workerId":2,"completionNote":"Checked on mobile",
             "occurredAt":"2026-10-31T00:00:00Z"}
            """;

    @Test void durableResultSurvivesRestartAndSuppressesRedelivery() throws Exception {
        Path file = temporary.resolve("notifications");
        ConsumerStore first = new ConsumerStore(file);
        TaskSubmitted event = TaskSubmitted.parse(payload, "event-123");
        assertEquals(1, first.observed(event.eventId(), false));
        assertTrue(first.save(event));

        // Simulates crash after H2 commit, before broker acknowledgement.
        ConsumerStore restarted = new ConsumerStore(file);
        assertEquals(2, restarted.observed(event.eventId(), true));
        assertFalse(restarted.save(event));
        String summary = String.join("\n", restarted.status());
        assertTrue(summary.contains("PERSISTED_NOTIFICATIONS count=1"));
        assertTrue(summary.contains("attempts=2 redeliveries=1 duplicates=1"));
        assertEquals(payload, restarted.lastMessage()[1]);
    }

    @Test void failedProcessingLeavesNoResultAndCanRetry() throws Exception {
        ConsumerStore store = new ConsumerStore(temporary.resolve("retry"));
        assertEquals(1, store.observed("event-123", false));
        store.outcome("event-123", "RETRY_ROLLBACK");
        assertTrue(store.status().get(0).endsWith("count=0"));
        assertEquals(2, store.observed("event-123", true));
        assertTrue(store.save(TaskSubmitted.parse(payload, "event-123")));
        assertTrue(store.status().get(0).endsWith("count=1"));
    }

    @Test void eventHeaderAndContractAreValidated() {
        assertThrows(IllegalArgumentException.class, () -> TaskSubmitted.parse(payload, "other"));
        assertThrows(IllegalArgumentException.class, () -> TaskSubmitted.parse(payload.replace("task.submitted", "task.deleted"), "event-123"));
        assertThrows(IllegalArgumentException.class, () -> TaskSubmitted.parse(payload.replace("\"taskId\":42", "\"taskId\":0"), "event-123"));
    }
}
