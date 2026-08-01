package com.otilm.provisioning.rabbitmq.config;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
@ConditionalOnProperty(name = "app.security.api-key-enabled", havingValue = "true")
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class ApiKeyFilter implements Filter {

    private final SecurityConfigProperties properties;

    public ApiKeyFilter(SecurityConfigProperties properties) {
        if (properties.apiKey() == null || properties.apiKey().isBlank()) {
            throw new IllegalStateException(
                    "app.security.api-key must be set when app.security.api-key-enabled is true");
        }
        this.properties = properties;
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        var httpRequest = (HttpServletRequest) request;
        var httpResponse = (HttpServletResponse) response;

        String requestUri = httpRequest.getRequestURI();
        if (properties.publicPaths() != null && properties.publicPaths().stream().anyMatch(requestUri::startsWith)) {
            chain.doFilter(request, response);
            return;
        }

        String apiKey = httpRequest.getHeader(properties.apiKeyHeader());
        if (!properties.apiKey().equals(apiKey)) {
            httpResponse.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            httpResponse.setContentType("application/json");
            httpResponse.getWriter().write("{\"error\":\"Invalid or missing " + properties.apiKeyHeader() + " header\"}");
            return;
        }

        chain.doFilter(request, response);
    }
}