package com.example.sample;

import akka.javasdk.testkit.TestKit;
import akka.javasdk.testkit.TestKitSupport;
import akka.javasdk.testkit.TestModelProvider;
import com.example.sample.application.ClassifierAgent;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ClassifierAgentJsonTest extends TestKitSupport {

    private final TestModelProvider classifierModel = new TestModelProvider();

    @Override
    protected TestKit.Settings testKitSettings() {
        return TestKit.Settings.DEFAULT
                .withModelProvider(ClassifierAgent.class, classifierModel);
    }

    @Test
    public void returns_strict_json_with_expected_fields() throws Exception {
        String json = "{\"service\":\"checkout\",\"severity\":\"P2\",\"domain\":\"payments\",\"rationale\":\"spike after deploy\"}";
        classifierModel.fixedResponse(json);

        String res = componentClient
                .forAgent()
                .method(ClassifierAgent::classify)
                .invoke(new ClassifierAgent.Request("Checkout 5xx spike after deploy"));

        ObjectMapper mapper = new ObjectMapper();
        JsonNode node = mapper.readTree(res);
        assertThat(node.get("service").asText()).isNotBlank();
        assertThat(node.get("domain").asText()).isNotBlank();
        String severity = node.get("severity").asText();
        assertThat(severity).isIn("P1", "P2", "P3", "P4");
    }
}

