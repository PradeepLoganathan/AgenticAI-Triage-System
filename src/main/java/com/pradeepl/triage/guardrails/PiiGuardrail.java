package com.pradeepl.triage.guardrails;

import akka.javasdk.agent.GuardrailContext;
import akka.javasdk.agent.TextGuardrail;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.regex.Pattern;

/**
 * PiiGuardrail detects and blocks Personally Identifiable Information (PII)
 * in agent inputs and outputs.
 *
 * Detects:
 * - Email addresses
 * - Phone numbers (US format)
 * - Credit card numbers
 * - Social Security Numbers
 */
public class PiiGuardrail implements TextGuardrail {

    private static final Logger logger = LoggerFactory.getLogger(PiiGuardrail.class);

    private static final Pattern EMAIL_PATTERN = Pattern.compile(
        "\\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Z|a-z]{2,}\\b"
    );

    private static final Pattern PHONE_PATTERN = Pattern.compile(
        "\\b(?:\\+?1[-.]?)?\\(?([0-9]{3})\\)?[-.]?([0-9]{3})[-.]?([0-9]{4})\\b"
    );

    private static final Pattern CREDIT_CARD_PATTERN = Pattern.compile(
        "\\b\\d{4}[-\\s]?\\d{4,5}[-\\s]?\\d{4}\\b"
    );

    private static final Pattern SSN_PATTERN = Pattern.compile(
        "\\b\\d{3}-\\d{2}-\\d{4}\\b"
    );

    private final GuardrailContext context;

    public PiiGuardrail(GuardrailContext context) {
        this.context = context;
        logger.warn("🛡️ PiiGuardrail INITIALIZED: {}", context.name());
    }

    @Override
    public Result evaluate(String text) {
        if (text == null || text.isEmpty()) {
            return Result.OK;
        }

        if (EMAIL_PATTERN.matcher(text).find()) {
            logger.error("🚨 PII DETECTED: email address in text");
            return new Result(false, "Email address detected in text");
        }

        if (PHONE_PATTERN.matcher(text).find()) {
            logger.warn("PII detected: phone number");
            return new Result(false, "Phone number detected in text");
        }

        if (CREDIT_CARD_PATTERN.matcher(text).find()) {
            logger.warn("PII detected: credit card number");
            return new Result(false, "Credit card number detected in text");
        }

        if (SSN_PATTERN.matcher(text).find()) {
            logger.warn("PII detected: SSN");
            return new Result(false, "Social Security Number detected in text");
        }

        return Result.OK;
    }
}
