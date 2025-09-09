package com.example.sample;

import com.example.sample.application.ClassifierAgent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Function Tool Usage Demonstration")
public class FunctionToolDemo {

    @Test
    @DisplayName("Demonstrate improved function tool design")
    public void demonstrateFunctionTools() {
        ClassifierAgent agent = new ClassifierAgent();
        
        // Test the new get_known_services function tool
        String servicesList = agent.getKnownServices();
        System.out.println("=== GET_KNOWN_SERVICES OUTPUT ===");
        System.out.println(servicesList);
        System.out.println();
        
        // Assertions
        assertThat(servicesList).contains("payment-service", "user-service", "Core Business");
        assertThat(servicesList).contains("Always select a service from this list");
    }
}