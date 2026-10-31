package nz.ac.aut.comp713.consumer;

import com.sun.messaging.ConnectionFactory;
import jakarta.jms.Connection;
import jakarta.jms.DeliveryMode;
import jakarta.jms.JMSException;
import jakarta.jms.Message;
import jakarta.jms.Session;
import jakarta.jms.TextMessage;

import java.nio.file.Path;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/** Run as an independent Java process; receives broker messages, never backend SQL. */
public final class NotificationConsumer {
    private NotificationConsumer() { }

    public static void main(String[] arguments) throws Exception {
        Options options = Options.parse(arguments);
        ConsumerStore store = new ConsumerStore(Path.of(options.value("data", "data/notifications")));
        if (options.flag("status")) {
            store.status().forEach(System.out::println);
            return;
        }
        ConnectionFactory factory = new ConnectionFactory();
        factory.setProperty("imqAddressList", options.value("broker", environment("COMP713_BROKER", "localhost:7676")));
        factory.setProperty("imqReconnectEnabled", "false");
        String user = options.value("user", environment("COMP713_BROKER_USER", "guest"));
        String password = environment("COMP713_BROKER_PASSWORD", "guest");
        String queue = options.value("queue", "TaskEventsQueue");
        String selectedEvent = options.value("event-id", "");
        String selector = selectedEvent.isBlank() ? null : "eventId = '" + selectedEvent.replace("'", "''") + "'";
        if (options.flag("replay-last")) {
            String[] event = store.lastMessage();
            try (var context = factory.createContext(user, password)) {
                context.createProducer().setDeliveryMode(DeliveryMode.PERSISTENT)
                        .setProperty("eventId", event[0]).setProperty("eventType", "task.submitted")
                        .send(context.createQueue(queue), event[1]);
            }
            log("DUPLICATE_REPLAYED eventId=" + event[0] + " queue=" + queue);
            return;
        }
        int limit = options.nonNegative("limit", 0);
        int idleSeconds = options.nonNegative("idle-seconds", 0);
        int accepted = 0;
        long lastActivity = System.nanoTime();
        log("CONSUMER_STARTED queue=" + queue + " broker=" + options.value("broker", environment("COMP713_BROKER", "localhost:7676"))
                + " data=" + Path.of(options.value("data", "data/notifications")).toAbsolutePath());
        while ((limit == 0 || accepted < limit) && !idleExpired(lastActivity, idleSeconds)) {
            try (Connection connection = factory.createConnection(user, password);
                 Session session = connection.createSession(true, Session.SESSION_TRANSACTED);
                 var receiver = session.createConsumer(session.createQueue(queue), selector)) {
                connection.start();
                log("BROKER_CONNECTED acknowledgement=SESSION_TRANSACTED");
                while ((limit == 0 || accepted < limit) && !idleExpired(lastActivity, idleSeconds)) {
                    Message message = receiver.receive(1000);
                    if (message == null) continue;
                    lastActivity = System.nanoTime();
                    String eventId = message.getStringProperty("eventId");
                    if (eventId == null) eventId = message.getJMSMessageID();
                    int deliveryCount = message.propertyExists("JMSXDeliveryCount") ? message.getIntProperty("JMSXDeliveryCount") : 1;
                    try {
                        int attempts = store.observed(eventId, message.getJMSRedelivered());
                        log("RECEIVED eventId=" + eventId + " redelivered=" + message.getJMSRedelivered()
                                + " brokerDeliveryCount=" + deliveryCount + " persistedAttempts=" + attempts);
                        if (!(message instanceof TextMessage text)) throw new IllegalArgumentException("Expected a JSON TextMessage.");
                        TaskSubmitted event = TaskSubmitted.parse(text.getText(), eventId);
                        if (options.flag("fail-once") && attempts == 1) {
                            throw new IllegalStateException("Injected processing failure before database commit.");
                        }
                        boolean created = store.save(event);
                        if (options.flag("fail-after-store") && attempts == 1) {
                            throw new IllegalStateException("Injected crash window after database commit, before broker acknowledgement.");
                        }
                        session.commit(); // Broker acknowledgement occurs only after durable business result.
                        accepted++;
                        log((created ? "NOTIFICATION_CREATED " : "DUPLICATE_SUPPRESSED ") + "eventId=" + eventId + " " + event.notification());
                        log("ACK_COMMIT eventId=" + eventId + " notificationsCreated=" + (created ? 1 : 0));
                    } catch (Exception processingError) {
                        if (deliveryCount >= 5) {
                            try (var sender = session.createProducer(session.createQueue(queue + "DeadLetter"))) {
                                sender.setDeliveryMode(DeliveryMode.PERSISTENT);
                                TextMessage deadLetter = session.createTextMessage(message instanceof TextMessage text ? text.getText() : "Unsupported message type");
                                deadLetter.setStringProperty("eventId", eventId);
                                deadLetter.setStringProperty("failure", processingError.getMessage());
                                sender.send(deadLetter);
                            }
                            store.outcome(eventId, "DEAD_LETTER");
                            session.commit();
                            accepted++;
                            log("DEAD_LETTER eventId=" + eventId + " queue=" + queue + "DeadLetter");
                        } else {
                            store.outcome(eventId, "RETRY_ROLLBACK");
                            session.rollback();
                            log("RETRY_ROLLBACK eventId=" + eventId + " reason=" + processingError.getMessage());
                            Thread.sleep(300);
                        }
                    }
                }
            } catch (JMSException brokerError) {
                log("BROKER_UNAVAILABLE retryInSeconds=2 reason=" + brokerError.getMessage());
                Thread.sleep(2000);
            }
        }
        log("CONSUMER_STOPPED acceptedDeliveries=" + accepted);
        store.status().forEach(System.out::println);
    }

    private static boolean idleExpired(long lastActivity, int idleSeconds) {
        return idleSeconds > 0 && System.nanoTime() - lastActivity >= idleSeconds * 1_000_000_000L;
    }

    private static String environment(String key, String fallback) {
        String value = System.getenv(key);
        return value == null || value.isBlank() ? fallback : value;
    }

    private static void log(String message) { System.out.println(Instant.now() + " " + message); }

    private record Options(Map<String, String> values) {
        static Options parse(String[] arguments) {
            Map<String, String> values = new HashMap<>();
            for (int i = 0; i < arguments.length; i++) {
                String argument = arguments[i];
                if (!argument.startsWith("--")) throw new IllegalArgumentException("Expected --option, got " + argument);
                String key = argument.substring(2);
                switch (key) {
                    case "status", "replay-last", "fail-once", "fail-after-store" -> values.put(key, "true");
                    case "data", "broker", "queue", "user", "event-id", "limit", "idle-seconds" -> {
                        if (++i == arguments.length) throw new IllegalArgumentException("Missing value for " + argument);
                        values.put(key, arguments[i]);
                    }
                    default -> throw new IllegalArgumentException("Unknown option " + argument);
                }
            }
            return new Options(values);
        }

        String value(String key, String fallback) { return values.getOrDefault(key, fallback); }
        boolean flag(String key) { return values.containsKey(key); }
        int nonNegative(String key, int fallback) {
            int value = Integer.parseInt(value(key, Integer.toString(fallback)));
            if (value < 0) throw new IllegalArgumentException("--" + key + " must be non-negative.");
            return value;
        }
    }
}
