package com.reengage.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class BootstrapData implements ApplicationRunner {
    private final JdbcClient jdbc;
    private final PasswordEncoder encoder;

    BootstrapData(JdbcClient jdbc, PasswordEncoder encoder) {
        this.jdbc = jdbc;
        this.encoder = encoder;
    }

    @Override public void run(ApplicationArguments args) {
        create("demo@reengage.ai", "+919999999901", "Demo User", "CUSTOMER", "Demo@12345");
        create("admin@reengage.ai", "+919999999902", "Platform Admin", "ADMIN", "Admin@12345");
    }

    private void create(String email, String phone, String name, String role, String password) {
        if (jdbc.sql("SELECT count(*) FROM app_user WHERE email=:email").param("email", email)
                .query(Integer.class).single() > 0) return;
        var id = UUID.randomUUID();
        jdbc.sql("""
                INSERT INTO app_user(id,email,phone_e164,password_hash,display_name,role)
                VALUES (:id,:email,:phone,:password,:name,:role)
                """).param("id", id).param("email", email).param("phone",phone).param("password", encoder.encode(password))
                .param("name", name).param("role", role).update();
        jdbc.sql("""
                INSERT INTO user_preference(user_id,email_opt_in,whatsapp_opt_in,preferred_channel)
                VALUES (:id,true,true,'WHATSAPP')
                """).param("id", id).update();
    }
}
