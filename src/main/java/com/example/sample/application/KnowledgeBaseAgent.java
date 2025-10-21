package com.example.sample.application;

import akka.javasdk.agent.Agent;
import akka.javasdk.agent.ModelProvider;
import akka.javasdk.agent.MemoryProvider;
import akka.javasdk.annotations.Component;
import akka.javasdk.annotations.FunctionTool;
import akka.javasdk.annotations.Description;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.JarURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Component(id = "knowledge-base-agent")
public class KnowledgeBaseAgent extends Agent {

    private static final Logger logger = LoggerFactory.getLogger(KnowledgeBaseAgent.class);
    
    private static final String SYSTEM = """
        You are a knowledge base retrieval assistant.
        
        Task:
        - Use the `search_knowledge_base` tool with the provided query to find relevant runbooks, incident reports, and documentation.
        - Return concise, helpful results. Prefer summarizing key findings and include file names as sources.
        - If no results are found, say so explicitly.
        
        Always call the tool before responding.
        """;

    public Effect<String> search(String query) {
        logger.info("🧠 KnowledgeBaseAgent.search() - invoking model with tool for query: {}", query);
        return effects()
                .model(
                        ModelProvider.openAi()
                                .withApiKey(System.getenv("OPENAI_API_KEY"))
                                .withModelName("gpt-4o-mini")
                                .withTemperature(0.1)
                                .withMaxTokens(1200)
                )
                .memory(MemoryProvider.limitedWindow())
                .tools(this)
                .systemMessage(SYSTEM)
                .userMessage("Query: " + (query == null ? "" : query))
                .thenReply();
    }

    public KnowledgeBaseAgent() {
        // Startup self-check to verify knowledge_base packaging and availability
        try {
            URL resource = getClass().getClassLoader().getResource("knowledge_base");
            if (resource == null) {
                logger.warn("knowledge_base directory not found on classpath at startup");
                return;
            }

            URI uri = resource.toURI();
            if ("jar".equalsIgnoreCase(uri.getScheme())) {
                try {
                    JarURLConnection conn = (JarURLConnection) resource.openConnection();
                    try (java.util.jar.JarFile jar = conn.getJarFile()) {
                        String mutablePrefix = conn.getEntryName();
                        if (mutablePrefix == null || mutablePrefix.isEmpty()) mutablePrefix = "knowledge_base";
                        if (!mutablePrefix.endsWith("/")) mutablePrefix = mutablePrefix + "/";
                        final String prefix = mutablePrefix;
                        long count = jar.stream()
                                .filter(e -> !e.isDirectory())
                                .map(java.util.jar.JarEntry::getName)
                                .filter(name -> name.startsWith(prefix))
                                .count();
                        logger.info("Knowledge base available in JAR: {} files under {}", count, prefix);
                    }
                } catch (Exception e) {
                    logger.error("Startup check: failed to verify knowledge_base inside JAR", e);
                }
            } else {
                Path basePath = Paths.get(uri);
                try (Stream<Path> paths = Files.walk(basePath)) {
                    long count = paths.filter(Files::isRegularFile).count();
                    logger.info("Knowledge base available on filesystem: {} files at {}", count, basePath);
                }
            }
        } catch (Exception e) {
            logger.error("Startup check: error verifying knowledge_base availability", e);
        }
    }

    @FunctionTool(name = "search_knowledge_base", description = "Searches the knowledge base for runbooks, incident reports, and other documents related to a query.")
    public String searchKnowledgeBase(
            @Description("The search query, typically containing a service name, error message, or symptom.") String query
    ) {
        logger.info("Searching knowledge base for query: {}", query);
        URL resource = getClass().getClassLoader().getResource("knowledge_base");
        if (resource == null) {
            return "Error: knowledge_base directory not found.";
        }

        try {
            URI uri = resource.toURI();

            Path basePath;
            if ("jar".equalsIgnoreCase(uri.getScheme())) {
                // Running from a packaged JAR: enumerate entries without relying on Zip FS
                try {
                    JarURLConnection conn = (JarURLConnection) resource.openConnection();
                    try (java.util.jar.JarFile jar = conn.getJarFile()) {
                        String q = (query == null ? "" : query).toLowerCase();

                        String mutablePrefix = conn.getEntryName();
                        if (mutablePrefix == null || mutablePrefix.isEmpty()) {
                            mutablePrefix = "knowledge_base";
                        }
                        if (!mutablePrefix.endsWith("/")) {
                            mutablePrefix = mutablePrefix + "/";
                        }
                        final String prefix = mutablePrefix;

                        String result = jar.stream()
                                .filter(e -> !e.isDirectory())
                                .map(java.util.jar.JarEntry::getName)
                                .filter(name -> name.startsWith(prefix))
                                .map(name -> {
                                    try (var in = getClass().getClassLoader().getResourceAsStream(name)) {
                                        if (in == null) return null;
                                        String content = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                                        return content.toLowerCase().contains(q)
                                                ? ("--- " + Paths.get(name).getFileName() + " ---" + content)
                                                : null;
                                    } catch (IOException ex) {
                                        logger.error("Error reading jar entry: {}", name, ex);
                                        return "Error reading file: " + name;
                                    }
                                })
                                .filter(Objects::nonNull)
                                .collect(Collectors.joining("\n\n"));

                        return result.isEmpty() ? "No relevant documents found for query: " + query : result;
                    }
                } catch (Exception e) {
                    logger.error("Error accessing knowledge_base within JAR", e);
                    return "Error: Could not access knowledge_base in JAR. " + e.getMessage();
                }
            } else {
                // Running from classes/resources on the filesystem
                basePath = Paths.get(uri);
            }

            String q = (query == null ? "" : query).toLowerCase();

            try (Stream<Path> paths = Files.walk(basePath)) {
                String result = paths
                        .filter(Files::isRegularFile)
                        .filter(path -> {
                            try (Stream<String> lines = Files.lines(path)) {
                                return lines.anyMatch(line -> line.toLowerCase().contains(q));
                            } catch (IOException e) {
                                logger.error("Error reading file: {}", path, e);
                                return false;
                            }
                        })
                        .map(path -> {
                            try {
                                return "--- " + path.getFileName().toString() + " ---" + Files.readString(path);
                            } catch (IOException e) {
                                return "Error reading file: " + path.getFileName().toString();
                            }
                        })
                        .collect(Collectors.joining("\n\n"));

                return result.isEmpty() ? "No relevant documents found for query: " + query : result;
            }
        } catch (Exception e) {
            logger.error("Error searching knowledge base", e);
            return "Error: Could not search knowledge base. " + e.getMessage();
        }
    }

    
}
