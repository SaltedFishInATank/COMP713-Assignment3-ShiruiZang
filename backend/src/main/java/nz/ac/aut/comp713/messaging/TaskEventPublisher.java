package nz.ac.aut.comp713.messaging;

import jakarta.annotation.Resource;
import jakarta.ejb.Schedule;
import jakarta.ejb.Singleton;
import jakarta.ejb.Startup;
import jakarta.ejb.TransactionAttribute;
import jakarta.ejb.TransactionAttributeType;
import jakarta.inject.Inject;
import jakarta.jms.ConnectionFactory;
import jakarta.jms.DeliveryMode;
import jakarta.jms.JMSContext;
import jakarta.jms.JMSDestinationDefinition;
import jakarta.jms.Queue;
import nz.ac.aut.comp713.persistence.Database;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Publishes committed task submissions from the database outbox to real OpenMQ. */
@Singleton
@Startup
@JMSDestinationDefinition(name = "java:app/jms/TaskEvents", interfaceName = "jakarta.jms.Queue",
        destinationName = "TaskEventsQueue", resourceAdapter = "jmsra")
@TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
public class TaskEventPublisher {
    private static final Logger LOG = Logger.getLogger(TaskEventPublisher.class.getName());

    @Inject
    Database database;

    @Resource(lookup = "java:comp/DefaultJMSConnectionFactory")
    ConnectionFactory connectionFactory;

    @Resource(lookup = "java:app/jms/TaskEvents")
    Queue taskEvents;

    /** This is server-side outbox delivery; clients receive WebSocket updates, without polling. */
    @Schedule(hour = "*", minute = "*", second = "*/5", persistent = false)
    public void dispatch() {
        try {
            List<PendingEvent> pending = database.read(connection -> {
                var events = new ArrayList<PendingEvent>();
                try (var statement = connection.prepareStatement(
                        "SELECT event_id, payload FROM event_outbox WHERE published = FALSE "
                                + "ORDER BY created_at, event_id FETCH FIRST 50 ROWS ONLY");
                     var rows = statement.executeQuery()) {
                    while (rows.next()) events.add(new PendingEvent(rows.getString(1), rows.getString(2)));
                }
                return events;
            });
            if (pending.isEmpty()) return;
            try (JMSContext context = connectionFactory.createContext(JMSContext.AUTO_ACKNOWLEDGE)) {
                var producer = context.createProducer().setDeliveryMode(DeliveryMode.PERSISTENT)
                        .setProperty("eventType", "task.submitted");
                for (PendingEvent event : pending) {
                    producer.setProperty("eventId", event.id()).send(taskEvents, event.payload());
                    // A failure after send but before this update causes redelivery. The consumer
                    // commits its event-id dedupe key with its notification before acknowledging.
                    database.transaction(connection -> {
                        try (var update = connection.prepareStatement(
                                "UPDATE event_outbox SET published = TRUE WHERE event_id = ?")) {
                            update.setString(1, event.id());
                            update.executeUpdate();
                        }
                        return null;
                    });
                    LOG.info("BROKER_PUBLISHED eventId=" + event.id() + " queue=TaskEventsQueue");
                }
            }
        } catch (RuntimeException error) {
            // Unsent or unmarked rows stay durable in the outbox; the next invocation retries.
            LOG.log(Level.WARNING, "BROKER_RETRY outbox retained; next dispatch in five seconds", error);
        }
    }

    private record PendingEvent(String id, String payload) { }
}
