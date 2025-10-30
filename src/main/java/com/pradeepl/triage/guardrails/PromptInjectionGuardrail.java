package com.pradeepl.triage.guardrails;

import akka.javasdk.agent.GuardrailContext;
import akka.javasdk.agent.TextGuardrail;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * PromptInjectionGuardrail detects and blocks prompt injection attacks
 * in agent inputs.
 *
 * Detects common prompt injection patterns like:
 * - "Ignore previous instructions"
 * - "You are now..."
 * - System prompt overrides
 */
public class PromptInjectionGuardrail implements TextGuardrail {

    private static final Logger logger = LoggerFactory.getLogger(PromptInjectionGuardrail.class);

    private static final List<String> INJECTION_PATTERNS = List.of(
        "ignore previous instructions",
        "ignore all previous",
        "disregard previous",
        "forget previous",
        "you are now",
        "new instructions:",
        "system:",
        "override instructions",
        "act as if",
        "pretend you are"
    );

    private final GuardrailContext context;

    public PromptInjectionGuardrail(GuardrailContext context) {
        this.context = context;
        logger.info("PromptInjectionGuardrail initialized: {}", context.name());
    }

    @Override
    public Result evaluate(String text) {
        if (text == null || text.isEmpty()) {
            return Result.OK;
        }

        String lowerText = text.toLowerCase();

        for (String pattern : INJECTION_PATTERNS) {
            if (lowerText.contains(pattern)) {
                logger.warn("Prompt injection detected: pattern '{}' found", pattern);
                return new Result(false, "Potential prompt injection detected: " + pattern);
            }
        }

        return Result.OK;
    }
}
