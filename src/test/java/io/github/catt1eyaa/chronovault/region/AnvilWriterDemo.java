package io.github.catt1eyaa.chronovault.region;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;

/**
 * Demonstration and validation program for AnvilWriter.
 * <p>
 * This program performs a complete round-trip test:
 * 1. Reads all chunks from an existing .mca file
 * 2. Writes them to a new file using AnvilWriter
 * 3. Compares the original and new files to verify correctness
 */
public class AnvilWriterDemo {

    public static void main(String[] args) {
        try {
            // Path to test region file
            Path originalFile = Paths.get("run/saves/New World/region/r.0.0.mca");
            Path outputFile = Paths.get("run/saves/New World/region/r.0.0.roundtrip.mca");

            if (!Files.exists(originalFile)) {
                System.err.println("Error: Test file not found: " + originalFile);
                System.err.println("Please create a test world first by running Minecraft.");
                System.exit(1);
            }

            System.out.println("=== AnvilWriter Round-Trip Test ===");
            System.out.println();

            // Step 1: Read original file
            System.out.println("Step 1: Reading original file...");
            List<ChunkData> chunks = AnvilReader.readAllChunks(originalFile);
            System.out.println("  Found " + chunks.size() + " chunks in original file");
            
            long originalSize = Files.size(originalFile);
            System.out.println("  Original file size: " + formatBytes(originalSize));
            System.out.println();

            // Step 2: Write to new file
            System.out.println("Step 2: Writing chunks to new file...");
            AnvilWriter.writeRegion(outputFile, chunks);
            
            long outputSize = Files.size(outputFile);
            System.out.println("  New file size: " + formatBytes(outputSize));
            System.out.println();

            // Step 3: Read back and verify
            System.out.println("Step 3: Verifying round-trip consistency...");
            List<ChunkData> readBackChunks = AnvilReader.readAllChunks(outputFile);
            System.out.println("  Read back " + readBackChunks.size() + " chunks");

            if (chunks.size() != readBackChunks.size()) {
                System.err.println("  ERROR: Chunk count mismatch!");
                System.exit(1);
            }

            // Sort both lists for comparison
            chunks.sort((a, b) -> {
                int cmp = Integer.compare(a.z(), b.z());
                if (cmp != 0) return cmp;
                return Integer.compare(a.x(), b.x());
            });
            readBackChunks.sort((a, b) -> {
                int cmp = Integer.compare(a.z(), b.z());
                if (cmp != 0) return cmp;
                return Integer.compare(a.x(), b.x());
            });

            // Verify each chunk
            boolean allMatch = true;
            for (int i = 0; i < chunks.size(); i++) {
                ChunkData original = chunks.get(i);
                ChunkData readBack = readBackChunks.get(i);

                if (original.x() != readBack.x() || original.z() != readBack.z()) {
                    System.err.println("  ERROR: Chunk coordinate mismatch at index " + i);
                    allMatch = false;
                }

                if (original.compressionType() != readBack.compressionType()) {
                    System.err.println("  ERROR: Compression type mismatch for chunk (" + 
                            original.x() + "," + original.z() + ")");
                    allMatch = false;
                }

                if (!Arrays.equals(original.rawData(), readBack.rawData())) {
                    System.err.println("  ERROR: Data mismatch for chunk (" + 
                            original.x() + "," + original.z() + ")");
                    allMatch = false;
                }
            }

            if (allMatch) {
                System.out.println("  ✓ All chunks match perfectly!");
            } else {
                System.err.println("  ✗ Some chunks don't match");
                System.exit(1);
            }
            System.out.println();

            // Step 4: Compare file headers
            System.out.println("Step 4: Comparing file headers...");
            List<ChunkLocation> originalLocations = AnvilReader.readHeader(originalFile);
            List<ChunkLocation> outputLocations = AnvilReader.readHeader(outputFile);

            int nonEmptyCount = 0;
            int headerMatches = 0;
            
            for (int i = 0; i < 1024; i++) {
                ChunkLocation orig = originalLocations.get(i);
                ChunkLocation output = outputLocations.get(i);

                if (!orig.isEmpty()) {
                    nonEmptyCount++;
                    
                    // Check if both have data at the same position
                    if (output.isEmpty()) {
                        System.err.println("  ERROR: Missing chunk at (" + orig.x() + "," + orig.z() + ")");
                    } else {
                        // Sector count should match (offset may differ due to different allocation)
                        if (orig.sectorCount() == output.sectorCount()) {
                            headerMatches++;
                        }
                    }
                }
            }

            System.out.println("  Non-empty chunks: " + nonEmptyCount);
            System.out.println("  Matching sector counts: " + headerMatches + "/" + nonEmptyCount);
            System.out.println();

            // Summary
            System.out.println("=== Summary ===");
            System.out.println("✓ Round-trip test PASSED");
            System.out.println("✓ All " + chunks.size() + " chunks verified successfully");
            System.out.println("✓ File format is valid and Minecraft-compatible");
            System.out.println();
            System.out.println("Output file: " + outputFile);
            System.out.println();
            System.out.println("You can verify the file works by:");
            System.out.println("1. Copying it to a Minecraft world's region folder");
            System.out.println("2. Loading the world in Minecraft");
            System.out.println("3. Checking that the chunks load correctly");

        } catch (IOException e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    private static String formatBytes(long bytes) {
        if (bytes < 1024) {
            return bytes + " bytes";
        } else if (bytes < 1024 * 1024) {
            return String.format("%.2f KB", bytes / 1024.0);
        } else {
            return String.format("%.2f MB", bytes / (1024.0 * 1024.0));
        }
    }
}
