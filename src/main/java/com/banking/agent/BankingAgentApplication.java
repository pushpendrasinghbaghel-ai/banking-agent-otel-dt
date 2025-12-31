package com.banking.agent;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import io.github.cdimascio.dotenv.Dotenv;

/**
 * Main application class for the Banking AI Agent.
 * This application integrates multiple LLM providers (Gemini, OpenAI, Ollama)
 * and uses the Embabel Agent framework for building banking-focused AI agents.
 */
@SpringBootApplication
public class BankingAgentApplication {

    public static void main(String[] args) {
        // Load .env file and set as system properties for Spring to use
        loadEnvFile();
        SpringApplication.run(BankingAgentApplication.class, args);
    }

    /**
     * Load environment variables from .env file if it exists.
     * This allows local development with a .env file while still supporting
     * environment variables in production (Docker, Kubernetes, etc.)
     */
    private static void loadEnvFile() {
        try {
            Dotenv dotenv = Dotenv.configure()
                    .ignoreIfMissing()  // Don't fail if .env doesn't exist
                    .load();

            // Set each env variable as a system property so Spring can resolve ${VAR} placeholders
            dotenv.entries().forEach(entry -> {
                if (System.getProperty(entry.getKey()) == null && System.getenv(entry.getKey()) == null) {
                    System.setProperty(entry.getKey(), entry.getValue());
                }
            });

            System.out.println("Environment configuration loaded from .env file (if present)");
        } catch (Exception e) {
            System.out.println("No .env file found or error loading it - using system environment variables");
        }
    }
}
