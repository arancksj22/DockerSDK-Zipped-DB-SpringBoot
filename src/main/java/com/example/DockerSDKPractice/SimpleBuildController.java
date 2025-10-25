package com.example.DockerSDKPractice;

import org.apache.commons.io.FileUtils; // Using commons-io
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@RestController
@RequestMapping("/api/simple") // Using a different base path
public class SimpleBuildController {

    private static final Logger log = LoggerFactory.getLogger(SimpleBuildController.class);
    private final BuildService buildService; // Inject the BuildService
    private final Path tempDir; // Make final
    private static final Set<String> ALLOWED_PROJECT_TYPES = Stream.of("MAVEN", "NPM", "PIP")
            .collect(Collectors.toSet());

    public SimpleBuildController(BuildService buildService) {
        this.buildService = buildService;
        Path resolvedTempDir = Paths.get("./temp_builds/").toAbsolutePath().normalize(); // Resolve absolute path once
        this.tempDir = resolvedTempDir;
        try {
            Files.createDirectories(tempDir);
            log.info("Temporary build directory ensured at: {}", tempDir); // Log absolute path
        } catch (IOException e) {
            log.error("Could not create temp build directory: {}", tempDir, e);
            // Consider throwing a runtime exception here to prevent startup if dir fails
        }
    }

    /**
     * Endpoint to submit a zip file and synchronously run a build.
     * WARNING: This request will block until the build is complete.
     */
    @PostMapping("/build-sync")
    public ResponseEntity<String> runBuildSynchronously(
            @RequestParam("file") MultipartFile file,
            @RequestParam("projectType") String projectType) {

        log.info("Received synchronous build request for type: {}", projectType);

        // --- Input Validation ---
        if (file == null || file.isEmpty()) {
            return ResponseEntity.badRequest().contentType(MediaType.TEXT_PLAIN).body("Error: File cannot be empty");
        }
        if (projectType == null || projectType.isBlank()) {
            return ResponseEntity.badRequest().contentType(MediaType.TEXT_PLAIN).body("Error: Project type must be specified");
        }
        String upperProjectType = projectType.toUpperCase();
        if (!ALLOWED_PROJECT_TYPES.contains(upperProjectType)) {
            return ResponseEntity.badRequest().contentType(MediaType.TEXT_PLAIN).body("Error: Invalid project type. Allowed: " + ALLOWED_PROJECT_TYPES);
        }
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || !originalFilename.toLowerCase().endsWith(".zip")) {
            return ResponseEntity.badRequest().contentType(MediaType.TEXT_PLAIN).body("Error: Only .zip files are allowed (Original Filename: " + originalFilename + ")");
        }
        // --- End Validation ---

        Path tempFilePath = null;
        String tempFilename = UUID.randomUUID() + ".zip"; // Generate filename

        try {
            // --- Resolve and Validate Path ---
            // Resolve the final path for the temporary file
            tempFilePath = this.tempDir.resolve(tempFilename).normalize(); // No toAbsolutePath here yet

            // *** ADD LOGGING ***
            log.debug("Base Temp Dir (Absolute): {}", this.tempDir); // Already absolute from constructor
            log.debug("Resolved Temp File Path (Normalized): {}", tempFilePath);
            log.debug("Resolved Temp File Path (Absolute): {}", tempFilePath.toAbsolutePath());
            // *** END LOGGING ***


            // *** THE CHECK ***
            // Compare absolute paths to ensure the resolved file path is truly inside the base temp dir
            if (!tempFilePath.toAbsolutePath().startsWith(this.tempDir)) {
                log.error("Path traversal attempt detected!");
                log.error("Base Dir : {}", this.tempDir);
                log.error("Target File: {}", tempFilePath.toAbsolutePath());
                throw new IOException("Potential path traversal attempt");
            }
            // *** END CHECK ***

            // --- Save file temporarily ---
            log.info("Saving temp file to: {}", tempFilePath.toAbsolutePath());
            file.transferTo(tempFilePath.toFile()); // Save the actual file
            // --- End File Saving ---


            // --- Execute Build Synchronously ---
            // This call will block until the build service returns
            String buildResult = buildService.executeSimpleBuild(tempFilePath.toString(), upperProjectType);
            log.info("Synchronous build completed.");
            // --- End Build Execution ---

            // Return the logs/status
            return ResponseEntity.ok()
                    .contentType(MediaType.TEXT_PLAIN) // Ensure plain text response
                    .body(buildResult);

        } catch (IOException e) {
            log.error("Failed to store or process uploaded file: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().contentType(MediaType.TEXT_PLAIN)
                    .body("Error saving/processing file: " + e.getMessage());
        } catch (Exception e) {
            log.error("Unexpected error during synchronous build: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().contentType(MediaType.TEXT_PLAIN)
                    .body("Unexpected build error: " + e.getMessage());
        } finally {
            // --- Cleanup Temp File ---
            if (tempFilePath != null) {
                try {
                    // Use resolved absolute path for deletion
                    Files.deleteIfExists(tempFilePath.toAbsolutePath());
                    log.info("Deleted temp file: {}", tempFilePath.toAbsolutePath());
                } catch (IOException e) {
                    log.warn("Could not delete temporary file: {}", tempFilePath.toAbsolutePath(), e);
                }
            }
            // --- End Cleanup ---
        }
    }
}

