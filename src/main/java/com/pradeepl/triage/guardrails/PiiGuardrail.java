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

        // Get context about which agent/component is being evaluated
        String agentName = context.name();

        if (EMAIL_PATTERN.matcher(text).find()) {
            // KnowledgeBaseAgent uses MCP tools that may return PII
            boolean isMcpAgent = "knowledge-base-agent".equals(agentName);
            String sourceInfo = isMcpAgent ?
                " (likely from MCP tool/resource - KnowledgeBaseAgent uses MCP)" : "";

            logger.error("🚨 PII DETECTED: email address from agent '{}'{}",
                        agentName, sourceInfo);
            return new Result(false, String.format(
                "Email address detected from agent '%s'%s. " +
                "Guardrails blocked PII from being exposed.",
                agentName, sourceInfo));
        }

        if (PHONE_PATTERN.matcher(text).find()) {
            boolean isMcpAgent = "knowledge-base-agent".equals(agentName);
            String sourceInfo = isMcpAgent ? " (likely from MCP)" : "";
            logger.warn("🚨 PII DETECTED: phone number from agent '{}'{}",
                       agentName, sourceInfo);
            return new Result(false, String.format("Phone number detected from agent '%s'%s", agentName, sourceInfo));
        }

        if (CREDIT_CARD_PATTERN.matcher(text).find()) {
            boolean isMcpAgent = "knowledge-base-agent".equals(agentName);
            String sourceInfo = isMcpAgent ? " (likely from MCP)" : "";
            logger.warn("🚨 PII DETECTED: credit card number from agent '{}'{}",
                       agentName, sourceInfo);
            return new Result(false, String.format("Credit card number detected from agent '%s'%s", agentName, sourceInfo));
        }

        if (SSN_PATTERN.matcher(text).find()) {
            boolean isMcpAgent = "knowledge-base-agent".equals(agentName);
            String sourceInfo = isMcpAgent ? " (likely from MCP)" : "";
            logger.warn("🚨 PII DETECTED: SSN from agent '{}'{}",
                       agentName, sourceInfo);
            return new Result(false, String.format("Social Security Number detected from agent '%s'%s", agentName, sourceInfo));
        }

        if (IPV4_PATTERN.matcher(text).find()) {
            boolean isMcpAgent = "knowledge-base-agent".equals(agentName);
            String sourceInfo = isMcpAgent ? " (likely from MCP)" : "";
            logger.warn("🚨 PII DETECTED: IPv4 address from agent '{}'{}",
                       agentName, sourceInfo);
            return new Result(false, String.format("IP address detected from agent '%s'%s", agentName, sourceInfo));
        }

        if (IPV6_PATTERN.matcher(text).find()) {
            boolean isMcpAgent = "knowledge-base-agent".equals(agentName);
            String sourceInfo = isMcpAgent ? " (likely from MCP)" : "";
            logger.warn("🚨 PII DETECTED: IPv6 address from agent '{}'{}",
                       agentName, sourceInfo);
            return new Result(false, String.format("IPv6 address detected from agent '%s'%s", agentName, sourceInfo));
        }

        if (PASSPORT_PATTERN.matcher(text).find()) {
            boolean isMcpAgent = "knowledge-base-agent".equals(agentName);
            String sourceInfo = isMcpAgent ? " (likely from MCP)" : "";
            logger.warn("🚨 PII DETECTED: passport number from agent '{}'{}",
                       agentName, sourceInfo);
            return new Result(false, String.format("Passport number detected from agent '%s'%s", agentName, sourceInfo));
        }

        if (DRIVERS_LICENSE_PATTERN.matcher(text).find()) {
            boolean isMcpAgent = "knowledge-base-agent".equals(agentName);
            String sourceInfo = isMcpAgent ? " (likely from MCP)" : "";
            logger.warn("🚨 PII DETECTED: driver's license from agent '{}'{}",
                       agentName, sourceInfo);
            return new Result(false, String.format("Driver's license number detected from agent '%s'%s", agentName, sourceInfo));
        }

        return Result.OK;
    }
}
