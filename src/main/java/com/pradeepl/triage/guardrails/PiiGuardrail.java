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
 * - Phone numbers (US/international format)
 * - Credit card numbers
 * - Social Security Numbers
 * - IP addresses (IPv4/IPv6)
 * - Passport numbers
 * - Driver's license numbers
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

    private static final Pattern IPV4_PATTERN = Pattern.compile(
        "\\b(?:[0-9]{1,3}\\.){3}[0-9]{1,3}\\b"
    );

    private static final Pattern IPV6_PATTERN = Pattern.compile(
        "\\b(?:[0-9a-fA-F]{1,4}:){7}[0-9a-fA-F]{1,4}\\b"
    );

    private static final Pattern PASSPORT_PATTERN = Pattern.compile(
        "\\b[A-Z]{1,2}[0-9]{6,9}\\b"
    );

    private static final Pattern DRIVERS_LICENSE_PATTERN = Pattern.compile(
        "\\b[A-Z]{1,2}[0-9]{5,8}\\b"
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
            logger.warn("🚨 PII detected: phone number");
            return new Result(false, "Phone number detected in text");
        }

        if (CREDIT_CARD_PATTERN.matcher(text).find()) {
            logger.warn("🚨 PII detected: credit card number");
            return new Result(false, "Credit card number detected in text");
        }

        if (SSN_PATTERN.matcher(text).find()) {
            logger.warn("🚨 PII detected: SSN");
            return new Result(false, "Social Security Number detected in text");
        }

        if (IPV4_PATTERN.matcher(text).find()) {
            logger.warn("🚨 PII detected: IPv4 address");
            return new Result(false, "IP address detected in text");
        }

        if (IPV6_PATTERN.matcher(text).find()) {
            logger.warn("🚨 PII detected: IPv6 address");
            return new Result(false, "IPv6 address detected in text");
        }

        if (PASSPORT_PATTERN.matcher(text).find()) {
            logger.warn("🚨 PII detected: passport number");
            return new Result(false, "Passport number detected in text");
        }

        if (DRIVERS_LICENSE_PATTERN.matcher(text).find()) {
            logger.warn("🚨 PII detected: driver's license");
            return new Result(false, "Driver's license number detected in text");
        }

        return Result.OK;
    }
}
