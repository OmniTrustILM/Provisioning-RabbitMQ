package com.otilm.provisioning.rabbitmq.controller;

import com.otilm.provisioning.rabbitmq.TestcontainersConfiguration;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@TestPropertySource(properties = {
        "app.security.api-key-enabled=true",
        "app.security.api-key=" + ProxyProvisioningControllerIT.API_KEY
})
class ProxyProvisioningControllerIT {

    public static final String API_KEY = "test-api-key";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void provisionProxy_returns201() throws Exception {
        mockMvc.perform(post("/api/v1/proxies")
                        .header("X-API-Key", API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"proxyCode": "IT_TEST_1"}
                                """))
                .andExpect(status().isCreated());
    }

    @Test
    void provisionProxy_isIdempotent() throws Exception {
        String body = """
                {"proxyCode": "IT_IDEMPOTENT"}
                """;
        mockMvc.perform(post("/api/v1/proxies")
                        .header("X-API-Key", API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/proxies")
                        .header("X-API-Key", API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());
    }

    @Test
    void decommissionProxy_returns204() throws Exception {
        mockMvc.perform(post("/api/v1/proxies")
                        .header("X-API-Key", API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"proxyCode": "IT_DELETE"}
                                """))
                .andExpect(status().isCreated());

        mockMvc.perform(delete("/api/v1/proxies/IT_DELETE")
                        .header("X-API-Key", API_KEY))
                .andExpect(status().isNoContent());
    }

    @Test
    void decommissionProxy_isIdempotent() throws Exception {
        mockMvc.perform(post("/api/v1/proxies")
                        .header("X-API-Key", API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"proxyCode": "IT_IDEMPOTENT_DELETE"}
                                """))
                .andExpect(status().isCreated());

        mockMvc.perform(delete("/api/v1/proxies/IT_IDEMPOTENT_DELETE")
                        .header("X-API-Key", API_KEY))
                .andExpect(status().isNoContent());

        mockMvc.perform(delete("/api/v1/proxies/IT_IDEMPOTENT_DELETE")
                        .header("X-API-Key", API_KEY))
                .andExpect(status().isNoContent());
    }

    @Test
    void getInstallationInstructions_returnsHelmCommand() throws Exception {
        mockMvc.perform(post("/api/v1/proxies")
                        .header("X-API-Key", API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"proxyCode": "IT_INSTALL"}
                                """))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/proxies/IT_INSTALL/installation")
                        .header("X-API-Key", API_KEY)
                        .param("format", "helm"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.command.shell").value(containsString(
                        "helm install proxy oci://hub.omnitrustregistry.com/ilm-helm/proxy --set token=")));
    }

    @Test
    void getInstallationInstructions_returns400_forUnsupportedFormat() throws Exception {
        mockMvc.perform(get("/api/v1/proxies/SOME_PROXY/installation")
                        .header("X-API-Key", API_KEY)
                        .param("format", "docker-compose"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void anyRequest_returns401_withoutApiKey() throws Exception {
        mockMvc.perform(post("/api/v1/proxies")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"proxyCode": "NO_AUTH"}
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void anyRequest_returns401_withWrongApiKey() throws Exception {
        mockMvc.perform(post("/api/v1/proxies")
                        .header("X-API-Key", "wrong-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"proxyCode": "WRONG_AUTH"}
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void actuatorHealth_returns200_withoutApiKey() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk());
    }

    @Nested
    @SpringBootTest
    @AutoConfigureMockMvc
    @Import(TestcontainersConfiguration.class)
    @TestPropertySource(properties = "app.security.api-key-enabled=false")
    class WithApiKeyDisabledIT {

        @Autowired
        private MockMvc mockMvc;

        @Test
        void anyRequest_returns2xx_withoutApiKeyHeader() throws Exception {
            mockMvc.perform(post("/api/v1/proxies")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"proxyCode": "NO_AUTH_DISABLED"}
                                    """))
                    .andExpect(status().isCreated());
        }

        @Test
        void anyRequest_returns2xx_withAnyApiKeyHeader() throws Exception {
            mockMvc.perform(post("/api/v1/proxies")
                            .header("X-API-Key", "any-random-key")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"proxyCode": "ANY_KEY_DISABLED"}
                                    """))
                    .andExpect(status().isCreated());
        }
    }
}
