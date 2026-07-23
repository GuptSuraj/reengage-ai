package com.reengage.events;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record BehaviourEvent(
        @NotNull UUID eventId,
        UUID userId,
        String anonymousId,
        @NotNull UUID sessionId,
        @NotBlank String eventType,
        String productId,
        @NotNull Instant timestamp,
        String sourcePage,
        Map<String,Object> device,
        Map<String,Object> metadata,
        Integer schemaVersion
) {}
