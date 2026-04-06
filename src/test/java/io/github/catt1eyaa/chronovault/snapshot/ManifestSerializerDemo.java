package io.github.catt1eyaa.chronovault.snapshot;

import java.util.List;
import java.util.Map;

/**
 * 演示 ManifestSerializer 的使用
 */
public class ManifestSerializerDemo {
    public static void main(String[] args) {
        // 创建示例数据
        String hash1 = "a".repeat(64);
        String hash2 = "b".repeat(64);
        String hash3 = "c".repeat(64);
        
        Map<String, String> files = Map.of(
            "level.dat", hash1,
            "playerdata/uuid.dat", hash2
        );
        
        List<ChunkEntry> chunks = List.of(
            new ChunkEntry(0, 0, hash3),
            new ChunkEntry(0, 1, null),  // 空chunk
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
        
        // 序列化为JSON
        String json = ManifestSerializer.toJson(manifest);
        System.out.println("Generated JSON:");
        System.out.println(json);
        
        System.out.println("\n" + "=".repeat(80));
        System.out.println("Stats:");
        ManifestStats stats = manifest.getStats();
        System.out.println("  Files: " + stats.fileCount());
        System.out.println("  Regions: " + stats.regionCount());
        System.out.println("  Total chunks: " + stats.totalChunks());
        System.out.println("  Non-empty chunks: " + stats.nonEmptyChunks());
        System.out.println("  Unique objects: " + stats.uniqueObjects());
    }
}
