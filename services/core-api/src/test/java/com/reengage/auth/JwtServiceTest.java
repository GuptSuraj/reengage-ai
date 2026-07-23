package com.reengage.auth;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {
    @Test
    void issuesAndVerifiesSignedToken(){
        var service=new JwtService("a-secure-test-secret-that-is-longer-than-32-bytes",Duration.ofMinutes(5));
        var id=UUID.randomUUID();
        var token=service.issue(id,"user@example.com","CUSTOMER");
        var claims=service.parse(token);
        assertThat(claims.getSubject()).isEqualTo(id.toString());
        assertThat(claims.get("role",String.class)).isEqualTo("CUSTOMER");
    }

    @Test
    void rejectsShortSecret(){
        assertThatThrownBy(()->new JwtService("short",Duration.ofMinutes(5)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
