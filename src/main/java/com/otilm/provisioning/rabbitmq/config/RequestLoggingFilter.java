package com.otilm.provisioning.rabbitmq.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.regex.Pattern;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestLoggingFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RequestLoggingFilter.class);

    static final String CORRELATION_ID_HEADER = "X-Correlation-ID";
    static final String MDC_KEY = "correlationId";
    private static final int MAX_BODY_LENGTH = 1000;
    /**
     * One byte beyond the logged maximum, so a body that exceeds the limit is still
     * recognisable as truncated while the cache stays bounded.
     */
    static final int CONTENT_CACHE_LIMIT = MAX_BODY_LENGTH + 1;
    private static final Pattern CONTROL_CHARACTERS = Pattern.compile("\\p{Cntrl}");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        String correlationId = request.getHeader(CORRELATION_ID_HEADER);
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = UUID.randomUUID().toString();
        }

        MDC.put(MDC_KEY, correlationId);

        var wrappedRequest = new ContentCachingRequestWrapper(request, CONTENT_CACHE_LIMIT);
        var wrappedResponse = new ContentCachingResponseWrapper(response);

        long startNano = System.nanoTime();
        try {
            chain.doFilter(wrappedRequest, wrappedResponse);
        } finally {
            long elapsedMs = (System.nanoTime() - startNano) / 1_000_000;

            logRequest(wrappedRequest);
            logResponse(wrappedResponse, elapsedMs);

            wrappedResponse.setHeader(CORRELATION_ID_HEADER, correlationId);
            wrappedResponse.copyBodyToResponse();

            MDC.clear();
        }
    }

    private void logRequest(ContentCachingRequestWrapper request) {
        if (!log.isInfoEnabled()) {
            return;
        }
        String query = request.getQueryString();
        String uri = request.getRequestURI() + (query != null ? "?" + query : "");
        String body = extractBody(request.getContentAsByteArray(), request.getContentType());
        log.info(">> {} {} body={}", sanitize(request.getMethod()), sanitize(uri), sanitize(body));
    }

    private void logResponse(ContentCachingResponseWrapper response, long elapsedMs) {
        if (!log.isInfoEnabled()) {
            return;
        }
        String body = extractBody(response.getContentAsByteArray(), response.getContentType());
        log.info("<< {} in {}ms body={}", response.getStatus(), elapsedMs, sanitize(body));
    }

    private String extractBody(byte[] content, String contentType) {
        if (content == null || content.length == 0) {
            return "<empty>";
        }
        if (isBinary(contentType)) {
            return "<binary>";
        }
        boolean truncated = content.length > MAX_BODY_LENGTH;
        // Surrounding whitespace is stripped so that the trailing newline most clients send
        // does not reach the sanitizer and show up as a stray placeholder in the log line.
        String body = new String(content, 0, truncated ? MAX_BODY_LENGTH : content.length, StandardCharsets.UTF_8)
                .strip();
        if (body.isEmpty()) {
            return "<empty>";
        }
        return truncated ? body + "...<truncated>" : body;
    }

    /**
     * Replaces control characters in request-derived values so that a caller cannot forge
     * additional log lines by embedding newlines in a URI, header or body. Every caller
     * passes a value the servlet contract guarantees to be present, or one produced by
     * {@link #extractBody}, so {@code value} is never {@code null}.
     */
    private String sanitize(String value) {
        return CONTROL_CHARACTERS.matcher(value).replaceAll("_");
    }

    private boolean isBinary(String contentType) {
        if (contentType == null) {
            return false;
        }
        String ct = contentType.toLowerCase();
        return ct.contains("octet-stream") || ct.contains("multipart");
    }
}
