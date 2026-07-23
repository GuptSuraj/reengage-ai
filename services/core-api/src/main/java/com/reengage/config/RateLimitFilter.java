package com.reengage.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;

@Component
public class RateLimitFilter extends OncePerRequestFilter {
    private final StringRedisTemplate redis;
    RateLimitFilter(StringRedisTemplate redis){this.redis=redis;}

    @Override
    protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain)
            throws ServletException,IOException {
        var path=request.getRequestURI();
        var limit=path.startsWith("/api/v1/auth/")?20:path.equals("/api/v1/events/batch")?3000:600;
        var authentication=SecurityContextHolder.getContext().getAuthentication();
        var client=authentication!=null&&authentication.isAuthenticated()
                ? "user:"+authentication.getPrincipal()
                : "ip:"+request.getRemoteAddr();
        var minute=Instant.now().getEpochSecond()/60;
        var key="rate:"+client+":"+path+":"+minute;
        try{
            var count=redis.opsForValue().increment(key);
            if(count!=null&&count==1) redis.expire(key,Duration.ofMinutes(2));
            response.setHeader("X-RateLimit-Limit",String.valueOf(limit));
            response.setHeader("X-RateLimit-Remaining",String.valueOf(Math.max(0,limit-(count==null?0:count))));
            if(count!=null&&count>limit){
                response.setStatus(429);response.setContentType("application/problem+json");
                response.getWriter().write("{\"title\":\"Too Many Requests\",\"status\":429,\"detail\":\"Rate limit exceeded\"}");
                return;
            }
        }catch(RuntimeException ignored){/* fail open; availability is preferred when Redis is degraded */}
        chain.doFilter(request,response);
    }
}
