package com.otilm.provisioning.rabbitmq.controller;

import com.otilm.provisioning.rabbitmq.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@TestPropertySource(properties = {
        "app.security.api-key-enabled=true",
        "app.security.api-key=" + QueueProvisioningControllerIT.API_KEY
})
class QueueProvisioningControllerIT {

    public static final String API_KEY = "test-api-key";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Test
    void provisionQueue_returns201() throws Exception {
        mockMvc.perform(post("/api/v1/queues")
                        .header("X-API-Key", API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "it-q-basic",
                                  "exchange": "ilm-proxy",
                                  "routingKey": "proxymessage.*.it-q-basic",
                                  "properties": { "x-expires": 60000 }
                                }
                                """))
                .andExpect(status().isCreated());
    }

    @Test
    void provisionQueue_isIdempotent() throws Exception {
        String body = """
                {
                  "name": "it-q-idempotent",
                  "exchange": "ilm-proxy",
                  "routingKey": "proxymessage.*.it-q-idempotent",
                  "properties": { "x-expires": 60000 }
                }
                """;

        mockMvc.perform(post("/api/v1/queues")
                        .header("X-API-Key", API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/queues")
                        .header("X-API-Key", API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());
    }

    @Test
    void provisionQueue_addsBindingForDifferentRoutingKey() throws Exception {
        String queueName = "it-q-additive-bindings";

        provisionQueue(queueName, "it.route.one");
        provisionQueue(queueName, "it.route.two");

        rabbitTemplate.convertAndSend("ilm-proxy", "it.route.one", "first");
        rabbitTemplate.convertAndSend("ilm-proxy", "it.route.two", "second");

        assertThat(rabbitTemplate.receiveAndConvert(queueName, 5_000)).isEqualTo("first");
        assertThat(rabbitTemplate.receiveAndConvert(queueName, 5_000)).isEqualTo("second");
    }

    @Test
    void provisionQueue_returns409_whenQueueExistsWithDifferentArguments() throws Exception {
        mockMvc.perform(post("/api/v1/queues")
                        .header("X-API-Key", API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "it-q-conflict",
                                  "exchange": "ilm-proxy",
                                  "routingKey": "proxymessage.*.it-q-conflict",
                                  "properties": { "x-expires": 60000 }
                                }
                                """))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/queues")
                        .header("X-API-Key", API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "it-q-conflict",
                                  "exchange": "ilm-proxy",
                                  "routingKey": "proxymessage.*.it-q-conflict",
                                  "properties": { "x-expires": 120000 }
                                }
                                """))
                .andExpect(status().isConflict());
    }

    @Test
    void provisionQueue_returns400_whenNameIsMissing() throws Exception {
        mockMvc.perform(post("/api/v1/queues")
                        .header("X-API-Key", API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "exchange": "ilm-proxy",
                                  "routingKey": "proxymessage.*.core-0"
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void deleteQueue_returns204_forExistingQueue() throws Exception {
        mockMvc.perform(post("/api/v1/queues")
                        .header("X-API-Key", API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "it-q-to-delete",
                                  "exchange": "ilm-proxy",
                                  "routingKey": "proxymessage.*.it-q-to-delete"
                                }
                                """))
                .andExpect(status().isCreated());

        mockMvc.perform(delete("/api/v1/queues/it-q-to-delete")
                        .header("X-API-Key", API_KEY))
                .andExpect(status().isNoContent());
    }

    @Test
    void deleteQueue_returns204_evenWhenQueueDoesNotExist() throws Exception {
        mockMvc.perform(delete("/api/v1/queues/nonexistent-queue")
                        .header("X-API-Key", API_KEY))
                .andExpect(status().isNoContent());
    }

    @Test
    void deleteQueue_isIdempotent() throws Exception {
        mockMvc.perform(post("/api/v1/queues")
                        .header("X-API-Key", API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "it-q-double-delete",
                                  "exchange": "ilm-proxy",
                                  "routingKey": "proxymessage.*.it-q-double-delete"
                                }
                                """))
                .andExpect(status().isCreated());

        mockMvc.perform(delete("/api/v1/queues/it-q-double-delete")
                        .header("X-API-Key", API_KEY))
                .andExpect(status().isNoContent());

        mockMvc.perform(delete("/api/v1/queues/it-q-double-delete")
                        .header("X-API-Key", API_KEY))
                .andExpect(status().isNoContent());
    }

    @Test
    void anyRequest_returns401_withoutApiKey() throws Exception {
        mockMvc.perform(post("/api/v1/queues")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "core-0",
                                  "exchange": "ilm-proxy",
                                  "routingKey": "proxymessage.*.core-0"
                                }
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void anyRequest_returns401_withWrongApiKey() throws Exception {
        mockMvc.perform(post("/api/v1/queues")
                        .header("X-API-Key", "wrong-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "core-0",
                                  "exchange": "ilm-proxy",
                                  "routingKey": "proxymessage.*.core-0"
                                }
                                """))
                .andExpect(status().isUnauthorized());
    }

    private void provisionQueue(String queueName, String routingKey) throws Exception {
        mockMvc.perform(post("/api/v1/queues")
                        .header("X-API-Key", API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "%s",
                                  "exchange": "ilm-proxy",
                                  "routingKey": "%s"
                                }
                                """.formatted(queueName, routingKey)))
                .andExpect(status().isCreated());
    }
}
