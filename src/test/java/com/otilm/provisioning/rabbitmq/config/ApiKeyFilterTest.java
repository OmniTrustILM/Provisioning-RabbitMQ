package com.otilm.provisioning.rabbitmq.config;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ApiKeyFilterTest {

    private static final String API_KEY = "secret-key";
    private static final String API_KEY_HEADER = "X-API-Key";

    private static SecurityConfigProperties properties(String apiKey, List<String> publicPaths) {
        return new SecurityConfigProperties(true, apiKey, API_KEY_HEADER, publicPaths);
    }

    private ApiKeyFilter filter(String apiKey, List<String> publicPaths) {
        return new ApiKeyFilter(properties(apiKey, publicPaths));
    }

    @Test
    void constructor_throwsIllegalStateException_whenApiKeyIsNull() {
        var properties = properties(null, List.of());

        assertThatThrownBy(() -> new ApiKeyFilter(properties))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void constructor_throwsIllegalStateException_whenApiKeyIsBlank() {
        var properties = properties("   ", List.of());

        assertThatThrownBy(() -> new ApiKeyFilter(properties))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void doFilter_chainsThrough_whenRequestPathIsPublic() throws Exception {
        var request = new MockHttpServletRequest("GET", "/actuator/health");
        var response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter(API_KEY, List.of("/actuator/health")).doFilter(request, response, chain);

        verify(chain).doFilter(any(), any());
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void doFilter_chainsThrough_whenApiKeyIsValid() throws Exception {
        var request = new MockHttpServletRequest("GET", "/api/v1/proxies");
        request.addHeader(API_KEY_HEADER, API_KEY);
        var response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter(API_KEY, List.of()).doFilter(request, response, chain);

        verify(chain).doFilter(any(), any());
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void doFilter_returns401_whenApiKeyHeaderIsMissing() throws Exception {
        var request = new MockHttpServletRequest("GET", "/api/v1/proxies");
        var response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter(API_KEY, List.of()).doFilter(request, response, chain);

        verifyNoInteractions(chain);
        assertThat(response.getStatus()).isEqualTo(401);
    }

    @Test
    void doFilter_returns401_whenApiKeyIsWrong() throws Exception {
        var request = new MockHttpServletRequest("POST", "/api/v1/proxies");
        request.addHeader(API_KEY_HEADER, "wrong-key");
        var response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter(API_KEY, List.of()).doFilter(request, response, chain);

        verifyNoInteractions(chain);
        assertThat(response.getStatus()).isEqualTo(401);
    }

    @Test
    void doFilter_chainsThrough_whenPublicPathsIsNull() throws Exception {
        var request = new MockHttpServletRequest("GET", "/api/v1/proxies");
        request.addHeader(API_KEY_HEADER, API_KEY);
        var response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter(API_KEY, null).doFilter(request, response, chain);

        verify(chain).doFilter(any(), any());
    }
}
