package io.github.catt1eyaa.chronovault.snapshot;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@DisplayName("Manifest Data Model Tests")
class ManifestTest {

    // ==================== ChunkEntry Tests ====================
    
    @Test
    @DisplayName("ChunkEntry: Valid chunk with hash")
    void testChunkEntryValid() {
        String hash = "3e25960a79dbc69b674cd4ec67a72c623e25960a79dbc69b674cd4ec67a72c62";
        ChunkEntry chunk = new ChunkEntry(0, 0, hash);
        
        assertEquals(0, chunk.x());
        assertEquals(0, chunk.z());
        assertEquals(hash, chunk.hash());
        assertFalse(chunk.isEmpty());
    }
    
    @Test
    @DisplayName("ChunkEntry: Empty chunk (null hash)")
    void testChunkEntryEmpty() {
        ChunkEntry chunk = new ChunkEntry(15, 20, null);
        
        assertEquals(15, chunk.x());
        assertEquals(20, chunk.z());
        assertNull(chunk.hash());
        assertTrue(chunk.isEmpty());
    }
    
    @Test
    @DisplayName("ChunkEntry: Coordinate range validation (0-31)")
    void testChunkEntryCoordinateRange() {
        // Valid boundaries
        assertDoesNotThrow(() -> new ChunkEntry(0, 0, null));
        assertDoesNotThrow(() -> new ChunkEntry(31, 31, null));
        assertDoesNotThrow(() -> new ChunkEntry(16, 16, null));
        
        // Invalid x coordinate
        Exception ex1 = assertThrows(IllegalArgumentException.class, 
            () -> new ChunkEntry(-1, 0, null));
        assertTrue(ex1.getMessage().contains("Chunk x must be 0-31"));
        
        Exception ex2 = assertThrows(IllegalArgumentException.class, 
            () -> new ChunkEntry(32, 0, null));
        assertTrue(ex2.getMessage().contains("Chunk x must be 0-31"));
        
        // Invalid z coordinate
        Exception ex3 = assertThrows(IllegalArgumentException.class, 
            () -> new ChunkEntry(0, -1, null));
        assertTrue(ex3.getMessage().contains("Chunk z must be 0-31"));
        
        Exception ex4 = assertThrows(IllegalArgumentException.class, 
            () -> new ChunkEntry(0, 32, null));
        assertTrue(ex4.getMessage().contains("Chunk z must be 0-31"));
    }
    
    @Test
    @DisplayName("ChunkEntry: Hash format validation (64 hex chars)")
    void testChunkEntryHashValidation() {
        // Valid hash
        String validHash = "a".repeat(64);
        assertDoesNotThrow(() -> new ChunkEntry(0, 0, validHash));
        
        // Invalid: too short
        Exception ex1 = assertThrows(IllegalArgumentException.class,
            () -> new ChunkEntry(0, 0, "abc123"));
        assertTrue(ex1.getMessage().contains("Invalid hash format"));
        
        // Invalid: too long
        Exception ex2 = assertThrows(IllegalArgumentException.class,
            () -> new ChunkEntry(0, 0, "a".repeat(65)));
        assertTrue(ex2.getMessage().contains("Invalid hash format"));
        
        // Invalid: contains uppercase
        Exception ex3 = assertThrows(IllegalArgumentException.class,
            () -> new ChunkEntry(0, 0, "A".repeat(64)));
        assertTrue(ex3.getMessage().contains("Invalid hash format"));
        
        // Invalid: contains non-hex chars
        Exception ex4 = assertThrows(IllegalArgumentException.class,
            () -> new ChunkEntry(0, 0, "z".repeat(64)));
        assertTrue(ex4.getMessage().contains("Invalid hash format"));
        
        // Valid: null is allowed
        assertDoesNotThrow(() -> new ChunkEntry(0, 0, null));
    }

    // ==================== RegionEntry Tests ====================
    
    @Test
    @DisplayName("RegionEntry: Valid region with chunks")
    void testRegionEntryValid() {
        List<ChunkEntry> chunks = List.of(
            new ChunkEntry(0, 0, "a".repeat(64)),
            new ChunkEntry(1, 0, "b".repeat(64)),
            new ChunkEntry(2, 0, null)  // empty chunk
        );
        
        RegionEntry region = new RegionEntry("r.0.0.mca", "anvil", chunks);
        
        assertEquals("r.0.0.mca", region.filename());
        assertEquals("anvil", region.format());
        assertEquals(3, region.chunks().size());
        assertEquals(2, region.getNonEmptyChunkCount());
    }
    
    @Test
    @DisplayName("RegionEntry: Default format is 'anvil'")
    void testRegionEntryDefaultFormat() {
        RegionEntry region = new RegionEntry("r.0.0.mca", null, List.of());
        assertEquals("anvil", region.format());
    }
    
    @Test
    @DisplayName("RegionEntry: Null chunks becomes empty list")
    void testRegionEntryNullChunks() {
        RegionEntry region = new RegionEntry("r.0.0.mca", "anvil", null);
        assertNotNull(region.chunks());
        assertEquals(0, region.chunks().size());
        assertEquals(0, region.getNonEmptyChunkCount());
    }
    
    @Test
    @DisplayName("RegionEntry: Filename validation")
    void testRegionEntryFilenameValidation() {
        // Valid filenames
        assertDoesNotThrow(() -> new RegionEntry("r.0.0.mca", "anvil", List.of()));
        assertDoesNotThrow(() -> new RegionEntry("r.-1.-1.mca", "anvil", List.of()));
        
        // Invalid: null filename
        Exception ex1 = assertThrows(IllegalArgumentException.class,
            () -> new RegionEntry(null, "anvil", List.of()));
        assertTrue(ex1.getMessage().contains("Filename cannot be null or empty"));
        
        // Invalid: empty filename
        Exception ex2 = assertThrows(IllegalArgumentException.class,
            () -> new RegionEntry("", "anvil", List.of()));
        assertTrue(ex2.getMessage().contains("Filename cannot be null or empty"));
    }
    
    @Test
    @DisplayName("RegionEntry: Max 1024 chunks validation")
    void testRegionEntryMaxChunks() {
        // Create exactly 1024 chunks
        List<ChunkEntry> chunks1024 = new ArrayList<>();
        for (int i = 0; i < 1024; i++) {
            chunks1024.add(new ChunkEntry(i % 32, i / 32, null));
        }
        
        assertDoesNotThrow(() -> new RegionEntry("r.0.0.mca", "anvil", chunks1024));
        
        // Create 1025 chunks - should fail
        List<ChunkEntry> chunks1025 = new ArrayList<>(chunks1024);
        chunks1025.add(new ChunkEntry(0, 0, null));
        
        Exception ex = assertThrows(IllegalArgumentException.class,
            () -> new RegionEntry("r.0.0.mca", "anvil", chunks1025));
        assertTrue(ex.getMessage().contains("Region cannot have more than 1024 chunks"));
    }
    
    @Test
    @DisplayName("RegionEntry: getNonEmptyChunkCount works correctly")
    void testRegionEntryNonEmptyCount() {
        List<ChunkEntry> chunks = List.of(
            new ChunkEntry(0, 0, "a".repeat(64)),
            new ChunkEntry(1, 0, null),
            new ChunkEntry(2, 0, "b".repeat(64)),
            new ChunkEntry(3, 0, null),
            new ChunkEntry(4, 0, "c".repeat(64))
        );
        
        RegionEntry region = new RegionEntry("r.0.0.mca", "anvil", chunks);
        assertEquals(5, region.chunks().size());
        assertEquals(3, region.getNonEmptyChunkCount());
    }

    // ==================== Manifest Tests ====================
    
    @Test
    @DisplayName("Manifest: Create complete manifest")
    void testManifestCreation() {
        Map<String, String> files = Map.of(
            "level.dat", "d41d8cd98f00b204e9800998ecf8427ed41d8cd98f00b204e9800998ecf8427e",
            "playerdata/uuid.dat", "9e107d9d372bb6826bd81d3542a419d69e107d9d372bb6826bd81d3542a419d6"
        );
        
        List<ChunkEntry> chunks = List.of(
            new ChunkEntry(0, 0, "a".repeat(64)),
            new ChunkEntry(1, 0, "b".repeat(64))
        );
        
        RegionEntry region = new RegionEntry("r.0.0.mca", "anvil", chunks);
        
        Map<String, Map<String, RegionEntry>> regions = Map.of(
            "overworld", Map.of("r.0.0.mca", region),
            "nether", Map.of()
        );
        
        Manifest manifest = new Manifest(
            "20260403_120000",
            1775198400L,
            "1.21.1",
            "Before fighting the Ender Dragon",
            files,
            regions
        );
        
        assertEquals("20260403_120000", manifest.snapshotId());
        assertEquals(1775198400L, manifest.timestamp());
        assertEquals("1.21.1", manifest.gameVersion());
        assertEquals("Before fighting the Ender Dragon", manifest.description());
        assertEquals(2, manifest.files().size());
        assertEquals(2, manifest.regions().size());
    }
    
    @Test
    @DisplayName("Manifest: Snapshot ID validation")
    void testManifestSnapshotIdValidation() {
        // Valid
        assertDoesNotThrow(() -> new Manifest(
            "20260403_120000", 1000L, "1.21.1", null, Map.of(), Map.of()
        ));
        
        // Invalid: null
        Exception ex1 = assertThrows(IllegalArgumentException.class,
            () -> new Manifest(null, 1000L, "1.21.1", null, Map.of(), Map.of()));
        assertTrue(ex1.getMessage().contains("Snapshot ID cannot be null or empty"));
        
        // Invalid: empty
        Exception ex2 = assertThrows(IllegalArgumentException.class,
            () -> new Manifest("", 1000L, "1.21.1", null, Map.of(), Map.of()));
        assertTrue(ex2.getMessage().contains("Snapshot ID cannot be null or empty"));
    }
    
    @Test
    @DisplayName("Manifest: Timestamp validation")
    void testManifestTimestampValidation() {
        // Valid
        assertDoesNotThrow(() -> new Manifest(
            "test", 1L, "1.21.1", null, Map.of(), Map.of()
        ));
        
        // Invalid: zero
        Exception ex1 = assertThrows(IllegalArgumentException.class,
            () -> new Manifest("test", 0L, "1.21.1", null, Map.of(), Map.of()));
        assertTrue(ex1.getMessage().contains("Timestamp must be positive"));
        
        // Invalid: negative
        Exception ex2 = assertThrows(IllegalArgumentException.class,
            () -> new Manifest("test", -1L, "1.21.1", null, Map.of(), Map.of()));
        assertTrue(ex2.getMessage().contains("Timestamp must be positive"));
    }
    
    @Test
    @DisplayName("Manifest: Null maps become empty maps")
    void testManifestNullMaps() {
        Manifest manifest = new Manifest(
            "test", 1000L, "1.21.1", null, null, null
        );
        
        assertNotNull(manifest.files());
        assertNotNull(manifest.regions());
        assertEquals(0, manifest.files().size());
        assertEquals(0, manifest.regions().size());
    }
    
    @Test
    @DisplayName("Manifest: generateSnapshotId generates correct format")
    void testManifestGenerateSnapshotId() {
        // Test with a known timestamp: 2026-04-03 12:00:00 UTC
        // Note: Result depends on system timezone
        long timestamp = 1775198400L;
        String snapshotId = Manifest.generateSnapshotId(timestamp);
        
        // Should match pattern YYYYMMDD_HHmmss
        assertTrue(snapshotId.matches("\\d{8}_\\d{6}"), 
            "Generated ID should match pattern YYYYMMDD_HHmmss: " + snapshotId);
        
        // Should contain the date portion (year and month should be recognizable)
        assertTrue(snapshotId.startsWith("2026"), 
            "Should be year 2026: " + snapshotId);
    }
    
    @Test
    @DisplayName("Manifest: getAllReferencedHashes includes all hashes")
    void testManifestGetAllReferencedHashes() {
        String hash1 = "a".repeat(64);
        String hash2 = "b".repeat(64);
        String hash3 = "c".repeat(64);
        String hash4 = "d".repeat(64);
        
        Map<String, String> files = Map.of(
            "level.dat", hash1,
            "session.lock", hash2
        );
        
        List<ChunkEntry> chunks = List.of(
            new ChunkEntry(0, 0, hash3),
            new ChunkEntry(1, 0, null),  // empty chunk
            new ChunkEntry(2, 0, hash4)
        );
        
        RegionEntry region = new RegionEntry("r.0.0.mca", "anvil", chunks);
        Map<String, Map<String, RegionEntry>> regions = Map.of(
            "overworld", Map.of("r.0.0.mca", region)
        );
        
        Manifest manifest = new Manifest(
            "test", 1000L, "1.21.1", null, files, regions
        );
        
        Set<String> hashes = manifest.getAllReferencedHashes();
        
        assertEquals(4, hashes.size());
        assertTrue(hashes.contains(hash1));
        assertTrue(hashes.contains(hash2));
        assertTrue(hashes.contains(hash3));
        assertTrue(hashes.contains(hash4));
    }
    
    @Test
    @DisplayName("Manifest: getAllReferencedHashes handles duplicates")
    void testManifestGetAllReferencedHashesDuplicates() {
        String sharedHash = "a".repeat(64);
        
        Map<String, String> files = Map.of(
            "level.dat", sharedHash
        );
        
        List<ChunkEntry> chunks = List.of(
            new ChunkEntry(0, 0, sharedHash),
            new ChunkEntry(1, 0, sharedHash)
        );
        
        RegionEntry region = new RegionEntry("r.0.0.mca", "anvil", chunks);
        Map<String, Map<String, RegionEntry>> regions = Map.of(
            "overworld", Map.of("r.0.0.mca", region)
        );
        
        Manifest manifest = new Manifest(
            "test", 1000L, "1.21.1", null, files, regions
        );
        
        Set<String> hashes = manifest.getAllReferencedHashes();
        
        // Should have only 1 unique hash
        assertEquals(1, hashes.size());
        assertTrue(hashes.contains(sharedHash));
    }
    
    @Test
    @DisplayName("Manifest: getStats calculates correctly")
    void testManifestGetStats() {
        String hash1 = "a".repeat(64);
        String hash2 = "b".repeat(64);
        String hash3 = "c".repeat(64);
        
        Map<String, String> files = Map.of(
            "level.dat", hash1,
            "session.lock", hash2
        );
        
        // Overworld: 5 chunks (3 non-empty)
        List<ChunkEntry> overworldChunks = List.of(
            new ChunkEntry(0, 0, hash3),
            new ChunkEntry(1, 0, null),
            new ChunkEntry(2, 0, hash3),  // duplicate hash
            new ChunkEntry(3, 0, null),
            new ChunkEntry(4, 0, hash3)   // duplicate hash
        );
        
        // Nether: 3 chunks (2 non-empty)
        List<ChunkEntry> netherChunks = List.of(
            new ChunkEntry(0, 0, hash1),  // duplicate with files
            new ChunkEntry(1, 0, null),
            new ChunkEntry(2, 0, hash2)   // duplicate with files
        );
        
        Map<String, Map<String, RegionEntry>> regions = Map.of(
            "overworld", Map.of("r.0.0.mca", new RegionEntry("r.0.0.mca", "anvil", overworldChunks)),
            "nether", Map.of("r.0.0.mca", new RegionEntry("r.0.0.mca", "anvil", netherChunks))
        );
        
        Manifest manifest = new Manifest(
            "test", 1000L, "1.21.1", null, files, regions
        );
        
        ManifestStats stats = manifest.getStats();
        
        assertEquals(2, stats.fileCount());           // 2 files
        assertEquals(2, stats.regionCount());         // 2 regions (overworld + nether)
        assertEquals(8, stats.totalChunks());         // 5 + 3 = 8 total chunks
        assertEquals(5, stats.nonEmptyChunks());      // 3 + 2 = 5 non-empty chunks
        assertEquals(3, stats.uniqueObjects());       // hash1, hash2, hash3 (deduplicated)
    }
    
    @Test
    @DisplayName("Manifest: Empty manifest has zero stats")
    void testManifestEmptyStats() {
        Manifest manifest = new Manifest(
            "test", 1000L, "1.21.1", null, Map.of(), Map.of()
        );
        
        ManifestStats stats = manifest.getStats();
        
        assertEquals(0, stats.fileCount());
        assertEquals(0, stats.regionCount());
        assertEquals(0, stats.totalChunks());
        assertEquals(0, stats.nonEmptyChunks());
        assertEquals(0, stats.uniqueObjects());
    }

    // ==================== JSON Compatibility Tests (Preparation) ====================
    
    @Test
    @DisplayName("JSON Compatibility: All fields are serializable types")
    void testJsonCompatibility() {
        // Create a complete manifest with all field types
        Map<String, String> files = new HashMap<>();
        files.put("level.dat", "a".repeat(64));
        
        List<ChunkEntry> chunks = new ArrayList<>();
        chunks.add(new ChunkEntry(0, 0, "b".repeat(64)));
        chunks.add(new ChunkEntry(1, 0, null));
        
        RegionEntry region = new RegionEntry("r.0.0.mca", "anvil", chunks);
        
        Map<String, RegionEntry> overworldRegions = new HashMap<>();
        overworldRegions.put("r.0.0.mca", region);
        
        Map<String, Map<String, RegionEntry>> regions = new HashMap<>();
        regions.put("overworld", overworldRegions);
        
        Manifest manifest = new Manifest(
            "20260403_120000",
            1775198400L,
            "1.21.1",
            "Test description",
            files,
            regions
        );
        
        // Verify all fields are accessible and have expected types
        assertNotNull(manifest.snapshotId());          // String
        assertTrue(manifest.timestamp() > 0);          // long
        assertNotNull(manifest.gameVersion());         // String
        assertNotNull(manifest.description());         // String (nullable)
        assertNotNull(manifest.files());               // Map<String, String>
        assertNotNull(manifest.regions());             // Map<String, Map<String, RegionEntry>>
        
        // Verify nested structures are accessible
        RegionEntry retrievedRegion = manifest.regions().get("overworld").get("r.0.0.mca");
        assertNotNull(retrievedRegion);
        assertEquals("r.0.0.mca", retrievedRegion.filename());
        assertEquals("anvil", retrievedRegion.format());
        assertEquals(2, retrievedRegion.chunks().size());
        
        ChunkEntry chunk0 = retrievedRegion.chunks().get(0);
        assertEquals(0, chunk0.x());
        assertEquals(0, chunk0.z());
        assertNotNull(chunk0.hash());
        
        ChunkEntry chunk1 = retrievedRegion.chunks().get(1);
        assertEquals(1, chunk1.x());
        assertNull(chunk1.hash());
    }
    
    @Test
    @DisplayName("ManifestStats: All fields are primitive types")
    void testManifestStatsJsonCompatibility() {
        ManifestStats stats = new ManifestStats(10, 5, 1000, 800, 500);
        
        assertEquals(10, stats.fileCount());
        assertEquals(5, stats.regionCount());
        assertEquals(1000, stats.totalChunks());
        assertEquals(800, stats.nonEmptyChunks());
        assertEquals(500, stats.uniqueObjects());
    }
}
