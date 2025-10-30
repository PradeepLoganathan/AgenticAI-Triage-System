package com.pradeepl.triage.guardrails;

import akka.javasdk.agent.GuardrailContext;
import akka.javasdk.agent.TextGuardrail;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.regex.Pattern;

/**
 * DataLeakageGuardrail detects and blocks sensitive data that could lead to
 * information disclosure or security breaches.
 *
 * Detects:
 * - AWS access keys
 * - API keys and tokens
 * - Private keys
 * - Database connection strings
 * - JWT tokens
 * - Passwords in plain text
 */
public class DataLeakageGuardrail implements TextGuardrail {

    private static final Logger logger = LoggerFactory.getLogger(DataLeakageGuardrail.class);

    private static final Pattern AWS_ACCESS_KEY_PATTERN = Pattern.compile(
        "\\b(AKIA[0-9A-Z]{16})\\b"
    );

    private static final Pattern AWS_SECRET_KEY_PATTERN = Pattern.compile(
        "\\b[A-Za-z0-9/+=]{40}\\b"
    );

    private static final Pattern API_KEY_PATTERN = Pattern.compile(
        "(?i)(api[_-]?key|apikey|access[_-]?token)\\s*[:=]\\s*['\"]?([a-zA-Z0-9_\\-]{20,})['\"]?"
    );

    private static final Pattern PRIVATE_KEY_PATTERN = Pattern.compile(
        "-----BEGIN (RSA |EC )?PRIVATE KEY-----"
    );

    private static final Pattern DB_CONNECTION_PATTERN = Pattern.compile(
        "(?i)(jdbc|mongodb|mysql|postgresql|postgres)://[^\\s]+"
    );

    private static final Pattern JWT_PATTERN = Pattern.compile(
        "\\beyJ[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\b"
    );

    private static final Pattern PASSWORD_PATTERN = Pattern.compile(
        "(?i)(password|passwd|pwd)\\s*[:=]\\s*['\"]?([^\\s'\"]{6,})['\"]?"
    );

    private final GuardrailContext context;

    public DataLeakageGuardrail(GuardrailContext context) {
        this.context = context;
        logger.info("🛡️ DataLeakageGuardrail INITIALIZED: {}", context.name());
    }

    @Override
    public Result evaluate(String text) {
        if (text == null || text.isEmpty()) {
            return Result.OK;
        }

        if (AWS_ACCESS_KEY_PATTERN.matcher(text).find()) {
            logger.error("🚨 DATA LEAKAGE: AWS access key detected");
            return new Result(false, "AWS access key detected in text");
        }

        if (PRIVATE_KEY_PATTERN.matcher(text).find()) {
            logger.error("🚨 DATA LEAKAGE: Private key detected");
            return new Result(false, "Private key detected in text");
        }

        if (API_KEY_PATTERN.matcher(text).find()) {
            logger.error("🚨 DATA LEAKAGE: API key detected");
            return new Result(false, "API key or access token detected in text");
        }

        if (DB_CONNECTION_PATTERN.matcher(text).find()) {
            logger.error("🚨 DATA LEAKAGE: Database connection string detected");
            return new Result(false, "Database connection string detected in text");
        }

        if (JWT_PATTERN.matcher(text).find()) {
            logger.error("🚨 DATA LEAKAGE: JWT token detected");
            return new Result(false, "JWT token detected in text");
        }

        if (PASSWORD_PATTERN.matcher(text).find()) {
            logger.error("🚨 DATA LEAKAGE: Password detected");
            return new Result(false, "Password detected in text");
        }

        return Result.OK;
    }
}
