package com.reengage.events;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Component
public class OutboxPublisher {
    private final JdbcClient jdbc;
    private final KafkaTemplate<String,String> kafka;
    private final String instanceId=UUID.randomUUID().toString();
    OutboxPublisher(JdbcClient jdbc, KafkaTemplate<String,String> kafka) { this.jdbc=jdbc; this.kafka=kafka; }

    @Scheduled(fixedDelayString = "${app.outbox-interval-ms:500}")
    public void publish() {
        for (var event : claim()) {
            try {
                kafka.send(event.topic(), event.eventKey(), event.payload()).get();
                markPublished(event.id());
            } catch (Exception error) {
                markFailed(event.id(), error.getMessage());
            }
        }
    }

    @Transactional
    protected List<OutboxRow> claim() {
        return jdbc.sql("""
                UPDATE outbox_event SET locked_at=now(),locked_by=:instance
                WHERE id IN (
                  SELECT id FROM outbox_event WHERE published_at IS NULL AND attempts<10
                  AND (locked_at IS NULL OR locked_at<now()-interval '2 minutes')
                  ORDER BY created_at LIMIT 100 FOR UPDATE SKIP LOCKED
                )
                RETURNING id,topic,event_key,payload::text
                """).param("instance",instanceId).query(OutboxRow.class).list();
    }

    private void markPublished(UUID id) {
        jdbc.sql("UPDATE outbox_event SET published_at=now(),attempts=attempts+1,locked_at=NULL,locked_by=NULL WHERE id=:id")
                .param("id", id).update();
    }
    private void markFailed(UUID id,String error) {
        jdbc.sql("UPDATE outbox_event SET attempts=attempts+1,last_error=:error,locked_at=NULL,locked_by=NULL WHERE id=:id")
                .param("id", id).param("error", error == null ? "unknown" : error.substring(0, Math.min(500,error.length()))).update();
    }
    record OutboxRow(UUID id,String topic,String eventKey,String payload) {}
}
