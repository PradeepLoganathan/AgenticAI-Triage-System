package com.example.sample.application;

import akka.javasdk.agent.Agent;
import akka.javasdk.annotations.Component;
import akka.javasdk.annotations.FunctionTool;
import akka.javasdk.annotations.Description;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Component(id = "knowledge-base-agent")
public class KnowledgeBaseAgent extends Agent {

    private static final Logger logger = LoggerFactory.getLogger(KnowledgeBaseAgent.class);

    public Effect<String> search(String query) {
        return effects().reply(searchKnowledgeBase(query));
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

        try (Stream<Path> paths = Files.walk(Paths.get(resource.toURI()))) {
            String result = paths
                    .filter(Files::isRegularFile)
                    .filter(path -> {
                        try {
                            return Files.lines(path).anyMatch(line -> line.toLowerCase().contains(query.toLowerCase()));
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
        } catch (Exception e) {
            logger.error("Error searching knowledge base", e);
            return "Error: Could not search knowledge base. " + e.getMessage();
        }
    }

    
}
