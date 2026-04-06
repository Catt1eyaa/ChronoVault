package io.github.catt1eyaa.chronovault.snapshot;

import java.util.List;
import java.util.Map;

/**
 * Demo showing how to use the Manifest data model.
 * This demonstrates creating a snapshot manifest for a Minecraft world.
 */
public class ManifestDemo {
    
    public static void main(String[] args) {
        System.out.println("=== ChronoVault Manifest Demo ===\n");
        
        // 1. Create some chunk entries
        System.out.println("1. Creating chunk entries...");
        List<ChunkEntry> overworldChunks = List.of(
            new ChunkEntry(0, 0, "3e25960a79dbc69b674cd4ec67a72c623e25960a79dbc69b674cd4ec67a72c62"),
            new ChunkEntry(1, 0, "9e107d9d372bb6826bd81d3542a419d69e107d9d372bb6826bd81d3542a419d6"),
            new ChunkEntry(2, 0, null),  // Empty chunk
            new ChunkEntry(3, 0, "d41d8cd98f00b204e9800998ecf8427ed41d8cd98f00b204e9800998ecf8427e")
        );
        System.out.println("   Created " + overworldChunks.size() + " chunk entries");
        System.out.println("   Empty chunks: " + overworldChunks.stream().filter(ChunkEntry::isEmpty).count());
        
        // 2. Create region entries
        System.out.println("\n2. Creating region entries...");
        RegionEntry overworldRegion = new RegionEntry("r.0.0.mca", "anvil", overworldChunks);
        System.out.println("   Region: " + overworldRegion.filename());
        System.out.println("   Format: " + overworldRegion.format());
        System.out.println("   Total chunks: " + overworldRegion.chunks().size());
        System.out.println("   Non-empty chunks: " + overworldRegion.getNonEmptyChunkCount());
        
        // 3. Create file mappings
        System.out.println("\n3. Creating file mappings...");
        Map<String, String> files = Map.of(
            "level.dat", "a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2",
            "session.lock", "f6e5d4c3b2a1f6e5d4c3b2a1f6e5d4c3b2a1f6e5d4c3b2a1f6e5d4c3b2a1f6e5",
            "playerdata/player1.dat", "1234567890abcdef1234567890abcdef1234567890abcdef1234567890abcdef"
        );
        System.out.println("   Added " + files.size() + " files");
        
        // 4. Create region mappings (dimensions)
        System.out.println("\n4. Creating dimension mappings...");
        Map<String, Map<String, RegionEntry>> regions = Map.of(
            "overworld", Map.of("r.0.0.mca", overworldRegion),
            "nether", Map.of(),
            "end", Map.of()
        );
        System.out.println("   Dimensions: " + regions.keySet());
        
        // 5. Create the manifest
        System.out.println("\n5. Creating manifest...");
        long timestamp = System.currentTimeMillis() / 1000;
        String snapshotId = Manifest.generateSnapshotId(timestamp);
        
        Manifest manifest = new Manifest(
            snapshotId,
            timestamp,
            "1.21.1",
            "Demo snapshot before Ender Dragon fight",
            files,
            regions
        );
        
        System.out.println("   Snapshot ID: " + manifest.snapshotId());
        System.out.println("   Timestamp: " + manifest.timestamp());
        System.out.println("   Game Version: " + manifest.gameVersion());
        System.out.println("   Description: " + manifest.description());
        
        // 6. Get statistics
        System.out.println("\n6. Manifest statistics:");
        ManifestStats stats = manifest.getStats();
        System.out.println("   Files: " + stats.fileCount());
        System.out.println("   Regions: " + stats.regionCount());
        System.out.println("   Total chunks: " + stats.totalChunks());
        System.out.println("   Non-empty chunks: " + stats.nonEmptyChunks());
        System.out.println("   Unique objects: " + stats.uniqueObjects());
        
        // 7. Get all referenced hashes (for garbage collection)
        System.out.println("\n7. Referenced object hashes:");
        var hashes = manifest.getAllReferencedHashes();
        System.out.println("   Total unique hashes: " + hashes.size());
        hashes.forEach(hash -> System.out.println("   - " + hash.substring(0, 16) + "..."));
        
        // 8. Demonstrate immutability
        System.out.println("\n8. Demonstrating immutability:");
        System.out.println("   Manifest is a record - all fields are final and immutable");
        System.out.println("   ChunkEntry is a record - coordinates and hash cannot change");
        System.out.println("   RegionEntry is a record - filename, format, and chunks cannot change");
        
        System.out.println("\n=== Demo Complete ===");
    }
}
