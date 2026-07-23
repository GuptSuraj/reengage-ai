package com.reengage.profile;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import com.reengage.notification.DecisionService;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class ProfileDecisionConsumer {
    private final JdbcClient jdbc;
    private final ObjectMapper mapper;
    private final AiClient ai;
    private final DecisionService decision;
    private final Timer processingTimer;

    ProfileDecisionConsumer(JdbcClient jdbc,ObjectMapper mapper,AiClient ai,
                            DecisionService decision,MeterRegistry registry) {
        this.jdbc=jdbc; this.mapper=mapper; this.ai=ai; this.decision=decision;
        this.processingTimer=registry.timer("reengage.event.processing");
    }

    @KafkaListener(topics = "user-behaviour-events")
    public void consume(String payload) {
        processingTimer.record(() -> {
            try {
                Map<String,Object> message=mapper.readValue(payload,new TypeReference<>() {});
                var userText=Objects.toString(message.get("userId"),"");
                if (userText.isBlank()) return;
                var userId=UUID.fromString(userText);
                var evaluation=ai.evaluate(buildRequest(userId));
                jdbc.sql("""
                        INSERT INTO user_profile(user_id,profile,intent_score,intent_level,intent_signals,recommendations,model_version)
                        VALUES (:u,CAST(:profile AS jsonb),:score,:level,CAST(:signals AS jsonb),CAST(:recommendations AS jsonb),:version)
                        ON CONFLICT(user_id) DO UPDATE SET profile=excluded.profile,intent_score=excluded.intent_score,
                        intent_level=excluded.intent_level,intent_signals=excluded.intent_signals,
                        recommendations=excluded.recommendations,
                        model_version=excluded.model_version,updated_at=now()
                        """).param("u",userId).param("profile",mapper.writeValueAsString(evaluation.profile()))
                        .param("score",evaluation.intentScore()).param("level",evaluation.intentLevel())
                        .param("signals",mapper.writeValueAsString(evaluation.signals()))
                        .param("recommendations",mapper.writeValueAsString(evaluation.recommendations()))
                        .param("version",evaluation.modelVersion()).update();
                decision.evaluate(userId,Objects.toString(message.get("eventType"),""),evaluation);
            } catch (Exception error) {
                throw new IllegalStateException("Profile/decision processing failed",error);
            }
        });
    }

    private Map<String,Object> buildRequest(UUID userId) throws Exception {
        var events=jdbc.sql("""
                SELECT event_id "eventId",event_type "eventType",product_id "productId",
                occurred_at "timestamp",metadata::text FROM behaviour_event
                WHERE user_id=:u AND occurred_at>now()-interval '90 days'
                ORDER BY occurred_at
                """).param("u",userId).query().listOfRows();
        var normalizedEvents=new ArrayList<Map<String,Object>>();
        for(var event:events){
            var copy=new HashMap<>(event);
            copy.put("metadata",mapper.readValue(Objects.toString(event.get("metadata"),"{}"),Map.class));
            normalizedEvents.add(copy);
        }
        var products=jdbc.sql("""
                SELECT id,name,brand,category,description,price_inr "priceInr",rating,stock,
                quality_score "qualityScore",features::text FROM product WHERE active=true
                """).query().listOfRows();
        var normalizedProducts=new ArrayList<Map<String,Object>>();
        for(var product:products){
            var copy=new HashMap<>(product);
            copy.put("features",mapper.readValue(Objects.toString(product.get("features"),"[]"),List.class));
            normalizedProducts.add(copy);
        }
        var purchased=jdbc.sql("""
                SELECT DISTINCT i.product_id FROM order_item i JOIN purchase_order o ON o.id=i.order_id
                WHERE o.user_id=:u
                """).param("u",userId).query(String.class).list();
        var blockedText=jdbc.sql("SELECT blocked_categories::text FROM user_preference WHERE user_id=:u")
                .param("u",userId).query(String.class).optional().orElse("[]");
        return Map.of("userId",userId.toString(),"events",normalizedEvents,"products",normalizedProducts,
                "purchasedProductIds",purchased,"blockedCategories",mapper.readValue(blockedText,List.class),"limit",3);
    }
}
