package com.reengage.notification;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.*;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class NotificationScheduler {
    private final JdbcClient jdbc;
    private final StringRedisTemplate redis;
    private final Map<String,NotificationProvider> providers;
    private final int maxRetries;
    private final Counter sent;
    private final Counter cancelled;
    private final Counter failed;

    NotificationScheduler(JdbcClient jdbc,StringRedisTemplate redis,List<NotificationProvider> providers,
                          @Value("${app.notification.max-retries}") int maxRetries,MeterRegistry registry){
        this.jdbc=jdbc; this.redis=redis; this.maxRetries=maxRetries;
        this.providers=providers.stream().collect(Collectors.toMap(NotificationProvider::channel,Function.identity()));
        this.sent=registry.counter("reengage.notifications.sent");
        this.cancelled=registry.counter("reengage.notifications.cancelled");
        this.failed=registry.counter("reengage.notifications.failed");
    }

    @Scheduled(fixedDelayString="${app.notification.poll-ms:1000}")
    public void tick(){
        repairRedisQueue();
        var now=Instant.now();
        Set<String> due;
        try { due=redis.opsForZSet().rangeByScore(DecisionService.DUE_SET,0,now.toEpochMilli(),0,100); }
        catch(RuntimeException error){ due=Set.of(); }
        if(due==null || due.isEmpty()) {
            due=new LinkedHashSet<>(jdbc.sql("""
                    SELECT id::text FROM notification_job WHERE status='SCHEDULED' AND scheduled_for<=now()
                    ORDER BY scheduled_for LIMIT 100
                    """).query(String.class).list());
        }
        due.forEach(this::process);
    }

    private void process(String textId){
        UUID id;
        try{id=UUID.fromString(textId);}catch(IllegalArgumentException ignored){return;}
        var claimed=jdbc.sql("""
                UPDATE notification_job SET status='PROCESSING',claimed_at=now(),updated_at=now()
                WHERE id=:id AND status='SCHEDULED' AND scheduled_for<=now()
                RETURNING id,user_id,product_id,channel,message,retry_count,expires_at
                """).param("id",id).query(Job.class).optional();
        if(claimed.isEmpty()){ remove(id); return; }
        var job=claimed.get();
        var eligibility=eligibility(job);
        if(!eligibility.allowed()){
            if("rescheduled_quiet_hours".equals(eligibility.reason())) return;
            jdbc.sql("""
                    UPDATE notification_job SET status='CANCELLED',cancelled_at=now(),
                    failure_reason=:reason,updated_at=now() WHERE id=:id
                    """).param("reason",eligibility.reason()).param("id",id).update();
            cancelled.increment(); remove(id); return;
        }
        var provider=providers.get(job.channel());
        if(provider==null){ retry(job,"provider_not_configured"); return; }
        var started=System.nanoTime();
        var result=provider.send(new NotificationProvider.NotificationMessage(
                eligibility.recipient(),job.message(),job.id().toString()));
        var duration=(int)((System.nanoTime()-started)/1_000_000);
        var attempt=job.retryCount()+1;
        jdbc.sql("""
                INSERT INTO delivery_attempt(notification_id,attempt,provider,status,response_code,error_message,duration_ms)
                VALUES (:id,:attempt,:provider,:status,:code,:error,:duration)
                """).param("id",id).param("attempt",attempt).param("provider",job.channel()+"_MOCK")
                .param("status",result.accepted()?"ACCEPTED":"FAILED").param("code",result.responseCode())
                .param("error",result.error()).param("duration",duration).update();
        if(result.accepted()){
            jdbc.sql("""
                    UPDATE notification_job SET status='SENT',sent_at=now(),provider_message_id=:providerId,
                    retry_count=:attempt,failure_reason=NULL,updated_at=now() WHERE id=:id
                    """).param("providerId",result.providerMessageId()).param("attempt",attempt).param("id",id).update();
            sent.increment(); remove(id);
        } else retry(job,result.error());
    }

    private Eligibility eligibility(Job job){
        if(job.expiresAt().isBefore(Instant.now())) return new Eligibility(false,null,"expired");
        var row=jdbc.sql("""
                SELECT p.stock,p.active,u.email,u.phone_e164,pref.email_opt_in,pref.whatsapp_opt_in,
                pref.timezone,pref.quiet_start,pref.quiet_end,
                EXISTS(SELECT 1 FROM order_item i JOIN purchase_order o ON o.id=i.order_id
                  WHERE o.user_id=:u AND i.product_id=:p) purchased
                FROM product p CROSS JOIN app_user u JOIN user_preference pref ON pref.user_id=u.id
                WHERE p.id=:p AND u.id=:u
                """).param("p",job.productId()).param("u",job.userId()).query(EligibilityRow.class).optional();
        if(row.isEmpty()) return new Eligibility(false,null,"missing_context");
        var r=row.get();
        if(r.purchased()) return new Eligibility(false,null,"purchase_completed");
        if(!r.active() || r.stock()<1) return new Eligibility(false,null,"product_unavailable");
        if(("EMAIL".equals(job.channel())&&!r.emailOptIn()) ||
                ("WHATSAPP".equals(job.channel())&&!r.whatsappOptIn())) return new Eligibility(false,null,"opted_out");
        var local=ZonedDateTime.now(ZoneId.of(r.timezone()));
        var h=local.getHour();
        var quiet=r.quietStart()>r.quietEnd()?h>=r.quietStart()||h<r.quietEnd():h>=r.quietStart()&&h<r.quietEnd();
        if(quiet){
            var next=local.withHour(r.quietEnd()).withMinute(0).withSecond(0).withNano(0);
            if(!next.isAfter(local))next=next.plusDays(1);
            reschedule(job.id(),next.toInstant(),"quiet_hours");
            return new Eligibility(false,null,"rescheduled_quiet_hours");
        }
        var recipient="WHATSAPP".equals(job.channel())?r.phoneE164():r.email();
        if(recipient==null||recipient.isBlank()) return new Eligibility(false,null,"recipient_unavailable");
        return new Eligibility(true,recipient,null);
    }

    private void retry(Job job,String reason){
        var nextAttempt=job.retryCount()+1;
        if(nextAttempt>=maxRetries){
            jdbc.sql("UPDATE notification_job SET status='FAILED',retry_count=:n,failure_reason=:r,updated_at=now() WHERE id=:id")
                    .param("n",nextAttempt).param("r",reason).param("id",job.id()).update();
            failed.increment(); remove(job.id()); return;
        }
        var delay=Duration.ofSeconds((long)Math.pow(2,nextAttempt)*30L+new Random().nextInt(15));
        reschedule(job.id(),Instant.now().plus(delay),reason);
        jdbc.sql("UPDATE notification_job SET retry_count=:n WHERE id=:id")
                .param("n",nextAttempt).param("id",job.id()).update();
    }

    private void reschedule(UUID id,Instant when,String reason){
        jdbc.sql("""
                UPDATE notification_job SET status='SCHEDULED',scheduled_for=:when,
                failure_reason=:reason,updated_at=now() WHERE id=:id
                """).param("when",when).param("reason",reason).param("id",id).update();
        try{redis.opsForZSet().add(DecisionService.DUE_SET,id.toString(),when.toEpochMilli());}catch(RuntimeException ignored){}
    }
    private void repairRedisQueue(){
        try{
            var jobs=jdbc.sql("""
                    SELECT id::text,extract(epoch from scheduled_for)*1000 score FROM notification_job
                    WHERE status='SCHEDULED' AND scheduled_for<now()+interval '1 hour' LIMIT 500
                    """).query(QueueRow.class).list();
            jobs.forEach(j->redis.opsForZSet().add(DecisionService.DUE_SET,j.id(),j.score()));
        }catch(RuntimeException ignored){}
    }
    private void remove(UUID id){try{redis.opsForZSet().remove(DecisionService.DUE_SET,id.toString());}catch(RuntimeException ignored){}}

    record Job(UUID id,UUID userId,String productId,String channel,String message,int retryCount,Instant expiresAt){}
    record Eligibility(boolean allowed,String recipient,String reason){}
    record EligibilityRow(int stock,boolean active,String email,String phoneE164,boolean emailOptIn,boolean whatsappOptIn,
                          String timezone,int quietStart,int quietEnd,boolean purchased){}
    record QueueRow(String id,double score){}
}
