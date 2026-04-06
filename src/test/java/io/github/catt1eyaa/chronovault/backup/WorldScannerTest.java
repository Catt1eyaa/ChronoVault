package io.github.catt1eyaa.chronovault.backup;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for WorldScanner
 */
class WorldScannerTest {
    
    @Test
    void testRealWorldScan() throws IOException {
        // Scan the actual test world
        Path worldDir = Path.of("run/saves/New World");
        
        if (!Files.exists(worldDir)) {
            System.out.println("Test world not found, skipping real world scan test");
            return;
        }
        
        WorldFiles files = WorldScanner.scan(worldDir);
        
        // Print scan results
        System.out.println("\n=== World Scan Results ===");
        System.out.println("Regular files: " + files.regularFiles().size());
        files.regularFiles().keySet().stream()
            .sorted()
            .limit(10)
            .forEach(path -> System.out.println("  - " + path));
        if (files.regularFiles().size() > 10) {
            System.out.println("  ... and " + (files.regularFiles().size() - 10) + " more");
        }
        
        System.out.println("\nRegion files by dimension:");
        for (String dimension : files.getDimensions().stream().sorted().toList()) {
            Map<String, Path> dimFiles = files.regionFiles().get(dimension);
            System.out.println("  " + dimension + ": " + dimFiles.size() + " files");
            dimFiles.keySet().stream()
                .sorted()
                .forEach(path -> System.out.println("    - " + path));
        }
        
        System.out.println("\nTotal: " + files.getTotalFileCount() + " files");
        System.out.println("========================\n");
        
        // Verify level.dat exists
        assertTrue(files.regularFiles().containsKey("level.dat"), 
            "level.dat should be found");
        
        // Verify region files exist
        assertTrue(files.getRegionFileCount() > 0, 
            "Should find region files");
        
        // Verify overworld dimension exists
        assertTrue(files.getDimensions().contains("overworld"), 
            "Should find overworld dimension");
        
        // Verify session.lock is NOT included
        assertFalse(files.regularFiles().containsKey("session.lock"), 
            "session.lock should be excluded");
    }
    
    @Test
    void testDimensionRecognition(@TempDir Path tempDir) throws IOException {
        // Create test directory structure with different dimensions
        
        // Overworld
        createTestFile(tempDir, "region/r.0.0.mca");
        createTestFile(tempDir, "entities/r.0.0.mca");
        createTestFile(tempDir, "poi/r.0.0.mca");
        
        // Nether
        createTestFile(tempDir, "DIM-1/region/r.0.0.mca");
        createTestFile(tempDir, "DIM-1/entities/r.0.0.mca");
        createTestFile(tempDir, "DIM-1/poi/r.0.0.mca");
        
        // End
        createTestFile(tempDir, "DIM1/region/r.0.0.mca");
        createTestFile(tempDir, "DIM1/entities/r.0.0.mca");
        createTestFile(tempDir, "DIM1/poi/r.0.0.mca");
        
        // Custom dimension
        createTestFile(tempDir, "dimensions/minecraft/the_nether/region/r.0.0.mca");
        createTestFile(tempDir, "dimensions/twilightforest/twilight_forest/region/r.0.0.mca");
        createTestFile(tempDir, "dimensions/twilightforest/twilight_forest/poi/r.0.0.mca");
        
        WorldFiles files = WorldScanner.scan(tempDir);
        
        // Verify dimensions are recognized correctly
        assertTrue(files.getDimensions().contains("overworld"), 
            "Should recognize overworld");
        assertTrue(files.getDimensions().contains("nether"), 
            "Should recognize nether");
        assertTrue(files.getDimensions().contains("end"), 
            "Should recognize end");
        assertTrue(files.getDimensions().contains("minecraft:the_nether"), 
            "Should recognize custom dimension minecraft:the_nether");
        assertTrue(files.getDimensions().contains("twilightforest:twilight_forest"), 
            "Should recognize custom dimension twilightforest:twilight_forest");
        
        // Verify file counts
        assertEquals(3, files.regionFiles().get("overworld").size(),
            "Overworld should have 3 files");
        assertEquals(3, files.regionFiles().get("nether").size(),
            "Nether should have 3 files");
        assertEquals(3, files.regionFiles().get("end").size(),
            "End should have 3 files");
        assertEquals(1, files.regionFiles().get("minecraft:the_nether").size(),
            "minecraft:the_nether should have 1 file");
        assertEquals(2, files.regionFiles().get("twilightforest:twilight_forest").size(),
            "twilightforest:twilight_forest should have 2 files");
    }

    @Test
    void testUnmanagedMcaFilesAreNotClassifiedAsRegion(@TempDir Path tempDir) throws IOException {
        createTestFile(tempDir, "region/r.0.0.mca");
        createTestFile(tempDir, "data/custom.mca");
        createTestFile(tempDir, "custom/r.1.2.mca");

        WorldFiles files = WorldScanner.scan(tempDir);

        assertEquals(1, files.getRegionFileCount());
        assertTrue(files.regionFiles().get("overworld").containsKey("region/r.0.0.mca"));

        assertTrue(files.regularFiles().containsKey("data/custom.mca"));
        assertTrue(files.regularFiles().containsKey("custom/r.1.2.mca"));
    }

    @Test
    void testZeroByteEntitiesRegionIsIncluded(@TempDir Path tempDir) throws IOException {
        createTestFile(tempDir, "region/r.0.0.mca");

        Path entitiesFile = tempDir.resolve("entities/r.0.0.mca");
        Files.createDirectories(entitiesFile.getParent());
        Files.createFile(entitiesFile);

        WorldFiles files = WorldScanner.scan(tempDir);

        assertEquals(2, files.getRegionFileCount());
        assertTrue(files.regionFiles().get("overworld").containsKey("region/r.0.0.mca"));
        assertTrue(files.regionFiles().get("overworld").containsKey("entities/r.0.0.mca"));
        assertFalse(files.regularFiles().containsKey("entities/r.0.0.mca"));
    }

    @Test
    void testZeroBytePoiRegionIsIncluded(@TempDir Path tempDir) throws IOException {
        createTestFile(tempDir, "region/r.0.0.mca");

        Path poiFile = tempDir.resolve("poi/r.0.0.mca");
        Files.createDirectories(poiFile.getParent());
        Files.createFile(poiFile);

        WorldFiles files = WorldScanner.scan(tempDir);

        assertEquals(2, files.getRegionFileCount());
        assertTrue(files.regionFiles().get("overworld").containsKey("region/r.0.0.mca"));
        assertTrue(files.regionFiles().get("overworld").containsKey("poi/r.0.0.mca"));
        assertFalse(files.regularFiles().containsKey("poi/r.0.0.mca"));
    }
    
    @Test
    void testFileFiltering(@TempDir Path tempDir) throws IOException {
        // Create files that should be included
        createTestFile(tempDir, "level.dat");
        createTestFile(tempDir, "level.dat_old");
        createTestFile(tempDir, "playerdata/test.dat");
        createTestFile(tempDir, "stats/test.json");
        createTestFile(tempDir, "advancements/test.json");
        createTestFile(tempDir, "data/scoreboard.dat");
        createTestFile(tempDir, "region/r.0.0.mca");
        
        // Create files that should be excluded
        createTestFile(tempDir, "session.lock");
        createTestFile(tempDir, "temp.tmp");
        createTestFile(tempDir, "test.temp");
        createTestFile(tempDir, "region/r.0.0.mca.tmp");
        createTestFile(tempDir, "playerdata/test.dat_old");
        createTestFile(tempDir, ".hidden");
        
        WorldFiles files = WorldScanner.scan(tempDir);
        
        // Verify included files
        assertTrue(files.regularFiles().containsKey("level.dat"));
        assertTrue(files.regularFiles().containsKey("level.dat_old"));
        assertTrue(files.regularFiles().containsKey("playerdata/test.dat"));
        assertTrue(files.regularFiles().containsKey("stats/test.json"));
        assertTrue(files.regularFiles().containsKey("advancements/test.json"));
        assertTrue(files.regularFiles().containsKey("data/scoreboard.dat"));
        
        // Verify excluded files
        assertFalse(files.regularFiles().containsKey("session.lock"));
        assertFalse(files.regularFiles().containsKey("temp.tmp"));
        assertFalse(files.regularFiles().containsKey("test.temp"));
        assertFalse(files.regularFiles().containsKey("playerdata/test.dat_old"));
        assertFalse(files.regularFiles().containsKey(".hidden"));
        
        // Region files should be separate
        assertEquals(1, files.getRegionFileCount());
        assertFalse(files.regionFiles().get("overworld").containsKey("region/r.0.0.mca.tmp"));
    }
    
    @Test
    void testRelativePaths(@TempDir Path tempDir) throws IOException {
        // Create nested directory structure
        createTestFile(tempDir, "level.dat");
        createTestFile(tempDir, "playerdata/abc-123.dat");
        createTestFile(tempDir, "data/scoreboard.dat");
        createTestFile(tempDir, "region/r.0.0.mca");
        createTestFile(tempDir, "DIM-1/region/r.0.0.mca");
        
        WorldFiles files = WorldScanner.scan(tempDir);
        
        // Verify paths are relative and use forward slashes
        assertTrue(files.regularFiles().containsKey("level.dat"));
        assertTrue(files.regularFiles().containsKey("playerdata/abc-123.dat"));
        assertTrue(files.regularFiles().containsKey("data/scoreboard.dat"));
        
        // Verify region files also use relative paths
        assertTrue(files.regionFiles().get("overworld").containsKey("region/r.0.0.mca"));
        assertTrue(files.regionFiles().get("nether").containsKey("DIM-1/region/r.0.0.mca"));
        
        // Verify no paths contain backslashes (Windows compatibility)
        for (String path : files.regularFiles().keySet()) {
            assertFalse(path.contains("\\"), 
                "Path should use forward slashes: " + path);
        }
    }
    
    @Test
    void testEmptyWorld(@TempDir Path tempDir) throws IOException {
        // Scan empty directory
        WorldFiles files = WorldScanner.scan(tempDir);
        
        // Should return empty results without error
        assertEquals(0, files.getTotalFileCount());
        assertEquals(0, files.getRegionFileCount());
        assertTrue(files.getDimensions().isEmpty());
    }
    
    @Test
    void testStatistics(@TempDir Path tempDir) throws IOException {
        // Create test files
        createTestFile(tempDir, "level.dat");
        createTestFile(tempDir, "level.dat_old");
        createTestFile(tempDir, "playerdata/test.dat");
        createTestFile(tempDir, "region/r.0.0.mca");
        createTestFile(tempDir, "region/r.0.1.mca");
        createTestFile(tempDir, "entities/r.0.0.mca");
        createTestFile(tempDir, "DIM-1/region/r.0.0.mca");
        createTestFile(tempDir, "DIM1/region/r.0.0.mca");
        
        WorldFiles files = WorldScanner.scan(tempDir);
        
        // Verify statistics
        assertEquals(3, files.regularFiles().size(), 
            "Should have 3 regular files");
        assertEquals(5, files.getRegionFileCount(), 
            "Should have 5 region files");
        assertEquals(8, files.getTotalFileCount(), 
            "Should have 8 total files");
        assertEquals(3, files.getDimensions().size(), 
            "Should have 3 dimensions");
    }
    
    @Test
    void testNonExistentDirectory() {
        Path nonExistent = Path.of("non/existent/path");
        
        assertThrows(IOException.class, () -> WorldScanner.scan(nonExistent),
            "Should throw IOException for non-existent directory");
    }
    
    @Test
    void testFileAsDirectory(@TempDir Path tempDir) throws IOException {
        // Create a regular file
        Path file = tempDir.resolve("test.txt");
        Files.writeString(file, "test");
        
        // Try to scan a file as if it were a directory
        assertThrows(IOException.class, () -> WorldScanner.scan(file),
            "Should throw IOException when path is not a directory");
    }
    
    // Helper method to create test files with parent directories
    private void createTestFile(Path baseDir, String relativePath) throws IOException {
        Path filePath = baseDir.resolve(relativePath);
        Files.createDirectories(filePath.getParent());
        Files.writeString(filePath, "test content");
    }
}
