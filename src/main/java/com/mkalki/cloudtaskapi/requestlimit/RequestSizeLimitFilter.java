package com.mkalki.cloudtaskapi.requestlimit;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

/**
 * Rejects request bodies that are larger than the configured limit with 413, before
 * Jackson parses them. Without this, one large body would be read fully into memory
 * before the job payload size check runs.
 *
 * <p>Two checks: a declared Content-Length above the limit is rejected without reading
 * anything. Otherwise (including chunked bodies with no Content-Length) the filter reads
 * at most limit + 1 bytes; one byte too many means the body is too large. An accepted
 * body is at most the limit, so keeping it in memory for the controller is cheap.
 *
 * <p>Runs before Spring Security, so an oversized body is refused even without a token.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class RequestSizeLimitFilter extends OncePerRequestFilter {

    private final List<RequestSizeLimitProperties.Rule> rules;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    public RequestSizeLimitFilter(RequestSizeLimitProperties properties) {
        this.rules = properties.rules();
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        Optional<RequestSizeLimitProperties.Rule> rule = findRule(request);
        if (rule.isEmpty()) {
            filterChain.doFilter(request, response);
            return;
        }

        // Capped at Integer.MAX_VALUE - 1 so that limit + 1 below cannot overflow.
        int limit = (int) Math.min(rule.get().maxSize().toBytes(), Integer.MAX_VALUE - 1);

        if (request.getContentLengthLong() > limit) {
            reject(response, limit);
            return;
        }

        byte[] body = request.getInputStream().readNBytes(limit + 1);
        if (body.length > limit) {
            reject(response, limit);
            return;
        }

        filterChain.doFilter(new CachedBodyRequest(request, body), response);
    }

    private Optional<RequestSizeLimitProperties.Rule> findRule(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return rules.stream()
                .filter(rule -> rule.method() == null || rule.method().equalsIgnoreCase(request.getMethod()))
                .filter(rule -> pathMatcher.match(rule.path(), path))
                .findFirst();
    }

    private void reject(HttpServletResponse response, int limit) throws IOException {
        response.setStatus(HttpStatus.PAYLOAD_TOO_LARGE.value());
        response.setContentType("text/plain;charset=UTF-8");
        response.getOutputStream().write(
                ("Request body exceeds " + limit + " bytes").getBytes(StandardCharsets.UTF_8));
    }
}
