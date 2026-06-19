package com.automationhub.workflow.service.action;

import com.automationhub.workflow.entity.Action;
import com.automationhub.workflow.entity.ActionType;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

class HttpActionExecutorTest {

    private MockWebServer server;
    private HttpActionExecutor executor;

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
        server.start();
        executor = new HttpActionExecutor(new ObjectMapper());
    }

    @AfterEach
    void tearDown() throws IOException {
        server.shutdown();
    }

    @Test
    void returns_success_when_response_is_2xx() {
        server.enqueue(new MockResponse().setResponseCode(200).setBody("ok"));
        Action action = httpAction("{\"url\":\"" + server.url("/ping") + "\",\"method\":\"GET\"}");

        ActionResult result = executor.execute(action);

        assertThat(result.success()).isTrue();
        assertThat(result.message()).contains("200").contains("/ping");
    }

    @Test
    void returns_failure_when_response_is_non_2xx() {
        server.enqueue(new MockResponse().setResponseCode(500).setBody("boom"));
        Action action = httpAction("{\"url\":\"" + server.url("/oops") + "\",\"method\":\"GET\"}");

        ActionResult result = executor.execute(action);

        assertThat(result.success()).isFalse();
        assertThat(result.message()).contains("500").contains("/oops");
    }

    @Test
    void returns_failure_when_config_is_missing() {
        Action action = httpAction(null);

        ActionResult result = executor.execute(action);

        assertThat(result.success()).isFalse();
        assertThat(result.message()).contains("missing config");
    }

    @Test
    void returns_failure_when_config_lacks_url() {
        Action action = httpAction("{\"method\":\"GET\"}");

        ActionResult result = executor.execute(action);

        assertThat(result.success()).isFalse();
        assertThat(result.message()).contains("url");
    }

    private static Action httpAction(String config) {
        return Action.builder()
                .type(ActionType.HTTP)
                .order(1)
                .config(config)
                .build();
    }
}
