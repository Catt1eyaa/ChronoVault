package io.github.catt1eyaa.chronovault.backup;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * Demo test that showcases WorldScanner functionality
 */
class WorldScannerDemoTest {
    
    @Test
    void demonstrateWorldScanner() throws IOException {
        // Scan the test world
        Path worldDir = Path.of("run/saves/New World");
        
        if (!Files.exists(worldDir)) {
            System.out.println("Test world not found, skipping demo");
            return;
        }
        
        System.out.println("\n" + "=".repeat(70));
        System.out.println("ChronoVault World Scanner Demo");
        System.out.println("=".repeat(70) + "\n");
        
        System.out.println("Scanning world: " + worldDir);
        long startTime = System.currentTimeMillis();
        
        WorldFiles files = WorldScanner.scan(worldDir);
        
        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;
        
        System.out.println("Scan completed in " + duration + "ms\n");
        
        // Display regular files
        System.out.println("Regular Files (" + files.regularFiles().size() + "):");
        System.out.println("-".repeat(70));
        files.regularFiles().keySet().stream()
            .sorted()
            .forEach(path -> System.out.println("  - " + path));
        
        System.out.println();
        
        // Display region files by dimension
        System.out.println("Region Files by Dimension:");
        System.out.println("-".repeat(70));
        
        if (files.getDimensions().isEmpty()) {
            System.out.println("  (No region files found)");
        } else {
            for (String dimension : files.getDimensions().stream().sorted().toList()) {
                Map<String, Path> dimFiles = files.regionFiles().get(dimension);
                System.out.println("\n  Dimension: " + dimension + " (" + dimFiles.size() + " files)");
                dimFiles.keySet().stream()
                    .sorted()
                    .forEach(path -> System.out.println("    - " + path));
            }
        }
        
        System.out.println("\n" + "-".repeat(70));
        System.out.println("Summary:");
        System.out.println("  Total files: " + files.getTotalFileCount());
        System.out.println("  Regular files: " + files.regularFiles().size());
        System.out.println("  Region files: " + files.getRegionFileCount());
        System.out.println("  Dimensions: " + files.getDimensions().size());
        System.out.println("  Scan time: " + duration + "ms");
        
        // Performance check
        if (duration > 1000) {
            System.out.println("\n  Warning: Scan took longer than 1 second");
        } else {
            System.out.println("\n  Performance: OK - Scan completed in under 1 second");
        }
        
        System.out.println("\n" + "=".repeat(70) + "\n");
    }
}
