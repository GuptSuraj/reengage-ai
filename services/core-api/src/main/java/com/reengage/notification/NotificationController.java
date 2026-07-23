package com.reengage.notification;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {
    private final JdbcClient jdbc;
    NotificationController(JdbcClient jdbc){this.jdbc=jdbc;}

    @GetMapping
    public List<Map<String,Object>> list(Authentication auth){
        return jdbc.sql("""
                SELECT id,product_id,channel,status,intent_score,template_key,variant,message,
                scheduled_for,sent_at,opened_at,clicked_at,converted_at,retry_count,failure_reason
                FROM notification_job WHERE user_id=:u ORDER BY created_at DESC LIMIT 50
                """).param("u",(UUID)auth.getPrincipal()).query().listOfRows();
    }
    @PostMapping("/{id}/open")
    public void opened(@PathVariable UUID id,Authentication auth){
        update(id,(UUID)auth.getPrincipal(),"opened_at");
    }
    @PostMapping("/{id}/click")
    public void clicked(@PathVariable UUID id,Authentication auth){
        update(id,(UUID)auth.getPrincipal(),"clicked_at");
    }
    private void update(UUID id,UUID userId,String column){
        jdbc.sql("UPDATE notification_job SET "+column+"=COALESCE("+column+",now()),updated_at=now() WHERE id=:id AND user_id=:u")
                .param("id",id).param("u",userId).update();
    }
}
