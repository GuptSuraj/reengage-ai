package com.reengage.events;

import tools.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@Validated
@RestController
@RequestMapping("/api/v1/events")
public class EventController {
    private static final Set<String> TYPES = Set.of("PAGE_VIEWED","PRODUCT_VIEWED","SEARCH_PERFORMED",
            "FILTER_APPLIED","PRODUCT_COMPARED","ADD_TO_CART","REMOVE_FROM_CART","CHECKOUT_STARTED",
            "PURCHASE_COMPLETED","TIME_SPENT","SESSION_STARTED","SESSION_ENDED","NOTIFICATION_CLICKED");
    private final JdbcClient jdbc;
    private final ObjectMapper mapper;
    private final Counter accepted;
    private final Counter duplicates;

    EventController(JdbcClient jdbc, ObjectMapper mapper, MeterRegistry registry) {
        this.jdbc = jdbc; this.mapper = mapper;
        this.accepted = registry.counter("reengage.events.accepted");
        this.duplicates = registry.counter("reengage.events.duplicates");
    }

    @PostMapping("/batch")
    @ResponseStatus(org.springframework.http.HttpStatus.ACCEPTED)
    @Transactional
    public BatchResponse batch(@Valid @RequestBody BatchRequest request, Authentication auth) throws Exception {
        var acceptedCount = 0;
        var duplicateCount = 0;
        var rejected = new ArrayList<Rejected>();
        UUID authenticatedId = auth != null && auth.getPrincipal() instanceof UUID id ? id : null;
        for (var incoming : request.events()) {
            var event = authenticatedId == null ? incoming : new BehaviourEvent(incoming.eventId(),
                    authenticatedId, incoming.anonymousId(), incoming.sessionId(), incoming.eventType(),
                    incoming.productId(), incoming.timestamp(), incoming.sourcePage(), incoming.device(),
                    incoming.metadata(), incoming.schemaVersion());
            var type = event.eventType().toUpperCase(Locale.ROOT);
            if (!TYPES.contains(type) || (event.userId() == null && isBlank(event.anonymousId()))) {
                rejected.add(new Rejected(event.eventId(), "invalid event type or identity"));
                continue;
            }
            {
                var payload = mapper.writeValueAsString(Map.ofEntries(
                        Map.entry("eventId", event.eventId()), Map.entry("userId", Objects.toString(event.userId(), "")),
                        Map.entry("anonymousId", Objects.toString(event.anonymousId(), "")),
                        Map.entry("sessionId", event.sessionId()), Map.entry("eventType", type),
                        Map.entry("productId", Objects.toString(event.productId(), "")),
                        Map.entry("timestamp", event.timestamp()), Map.entry("sourcePage", Objects.toString(event.sourcePage(), "")),
                        Map.entry("device", nullSafe(event.device())), Map.entry("metadata", nullSafe(event.metadata()))));
                var inserted=jdbc.sql("""
                        INSERT INTO behaviour_event(event_id,user_id,anonymous_id,session_id,event_type,product_id,
                          occurred_at,source_page,device,metadata,schema_version)
                        VALUES (:eventId,:userId,:anonymousId,:sessionId,:type,:productId,:occurredAt,:source,
                          CAST(:device AS jsonb),CAST(:metadata AS jsonb),:version)
                        ON CONFLICT(event_id) DO NOTHING
                        """).param("eventId", event.eventId()).param("userId", event.userId())
                        .param("anonymousId", event.anonymousId()).param("sessionId", event.sessionId())
                        .param("type", type).param("productId", blankToNull(event.productId()))
                        .param("occurredAt", event.timestamp()).param("source", event.sourcePage())
                        .param("device", mapper.writeValueAsString(nullSafe(event.device())))
                        .param("metadata", mapper.writeValueAsString(nullSafe(event.metadata())))
                        .param("version", event.schemaVersion() == null ? 1 : event.schemaVersion()).update();
                if(inserted==0){ duplicateCount++; continue; }
                jdbc.sql("""
                        INSERT INTO outbox_event(aggregate_type,aggregate_id,topic,event_key,payload)
                        VALUES ('BEHAVIOUR_EVENT',:eventId,'user-behaviour-events',:eventKey,CAST(:payload AS jsonb))
                        """).param("eventId", event.eventId().toString())
                        .param("eventKey", event.userId() != null ? event.userId().toString() : event.anonymousId())
                        .param("payload", payload).update();
                acceptedCount++;
            }
        }
        accepted.increment(acceptedCount); duplicates.increment(duplicateCount);
        return new BatchResponse(acceptedCount, duplicateCount, rejected);
    }

    private static boolean isBlank(String value) { return value == null || value.isBlank(); }
    private static String blankToNull(String value) { return isBlank(value) ? null : value; }
    private static Map<String,Object> nullSafe(Map<String,Object> map) { return map == null ? Map.of() : map; }
    public record BatchRequest(@Size(min=1,max=100) List<@Valid BehaviourEvent> events) {}
    public record Rejected(UUID eventId,String reason) {}
    public record BatchResponse(int accepted,int duplicates,List<Rejected> rejected) {}
}
