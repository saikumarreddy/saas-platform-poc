package com.saasplatform.filter;

import com.saasplatform.context.RequestContext;
import com.saasplatform.context.RequestContextHolder;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.slf4j.MDC;

import java.io.IOException;
import java.util.UUID;

@Slf4j
@Component
public class TenantAwareFilter implements Filter {
    @Value("${jwt.secret:test-secret-key-that-is-long-enough}")
    private String jwtSecret;

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        String tenantId = null;
        String userId = null;
        String correlationId = httpRequest.getHeader("X-Correlation-ID");
        if (correlationId == null) {
            correlationId = UUID.randomUUID().toString();
        }
        String traceId = httpRequest.getHeader("X-Trace-ID");
        if (traceId == null) {
            traceId = UUID.randomUUID().toString();
        }

        try {
            String authHeader = httpRequest.getHeader("Authorization");
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                String token = authHeader.substring(7);
                try {
                    Jws<Claims> claims = Jwts.parser()
                            .verifyWith(Keys.hmacShaKeyFor(jwtSecret.getBytes()))
                            .build()
                            .parseSignedClaims(token);

                    tenantId = (String) claims.getPayload().get("tenant_id");
                    userId = (String) claims.getPayload().get("sub");
                } catch (Exception e) {
                    log.warn("Invalid token: {}", e.getMessage());
                    httpResponse.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid token");
                    return;
                }
            } else if (!isPublicEndpoint(httpRequest.getRequestURI())) {
                httpResponse.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Missing authentication");
                return;
            }

            if (tenantId != null) {
                RequestContext ctx = RequestContext.builder()
                        .tenantId(tenantId)
                        .userId(userId)
                        .correlationId(correlationId)
                        .traceId(traceId)
                        .build();

                RequestContextHolder.set(ctx);

                MDC.put("tenant_id", tenantId);
                MDC.put("user_id", userId);
                MDC.put("correlation_id", correlationId);
                MDC.put("trace_id", traceId);
            }

            chain.doFilter(request, response);
        } finally {
            RequestContextHolder.clear();
            MDC.clear();
        }
    }

    private boolean isPublicEndpoint(String uri) {
        return uri.contains("/health") || uri.contains("/actuator");
    }
}
