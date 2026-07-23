package com.reengage.notification;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/preferences")
public class PreferenceController {
    private final JdbcClient jdbc;
    PreferenceController(JdbcClient jdbc){this.jdbc=jdbc;}

    @GetMapping
    public Map<String,Object> get(Authentication auth){
        return jdbc.sql("""
                SELECT pref.email_opt_in,pref.whatsapp_opt_in,pref.preferred_channel,pref.timezone,
                pref.quiet_start,pref.quiet_end,pref.blocked_categories,u.phone_e164
                FROM user_preference pref JOIN app_user u ON u.id=pref.user_id WHERE pref.user_id=:u
                """).param("u",id(auth)).query().singleRow();
    }
    @PutMapping
    public Map<String,Object> update(@Valid @RequestBody PreferenceRequest r,Authentication auth){
        var existingPhone=jdbc.sql("SELECT phone_e164 FROM app_user WHERE id=:u")
                .param("u",id(auth)).query(String.class).optional().orElse(null);
        var phone=r.phoneE164()==null||r.phoneE164().isBlank()?existingPhone:r.phoneE164();
        if(r.whatsappOptIn()&&phone==null) throw new IllegalArgumentException("A phone number is required for WhatsApp consent");
        if(r.phoneE164()!=null&&!r.phoneE164().isBlank()){
            jdbc.sql("UPDATE app_user SET phone_e164=:phone,updated_at=now() WHERE id=:u")
                    .param("phone",r.phoneE164()).param("u",id(auth)).update();
        }
        jdbc.sql("""
                UPDATE user_preference SET email_opt_in=:email,whatsapp_opt_in=:wa,
                preferred_channel=:channel,timezone=:timezone,quiet_start=:start,
                quiet_end=:end,updated_at=now() WHERE user_id=:u
                """).param("email",r.emailOptIn()).param("wa",r.whatsappOptIn())
                .param("channel",r.preferredChannel()).param("timezone",r.timezone())
                .param("start",r.quietStart()).param("end",r.quietEnd()).param("u",id(auth)).update();
        if(!r.emailOptIn()&&!r.whatsappOptIn()){
            jdbc.sql("""
                    UPDATE notification_job SET status='CANCELLED',cancelled_at=now(),
                    failure_reason='user_opted_out',updated_at=now()
                    WHERE user_id=:u AND status='SCHEDULED'
                    """).param("u",id(auth)).update();
        }
        return get(auth);
    }
    private UUID id(Authentication auth){return (UUID)auth.getPrincipal();}
    public record PreferenceRequest(boolean emailOptIn,boolean whatsappOptIn,
        @Pattern(regexp="EMAIL|WHATSAPP") String preferredChannel,String timezone,
        @Min(0)@Max(23) int quietStart,@Min(0)@Max(23) int quietEnd,
        @Pattern(regexp="^\\+[1-9]\\d{7,14}$") String phoneE164){}
}
