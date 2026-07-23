package com.reengage.notification;

import tools.jackson.databind.ObjectMapper;
import com.reengage.profile.AiClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.util.*;

@Service
public class DecisionService {
    public static final String DUE_SET="reengage:notifications:due";
    private final JdbcClient jdbc;
    private final ObjectMapper mapper;
    private final StringRedisTemplate redis;
    private final Duration delay;

    DecisionService(JdbcClient jdbc,ObjectMapper mapper,StringRedisTemplate redis,
                    @Value("${app.notification.demo-delay}") Duration delay) {
        this.jdbc=jdbc; this.mapper=mapper; this.redis=redis; this.delay=delay;
    }

    @Transactional
    public Optional<UUID> evaluate(UUID userId,String trigger,AiClient.Evaluation evaluation) throws Exception {
        if (!"HIGH".equals(evaluation.intentLevel()) || evaluation.anchorProductId()==null
                || !Set.of("ADD_TO_CART","CHECKOUT_STARTED","PRODUCT_COMPARED","TIME_SPENT").contains(trigger)) {
            return Optional.empty();
        }
        var context=jdbc.sql("""
                SELECT p.name,p.price_inr,p.stock,p.active,u.email,u.phone_e164,
                pref.preferred_channel,pref.email_opt_in,pref.whatsapp_opt_in,pref.timezone,
                pref.quiet_start,pref.quiet_end
                FROM product p CROSS JOIN app_user u JOIN user_preference pref ON pref.user_id=u.id
                WHERE p.id=:p AND u.id=:u
                """).param("p",evaluation.anchorProductId()).param("u",userId)
                .query(DecisionContext.class).optional();
        if(context.isEmpty() || !context.get().active() || context.get().stock()<1) return Optional.empty();
        var c=context.get();
        var channel=chooseChannel(c);
        if(channel==null || weeklyCount(userId)>=3) return Optional.empty();
        var recommendationIds=evaluation.recommendations().stream().map(AiClient.Recommendation::productId).toList();
        var alternatives=recommendationIds.isEmpty() ? List.<String>of() :
                jdbc.sql("SELECT name FROM product WHERE id IN (:ids) ORDER BY rating DESC")
                        .param("ids",recommendationIds).query(String.class).list();
        var message="You were checking "+c.name()+". It is still available for ₹"+
                String.format("%,d",c.priceInr())+"."+
                (alternatives.isEmpty() ? "" : " You may also like "+String.join(" and ",alternatives)+".");
        var scheduled=respectQuietHours(Instant.now().plus(delay),c);
        var id=UUID.randomUUID();
        var inserted=jdbc.sql("""
                    INSERT INTO notification_job(id,user_id,product_id,channel,status,intent_score,
                    template_key,variant,message,recommendations,scheduled_for,expires_at)
                    VALUES (:id,:u,:p,:channel,'SCHEDULED',:score,'high-intent-abandonment-v1',
                    :variant,:message,CAST(:recommendations AS jsonb),:scheduled,:expires)
                    ON CONFLICT DO NOTHING
                    """).param("id",id).param("u",userId).param("p",evaluation.anchorProductId())
                    .param("channel",channel).param("score",evaluation.intentScore())
                    .param("variant",Math.abs(userId.hashCode())%2==0 ? "A":"B").param("message",message)
                    .param("recommendations",mapper.writeValueAsString(evaluation.recommendations()))
                    .param("scheduled",scheduled).param("expires",scheduled.plus(Duration.ofHours(48))).update();
        if(inserted==0) return Optional.empty();
        try { redis.opsForZSet().add(DUE_SET,id.toString(),scheduled.toEpochMilli()); }
        catch (RuntimeException ignored) { /* canonical PostgreSQL repair sweep will enqueue it */ }
        return Optional.of(id);
    }

    private String chooseChannel(DecisionContext c) {
        if("WHATSAPP".equals(c.preferredChannel()) && c.whatsappOptIn() && c.phoneE164()!=null) return "WHATSAPP";
        if("EMAIL".equals(c.preferredChannel()) && c.emailOptIn()) return "EMAIL";
        if(c.whatsappOptIn() && c.phoneE164()!=null) return "WHATSAPP";
        if(c.emailOptIn()) return "EMAIL";
        return null;
    }
    private int weeklyCount(UUID userId) {
        return jdbc.sql("SELECT count(*) FROM notification_job WHERE user_id=:u AND created_at>now()-interval '7 days'")
                .param("u",userId).query(Integer.class).single();
    }
    private Instant respectQuietHours(Instant candidate,DecisionContext c) {
        var zone=ZoneId.of(c.timezone());
        var local=candidate.atZone(zone);
        var hour=local.getHour();
        var quiet=c.quietStart()>c.quietEnd()
                ? hour>=c.quietStart() || hour<c.quietEnd()
                : hour>=c.quietStart() && hour<c.quietEnd();
        if(!quiet) return candidate;
        var next=local.withHour(c.quietEnd()).withMinute(0).withSecond(0).withNano(0);
        if(!next.isAfter(local)) next=next.plusDays(1);
        return next.toInstant();
    }
    record DecisionContext(String name,int priceInr,int stock,boolean active,String email,String phoneE164,
                           String preferredChannel,boolean emailOptIn,boolean whatsappOptIn,
                           String timezone,int quietStart,int quietEnd) {}
}
