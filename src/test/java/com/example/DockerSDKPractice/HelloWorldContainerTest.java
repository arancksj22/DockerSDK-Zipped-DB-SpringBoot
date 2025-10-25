package com.example.DockerSDKPractice;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers // Enables Testcontainers JUnit 5 support
public class HelloWorldContainerTest {

    private static final Logger log = LoggerFactory.getLogger(HelloWorldContainerTest.class);

    // Define the container using the hello-world image
    // The 'withLogConsumer' will automatically capture logs
    @Container
    public GenericContainer<?> helloWorldContainer = new GenericContainer<>(DockerImageName.parse("hello-world:latest"))
            .withLogConsumer(new Slf4jLogConsumer(log).withPrefix("HELLO-WORLD")); // Log output with a prefix

    @Test
    void testHelloWorldRuns() {
        // Testcontainers automatically starts the container before the test
        // and stops it afterwards.

        // The hello-world container runs and exits quickly.
        // We just need to check if it started and logged something.

        // Get the logs captured by the consumer (you might need to wait briefly)
        String logs = helloWorldContainer.getLogs();

        log.info("--- Captured Logs ---");
        log.info(logs);
        log.info("--- End Logs ---");


        // Assert that the container ran and produced the expected output
        assertTrue(helloWorldContainer.isRunning() || helloWorldContainer.getContainerInfo() != null, "Container should have run");
        assertTrue(logs.contains("Hello from Docker!"), "Logs should contain the hello message");

        log.info("Test finished successfully!");
    }
}

