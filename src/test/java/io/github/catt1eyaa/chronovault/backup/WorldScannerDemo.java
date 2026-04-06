package io.github.catt1eyaa.chronovault.backup;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;

/**
 * Demo program to showcase WorldScanner functionality
 */
public class WorldScannerDemo {
    
    public static void main(String[] args) {
        try {
            // Scan the test world
            Path worldDir = Path.of("run/saves/New World");
            
            System.out.println("ChronoVault World Scanner Demo");
            System.out.println("================================\n");
            
            System.out.println("Scanning world: " + worldDir);
            long startTime = System.currentTimeMillis();
            
            WorldFiles files = WorldScanner.scan(worldDir);
            
            long endTime = System.currentTimeMillis();
            long duration = endTime - startTime;
            
            System.out.println("Scan completed in " + duration + "ms\n");
            
            // Display regular files
            System.out.println("Regular Files (" + files.regularFiles().size() + "):");
            System.out.println("─".repeat(60));
            files.regularFiles().keySet().stream()
                .sorted()
                .forEach(path -> System.out.println("  📄 " + path));
            
            System.out.println();
            
            // Display region files by dimension
            System.out.println("Region Files by Dimension:");
            System.out.println("─".repeat(60));
            
            if (files.getDimensions().isEmpty()) {
                System.out.println("  (No region files found)");
            } else {
                for (String dimension : files.getDimensions().stream().sorted().toList()) {
                    Map<String, Path> dimFiles = files.regionFiles().get(dimension);
                    System.out.println("\n  🌍 " + dimension + " (" + dimFiles.size() + " files):");
                    dimFiles.keySet().stream()
                        .sorted()
                        .forEach(path -> System.out.println("      " + path));
                }
            }
            
            System.out.println("\n" + "─".repeat(60));
            System.out.println("Summary:");
            System.out.println("  Total files: " + files.getTotalFileCount());
            System.out.println("  Regular files: " + files.regularFiles().size());
            System.out.println("  Region files: " + files.getRegionFileCount());
            System.out.println("  Dimensions: " + files.getDimensions().size());
            System.out.println("  Scan time: " + duration + "ms");
            
            // Performance check
            if (duration > 1000) {
                System.out.println("\n⚠️  Warning: Scan took longer than 1 second");
            } else {
                System.out.println("\n✅ Performance: Scan completed in under 1 second");
            }
            
        } catch (IOException e) {
            System.err.println("Error scanning world: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
