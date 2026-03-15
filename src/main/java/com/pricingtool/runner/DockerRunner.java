package com.pricingtool.runner;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;

public class DockerRunner {

    public static void main(String[] args) {
        try {
            System.out.println("========================================");
            System.out.println("  PricingTool - Docker Runner - Docker Runner");
            System.out.println("========================================");

            System.out.println("\n[Step 1] Building Docker image...");
            int buildResult = runCommand("docker", "compose", "build", "test-runner");
            if (buildResult != 0) {
                System.err.println("Docker build failed with exit code: " + buildResult);
                System.exit(1);
            }
            System.out.println("Docker image built successfully.\n");

            System.out.println("[Step 2] Running tests in Docker container (headless mode)...");
            int testResult = runCommand("docker", "compose", "run", "--rm", "test-runner");

            System.out.println("\n========================================");
            if (testResult == 0) {
                System.out.println("  All tests PASSED");
            } else {
                System.out.println("  Tests FAILED (exit code: " + testResult + ")");
            }
            System.out.println("========================================");

            System.exit(testResult);

        } catch (Exception e) {
            System.err.println("Error running Docker tests: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    private static int runCommand(String... command) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.directory(findProjectRoot());
        pb.redirectErrorStream(true);

        Process process = pb.start();

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                System.out.println(line);
            }
        }

        return process.waitFor();
    }

    private static File findProjectRoot() {
        File dir = new File(System.getProperty("user.dir"));
        while (dir != null && !new File(dir, "docker-compose.yml").exists()) {
            dir = dir.getParentFile();
        }
        if (dir == null) {
            throw new RuntimeException(
                    "Could not find project root (no docker-compose.yml found)");
        }
        return dir;
    }
}
