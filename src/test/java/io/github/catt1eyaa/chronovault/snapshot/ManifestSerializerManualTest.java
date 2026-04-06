package io.github.catt1eyaa.chronovault.snapshot;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

/**
 * 手动测试 - 打印 JSON 输出以验证格式
 */
public class ManifestSerializerManualTest {
    
    @Test
    void printExampleJson() {
        String hash1 = "a1b2c3d4e5f67890a1b2c3d4e5f67890a1b2c3d4e5f67890a1b2c3d4e5f67890";
        String hash2 = "d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5";
        String hash3 = "3e25960a79dbc69b674cd4ec67a72c623e25960a79dbc69b674cd4ec67a72c62";
        
        Map<String, String> files = Map.of(
            "level.dat", hash1,
            "playerdata/uuid.dat", hash2
        );
        
        List<ChunkEntry> chunks = List.of(
            new ChunkEntry(0, 0, hash3),
            new ChunkEntry(0, 1, null),  // Empty chunk
            new ChunkEntry(1, 0, hash1)
        );
        
        RegionEntry region = new RegionEntry("r.0.0.mca", "anvil", chunks);
        
        Map<String, Map<String, RegionEntry>> regions = Map.of(
            "overworld", Map.of("r.0.0.mca", region)
        );
        
        Manifest manifest = new Manifest(
            "20260405_153000",
            1775203800L,
            "1.21.1",
            "Test backup",
            files,
            regions
        );
        
        String json = ManifestSerializer.toJson(manifest);
        
        System.out.println("=".repeat(80));
        System.out.println("Generated JSON (should match spec.md schema):");
        System.out.println("=".repeat(80));
        System.out.println(json);
        System.out.println("=".repeat(80));
        
        // 也测试反序列化
        Manifest restored = ManifestSerializer.fromJson(json);
        System.out.println("\nDeserialization successful!");
        System.out.println("Snapshot ID: " + restored.snapshotId());
        System.out.println("Timestamp: " + restored.timestamp());
        System.out.println("Files: " + restored.files().size());
        System.out.println("Regions: " + restored.regions().size());
    }
}
