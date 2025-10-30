package com.pradeepl.triage.guardrails;

import akka.javasdk.agent.GuardrailContext;
import akka.javasdk.agent.TextGuardrail;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.regex.Pattern;

/**
 * ProfanityGuardrail detects and blocks offensive language and profanity
 * in agent inputs and outputs.
 */
public class ProfanityGuardrail implements TextGuardrail {

    private static final Logger logger = LoggerFactory.getLogger(ProfanityGuardrail.class);

    private static final List<String> PROFANITY_WORDS = List.of(
        "damn", "hell", "crap", "shit", "fuck", "ass", "bitch", "bastard"
    );

    private static final Pattern PROFANITY_PATTERN = Pattern.compile(
        "\\b(" + String.join("|", PROFANITY_WORDS) + ")\\b",
        Pattern.CASE_INSENSITIVE
    );

    private final GuardrailContext context;

    public ProfanityGuardrail(GuardrailContext context) {
        this.context = context;
        logger.info("🛡️ ProfanityGuardrail INITIALIZED: {}", context.name());
    }

    @Override
    public Result evaluate(String text) {
        if (text == null || text.isEmpty()) {
            return Result.OK;
        }

        if (PROFANITY_PATTERN.matcher(text).find()) {
            logger.warn("🚨 Profanity detected in text");
            return new Result(false, "Offensive language detected in text");
        }

        return Result.OK;
    }
}
