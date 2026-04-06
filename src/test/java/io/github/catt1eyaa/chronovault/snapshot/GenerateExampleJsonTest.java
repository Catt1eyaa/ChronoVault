package io.github.catt1eyaa.chronovault.snapshot;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * 生成示例 JSON 文件用于验证
 */
public class GenerateExampleJsonTest {
    
    @Test
    void generateExampleJson(@TempDir Path tempDir) throws IOException {
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
        
        // 保存到临时文件
        Path outputFile = tempDir.resolve("example_manifest.json");
        ManifestSerializer.save(manifest, outputFile);
        
        // 读取并打印
        String json = Files.readString(outputFile);
        
        // 写入到项目根目录以便查看
        Path projectOutput = Path.of("example_manifest.json");
        Files.writeString(projectOutput, json);
        
        System.out.println("Example JSON saved to: " + projectOutput.toAbsolutePath());
        System.out.println("\nJSON content:");
        System.out.println(json);
    }
}
