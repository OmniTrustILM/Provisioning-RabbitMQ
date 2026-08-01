package com.otilm.provisioning.rabbitmq.config;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.util.ContentCachingRequestWrapper;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(OutputCaptureExtension.class)
class RequestLoggingFilterTest {

    private final RequestLoggingFilter filter = new RequestLoggingFilter();

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    /**
     * The filter only sees a request body once something downstream consumes the input
     * stream, which in production is Spring MVC. This chain stands in for that.
     */
    private static FilterChain chainThatReadsRequestAndWrites(String responseBody, String responseContentType) {
        FilterChain chain = mock(FilterChain.class);
        try {
            doAnswer(invocation -> {
                var request = (HttpServletRequest) invocation.getArgument(0);
                request.getInputStream().readAllBytes();
                if (responseBody != null) {
                    var response = (HttpServletResponse) invocation.getArgument(1);
                    response.setContentType(responseContentType);
                    response.getOutputStream().write(responseBody.getBytes(StandardCharsets.UTF_8));
                }
                return null;
            }).when(chain).doFilter(any(), any());
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        return chain;
    }

    @Test
    void missingCorrelationId_generatesUuidAndSetsResponseHeader() throws Exception {
        var request = new MockHttpServletRequest("GET", "/api/v1/proxies");
        var response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilterInternal(request, response, chain);

        String correlationId = response.getHeader(RequestLoggingFilter.CORRELATION_ID_HEADER);
        assertThat(correlationId)
                .isNotNull()
                .isNotBlank()
                .matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
    }

    @Test
    void presentCorrelationId_propagatedToResponseHeader() throws Exception {
        var request = new MockHttpServletRequest("POST", "/api/v1/proxies");
        request.addHeader(RequestLoggingFilter.CORRELATION_ID_HEADER, "my-test-id-123");
        var response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilterInternal(request, response, chain);

        assertThat(response.getHeader(RequestLoggingFilter.CORRELATION_ID_HEADER))
                .isEqualTo("my-test-id-123");
    }

    @Test
    void mdcIsSetDuringFilterAndClearedAfter() throws Exception {
        var request = new MockHttpServletRequest("GET", "/api/v1/proxies");
        request.addHeader(RequestLoggingFilter.CORRELATION_ID_HEADER, "corr-id-for-mdc");
        var response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        String[] mdcDuringFilter = new String[1];
        doAnswer(invocation -> {
            mdcDuringFilter[0] = MDC.get(RequestLoggingFilter.MDC_KEY);
            return null;
        }).when(chain).doFilter(any(), any());

        filter.doFilterInternal(request, response, chain);

        assertThat(mdcDuringFilter[0]).isEqualTo("corr-id-for-mdc");
        assertThat(MDC.get(RequestLoggingFilter.MDC_KEY)).isNull();
    }

    @Test
    void filterChainIsInvoked() throws Exception {
        var request = new MockHttpServletRequest("GET", "/api/v1/proxies");
        var response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilterInternal(request, response, chain);

        verify(chain).doFilter(any(), any());
    }

    @Test
    void requestAndResponseBodiesAreLogged(CapturedOutput output) throws Exception {
        var request = new MockHttpServletRequest("POST", "/api/v1/proxies");
        request.setContentType("application/json");
        request.setContent("{\"proxyCode\":\"MY_PROXY\"}".getBytes(StandardCharsets.UTF_8));
        var response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response,
                chainThatReadsRequestAndWrites("{\"status\":\"ok\"}", "application/json"));

        assertThat(output)
                .contains(">> POST /api/v1/proxies body={\"proxyCode\":\"MY_PROXY\"}")
                .contains("body={\"status\":\"ok\"}");
    }

    @Test
    void trailingNewlineInBodyDoesNotLeaveAPlaceholder(CapturedOutput output) throws Exception {
        var request = new MockHttpServletRequest("POST", "/api/v1/proxies");
        request.setContentType("application/json");
        request.setContent("{\"proxyCode\": \"MY_PROXY\"}\n".getBytes(StandardCharsets.UTF_8));
        var response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, chainThatReadsRequestAndWrites(null, null));

        assertThat(output)
                .contains("body={\"proxyCode\": \"MY_PROXY\"}")
                .doesNotContain("body={\"proxyCode\": \"MY_PROXY\"}_");
    }

    @Test
    void whitespaceOnlyBodyIsLoggedAsEmpty(CapturedOutput output) throws Exception {
        var request = new MockHttpServletRequest("POST", "/api/v1/proxies");
        request.setContentType("application/json");
        request.setContent("   \n".getBytes(StandardCharsets.UTF_8));
        var response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, chainThatReadsRequestAndWrites(null, null));

        assertThat(output).contains(">> POST /api/v1/proxies body=<empty>");
    }

    @Test
    void queryStringIsAppendedToLoggedUri(CapturedOutput output) throws Exception {
        var request = new MockHttpServletRequest("GET", "/api/v1/proxies/P1/installation");
        request.setQueryString("format=helm");
        var response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, mock(FilterChain.class));

        assertThat(output).contains(">> GET /api/v1/proxies/P1/installation?format=helm");
    }

    @Test
    void emptyBodyIsLoggedAsPlaceholder(CapturedOutput output) throws Exception {
        var request = new MockHttpServletRequest("DELETE", "/api/v1/proxies/P1");
        var response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, mock(FilterChain.class));

        assertThat(output).contains(">> DELETE /api/v1/proxies/P1 body=<empty>");
    }

    @Test
    void binaryBodyIsNotLoggedVerbatim(CapturedOutput output) throws Exception {
        var request = new MockHttpServletRequest("POST", "/api/v1/proxies");
        request.setContentType("application/octet-stream");
        request.setContent(new byte[]{1, 2, 3, 4});
        var response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, chainThatReadsRequestAndWrites(null, null));

        assertThat(output).contains(">> POST /api/v1/proxies body=<binary>");
    }

    @Test
    void bodyWithoutContentTypeIsLoggedAsText(CapturedOutput output) throws Exception {
        var request = new MockHttpServletRequest("POST", "/api/v1/proxies");
        request.setContent("plain payload".getBytes(StandardCharsets.UTF_8));
        var response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, chainThatReadsRequestAndWrites(null, null));

        assertThat(output).contains(">> POST /api/v1/proxies body=plain payload");
    }

    @Test
    void oversizedBodyIsTruncatedInTheLogButReachesTheApplicationIntact(CapturedOutput output) throws Exception {
        String longBody = "x".repeat(2000);
        var request = new MockHttpServletRequest("POST", "/api/v1/proxies");
        request.setContentType("application/json");
        request.setContent(longBody.getBytes(StandardCharsets.UTF_8));
        var response = new MockHttpServletResponse();

        byte[][] received = new byte[1][];
        int[] cachedLength = new int[1];
        FilterChain chain = mock(FilterChain.class);
        doAnswer(invocation -> {
            var wrapped = (ContentCachingRequestWrapper) invocation.getArgument(0);
            received[0] = wrapped.getInputStream().readAllBytes();
            cachedLength[0] = wrapped.getContentAsByteArray().length;
            return null;
        }).when(chain).doFilter(any(), any());

        filter.doFilterInternal(request, response, chain);

        assertThat(new String(received[0], StandardCharsets.UTF_8)).isEqualTo(longBody);
        // The wrapper must stop caching well before the end of the body; otherwise an
        // attacker-sized request would be buffered in memory in full just to produce a
        // 1000-character log line. Comparing against the body length rather than the
        // constant keeps this assertion meaningful if the cap is ever raised.
        assertThat(cachedLength[0])
                .isLessThan(longBody.length())
                .isLessThanOrEqualTo(RequestLoggingFilter.CONTENT_CACHE_LIMIT);
        assertThat(output)
                .contains("...<truncated>")
                .doesNotContain("x".repeat(1001));
    }

    @Test
    void controlCharactersAreReplacedSoLogLinesCannotBeForged(CapturedOutput output) throws Exception {
        var request = new MockHttpServletRequest("GET", "/api/v1/proxies");
        request.setQueryString("format=helm\nINFO forged log line");
        var response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, mock(FilterChain.class));

        assertThat(output)
                .contains("format=helm_INFO forged log line")
                .doesNotContain("helm\nINFO forged");
    }

    @Test
    void nothingIsLoggedWhenInfoLevelIsDisabled(CapturedOutput output) throws Exception {
        var logger = (Logger) LoggerFactory.getLogger(RequestLoggingFilter.class);
        Level originalLevel = logger.getLevel();
        logger.setLevel(Level.WARN);
        try {
            var request = new MockHttpServletRequest("POST", "/api/v1/proxies");
            request.setContentType("application/json");
            request.setContent("{\"proxyCode\":\"QUIET\"}".getBytes(StandardCharsets.UTF_8));
            var response = new MockHttpServletResponse();

            filter.doFilterInternal(request, response, chainThatReadsRequestAndWrites(null, null));

            assertThat(output)
                    .doesNotContain("QUIET")
                    .doesNotContain(">> POST /api/v1/proxies");
        } finally {
            logger.setLevel(originalLevel);
        }
    }
}
