package com.example.DockerSDKPractice;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.testcontainers.containers.Container;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

// Removed unused imports like File, IOException, LocalDateTime, BuildJob, BuildJobRepository
// Removed Transferable as it's less common now, MountableFile is preferred

@Service
public class BuildService { // Renamed from DockerInteractionService

    private static final Logger log = LoggerFactory.getLogger(BuildService.class);

    // No repository needed for this simple version
    public BuildService() {
        log.info("BuildService initialized.");
    }


    /**
     * Executes a build synchronously in a Docker container using Testcontainers.
     * This is called directly by SimpleBuildController.
     *
     * @param localZipPath Path to the temporary local zip file containing the project code.
     * @param projectType  Type of project (e.g., "MAVEN", "NPM", "PIP").
     * @return A string containing the build logs and status.
     */
    public String executeSimpleBuild(String localZipPath, String projectType) {
        log.info("Starting synchronous build for type: {}, file: {}", projectType, localZipPath);
        StringBuilder resultOutput = new StringBuilder();

        String buildImageName = "";
        // Define commands using an array for clarity with execInContainer
        String[] buildCommands = {};
        // We won't copy artifacts out in this simple synchronous version

        // --- 1. Determine Image and Commands ---
        try {
            // Use switch statement for clarity
            switch (projectType.toUpperCase()) {
                case "MAVEN":
                    // *** CHANGE IMAGE HERE ***
                    // Use the standard image, not -slim, as it includes unzip
                    buildImageName = "maven:3.8-openjdk-17";
                    // *** END CHANGE ***

                    // Sequence of commands executed via shell
                    buildCommands = new String[]{ "/bin/sh", "-c",
                            "mkdir -p /app/src && " + // Ensure src dir exists
                                    "cd /app && " +
                                    "unzip -o code.zip -d src && " + // Unzip into src directory
                                    "echo 'Unzipped code.' && " + // Log progress
                                    "rm code.zip && " +
                                    "cd src && " +
                                    // Find the directory containing pom.xml (might be current or one level down)
                                    "BUILD_DIR=$(find . -maxdepth 2 -name pom.xml -printf '%h' | head -n 1) && " +
                                    "cd \"$BUILD_DIR\" && " +
                                    "echo 'Building in directory: ' $(pwd) && " + // Log current dir
                                    "ls -la && " + // List files for debugging
                                    "mvn clean install -DskipTests" // Build, skip tests
                    };
                    break;

                case "NPM":
                    // Alpine images often lack unzip too. We might need to install it.
                    buildImageName = "node:20-alpine";
                    buildCommands = new String[]{ "/bin/sh", "-c",
                            "apk add --no-cache unzip && " + // Install unzip in Alpine
                                    "mkdir -p /app/src && cd /app && unzip -o code.zip -d src && rm code.zip && cd src && " +
                                    "echo 'Building in directory: ' $(pwd) && ls -la && " +
                                    "npm install && npm run build"
                    };
                    break;

                case "PIP": // Example for Python/pip
                    // Slim Debian images might also lack unzip.
                    buildImageName = "python:3.11-slim";
                    buildCommands = new String[]{ "/bin/sh", "-c",
                            "apt-get update && apt-get install -y --no-install-recommends unzip && rm -rf /var/lib/apt/lists/* && " + // Install unzip in Debian/Ubuntu slim
                                    "mkdir -p /app/src && cd /app && unzip -o code.zip -d src && rm code.zip && cd src && " +
                                    "BUILD_DIR=$(find . -maxdepth 2 -name requirements.txt -printf '%h' | head -n 1) && cd \"$BUILD_DIR\" && " +
                                    "echo 'Building in directory: ' $(pwd) && ls -la && " +
                                    "pip install --no-cache-dir -r requirements.txt"
                            // Add test/build command if needed: "&& python -m pytest"
                    };
                    break;

                default:
                    // Handle unknown type directly
                    log.error("Unsupported project type received: {}", projectType);
                    return "Error: Unsupported project type: " + projectType + ". Allowed types: MAVEN, NPM, PIP";
            }

            resultOutput.append("Using build image: ").append(buildImageName).append("\n");
            resultOutput.append("Attempting to run commands:\n")
                    .append(String.join(" ", buildCommands)).append("\n\n");


            // --- 2. Instantiate, Configure, Run Container ---
            log.info("Creating container with image: {}", buildImageName);
            // Use try-with-resources for automatic container cleanup
            try (GenericContainer<?> container = new GenericContainer<>(DockerImageName.parse(buildImageName))) {
                // Keep the container running in the background while we execute commands
                container.withCommand("sleep", "infinity");

                // Start the container first
                container.start();
                log.info("Container started: {}", container.getContainerId());

                // Copy the user's zip file into the container
                // MountableFile handles path resolution
                container.copyFileToContainer(MountableFile.forHostPath(localZipPath), "/app/code.zip");
                log.info("Copied code zip {} to /app/code.zip", localZipPath);

                // Execute the build commands defined earlier
                log.info("Executing build commands in container...");
                // Increase timeout if builds take longer, e.g., .withStartupTimeout(Duration.ofMinutes(5)) on container
                Container.ExecResult result = container.execInContainer(buildCommands);
                log.info("Build commands finished with exit code: {}", result.getExitCode());

                // Append logs to the final output string
                resultOutput.append("--- BUILD LOGS ---\n");
                resultOutput.append("Exit Code: ").append(result.getExitCode()).append("\n\n");
                resultOutput.append("--- STDOUT ---\n").append(result.getStdout()).append("\n\n");
                resultOutput.append("--- STDERR ---\n").append(result.getStderr()).append("\n");

                // Append final status
                if (result.getExitCode() == 0) {
                    resultOutput.append("\n--- BUILD STATUS: SUCCESS ---");
                    log.info("Synchronous build SUCCESSFUL for file {}", localZipPath);
                    // In this simple version, we don't copy artifacts out.
                } else {
                    resultOutput.append("\n--- BUILD STATUS: FAILED ---");
                    log.error("Synchronous build FAILED for file {}", localZipPath);
                }

            } // Container stops automatically here due to try-with-resources

        } catch (IllegalArgumentException iae) {
            // Catch specific error for unsupported type
            log.error("Configuration error: {}", iae.getMessage());
            resultOutput.append("\nConfiguration Error: ").append(iae.getMessage());
            return resultOutput.toString(); // Return error details
        } catch (Exception e) {
            // Catch general errors during container operations
            log.error("Error during build execution for file {}", localZipPath, e);
            resultOutput.append("\nExecution Error: ").append(e.getMessage());
            return resultOutput.toString(); // Return error details
        }

        log.info("Synchronous build finished for file {}.", localZipPath);
        return resultOutput.toString(); // Return the complete log string
    }
}

