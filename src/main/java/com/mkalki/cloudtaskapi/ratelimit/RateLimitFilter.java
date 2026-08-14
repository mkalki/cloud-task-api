package com.mkalki.cloudtaskapi.ratelimit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mkalki.cloudtaskapi.entity.User;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;

@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private final RateLimitBucketManager bucketManager;
    private final ObjectMapper objectMapper;

    public RateLimitFilter(RateLimitBucketManager bucketManager,
                           ObjectMapper objectMapper) {
        this.bucketManager = bucketManager;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {


        Authentication authentication =
                SecurityContextHolder.getContext().getAuthentication();

        if(authentication == null || !authentication.isAuthenticated()) {
            filterChain.doFilter(request, response);
            return;
        }

        if(!(authentication.getPrincipal() instanceof User user)) {
            filterChain.doFilter(request, response);
            return;
        }
        Long userId = user.getId();

        Bucket bucket = bucketManager.resolveBucket(String.valueOf(userId));

        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);

        if(probe.isConsumed()) {
            filterChain.doFilter(request, response);
            return;
        }
        long nanosToWait = probe.getNanosToWaitForRefill();
        long secondsToWait = (long) Math.ceil(nanosToWait / 1_000_000_000.0);

        response.setHeader("Retry-After", String.valueOf(secondsToWait));
        response.setContentType("application/json");
        Map<String,String> errorResponse = Map.of(
                "error", "Too many requests"
        );

        response.setStatus(429);
        objectMapper.writeValue(response.getWriter(), errorResponse);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {

        String path = request.getServletPath();

        return path.startsWith("/auth/")
                || path.startsWith("/swagger-ui/")
                || path.startsWith("/v3/api-docs/");
    }


}
