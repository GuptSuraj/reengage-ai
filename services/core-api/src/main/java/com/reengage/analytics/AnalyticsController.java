package com.reengage.analytics;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class AnalyticsController {
    private final JdbcClient jdbc;
    AnalyticsController(JdbcClient jdbc){this.jdbc=jdbc;}

    @GetMapping("/profile")
    public Map<String,Object> profile(Authentication auth){
        var rows=jdbc.sql("""
                SELECT profile,intent_score,intent_level,intent_signals,recommendations,model_version,updated_at
                FROM user_profile WHERE user_id=:u
                """).param("u",(UUID)auth.getPrincipal()).query().listOfRows();
        return rows.isEmpty()?Map.of("profile",Map.of(),"intent_score",0,"intent_level","LOW",
                "intent_signals",List.of(),"model_version","rules-hybrid-v1"):rows.getFirst();
    }

    @GetMapping("/admin/analytics")
    @PreAuthorize("hasRole('ADMIN')")
    public Map<String,Object> analytics(){
        var metrics=jdbc.sql("""
                SELECT
                 (SELECT count(*) FROM behaviour_event) total_events,
                 (SELECT count(DISTINCT user_id) FROM behaviour_event WHERE received_at>now()-interval '24 hours') active_users,
                 (SELECT count(*) FROM user_profile WHERE intent_level='HIGH') high_intent_users,
                 (SELECT count(DISTINCT c.user_id) FROM cart_item c) abandoned_carts,
                 (SELECT count(*) FROM notification_job WHERE status='SCHEDULED') scheduled_notifications,
                 (SELECT count(*) FROM notification_job WHERE status='SENT') delivered_notifications,
                 (SELECT count(*) FROM notification_job WHERE converted_at IS NOT NULL) recovered_carts,
                 (SELECT count(*) FROM outbox_event WHERE published_at IS NULL) outbox_lag,
                 (SELECT count(*) FROM dead_letter) failed_events,
                 (SELECT count(*) FROM delivery_attempt WHERE status='FAILED') delivery_failures
                """).query().singleRow();
        var activity=jdbc.sql("""
                SELECT event_id,user_id,event_type,product_id,occurred_at,received_at
                FROM behaviour_event ORDER BY received_at DESC LIMIT 20
                """).query().listOfRows();
        var notifications=jdbc.sql("""
                SELECT n.id,n.channel,n.status,n.intent_score,n.variant,n.scheduled_for,n.sent_at,
                n.retry_count,n.failure_reason,p.name product_name,u.email
                FROM notification_job n JOIN product p ON p.id=n.product_id JOIN app_user u ON u.id=n.user_id
                ORDER BY n.created_at DESC LIMIT 20
                """).query().listOfRows();
        var series=jdbc.sql("""
                SELECT date_trunc('hour',received_at) bucket,count(*) value
                FROM behaviour_event WHERE received_at>now()-interval '24 hours'
                GROUP BY bucket ORDER BY bucket
                """).query().listOfRows();
        var variants=jdbc.sql("""
                SELECT variant,count(*) FILTER(WHERE status='SENT') sent,
                count(*) FILTER(WHERE clicked_at IS NOT NULL) clicks,
                count(*) FILTER(WHERE converted_at IS NOT NULL) conversions
                FROM notification_job GROUP BY variant ORDER BY variant
                """).query().listOfRows();
        return Map.of("metrics",metrics,"activity",activity,"notifications",notifications,
                "eventSeries",series,"variants",variants);
    }

}
