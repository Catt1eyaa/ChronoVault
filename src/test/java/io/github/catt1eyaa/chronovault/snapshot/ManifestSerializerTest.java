package io.github.catt1eyaa.chronovault.snapshot;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

@DisplayName("Manifest Serializer Tests")
class ManifestSerializerTest {

    // ==================== 序列化测试 ====================
    
    @Test
    @DisplayName("toJson: Serialize simple manifest")
    void testToJsonSimple() {
        Manifest manifest = new Manifest(
            "20260405_120000",
            1775203200L,
            "1.21.1",
            "Test backup",
            Map.of("level.dat", "a".repeat(64)),
            Map.of()
        );
        
        String json = ManifestSerializer.toJson(manifest);
        
        assertNotNull(json);
        assertTrue(json.contains("\"snapshotId\": \"20260405_120000\""));
        assertTrue(json.contains("\"timestamp\": 1775203200"));
        assertTrue(json.contains("\"gameVersion\": \"1.21.1\""));
        assertTrue(json.contains("\"description\": \"Test backup\""));
        assertTrue(json.contains("\"level.dat\""));
    }
    
    @Test
    @DisplayName("toJson: Serialize manifest with null chunks")
    void testToJsonWithNullChunks() {
        List<ChunkEntry> chunks = List.of(
            new ChunkEntry(0, 0, "a".repeat(64)),
            new ChunkEntry(0, 1, null),  // 空chunk
            new ChunkEntry(1, 0, "b".repeat(64))
        );
        
        RegionEntry region = new RegionEntry("r.0.0.mca", "anvil", chunks);
        
        Map<String, Map<String, RegionEntry>> regions = Map.of(
            "overworld", Map.of("r.0.0.mca", region)
        );
        
        Manifest manifest = new Manifest(
            "20260405_120000",
            1775203200L,
            "1.21.1",
            null,
            Map.of(),
            regions
        );
        
        String json = ManifestSerializer.toJson(manifest);
        
        // 验证null chunk被序列化为 "hash": null
        assertTrue(json.contains("\"hash\": null"), 
            "JSON should contain null hash for empty chunks");
        
        // 验证非空chunk有hash值
        assertTrue(json.contains("\"hash\": \"" + "a".repeat(64) + "\""));
    }
    
    @Test
    @DisplayName("toJson: Pretty printing produces readable output")
    void testToJsonPrettyPrinting() {
        Manifest manifest = new Manifest(
            "test",
            1000L,
            "1.21.1",
            null,
            Map.of(),
            Map.of()
        );
        
        String json = ManifestSerializer.toJson(manifest);
        
        // 验证包含换行符（pretty printing）
        assertTrue(json.contains("\n"), "JSON should be pretty-printed with newlines");
        
        // 验证包含缩进
        assertTrue(json.contains("  "), "JSON should have indentation");
    }
    
    @Test
    @DisplayName("toJson: Null manifest throws exception")
    void testToJsonNullManifest() {
        assertThrows(NullPointerException.class, 
            () -> ManifestSerializer.toJson(null));
    }
    
    // ==================== 反序列化测试 ====================
    
    @Test
    @DisplayName("fromJson: Deserialize simple manifest")
    void testFromJsonSimple() {
        String json = """
            {
              "snapshotId": "20260405_120000",
              "timestamp": 1775203200,
              "gameVersion": "1.21.1",
              "description": "Test backup",
              "files": {
                "level.dat": "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
              },
              "regions": {}
            }
            """;
        
        Manifest manifest = ManifestSerializer.fromJson(json);
        
        assertNotNull(manifest);
        assertEquals("20260405_120000", manifest.snapshotId());
        assertEquals(1775203200L, manifest.timestamp());
        assertEquals("1.21.1", manifest.gameVersion());
        assertEquals("Test backup", manifest.description());
        assertEquals(1, manifest.files().size());
        assertTrue(manifest.files().containsKey("level.dat"));
    }
    
    @Test
    @DisplayName("fromJson: Deserialize manifest with null chunks")
    void testFromJsonWithNullChunks() {
        String json = """
            {
              "snapshotId": "test",
              "timestamp": 1000,
              "gameVersion": "1.21.1",
              "description": null,
              "files": {},
              "regions": {
                "overworld": {
                  "r.0.0.mca": {
                    "filename": "r.0.0.mca",
                    "format": "anvil",
                    "chunks": [
                      {"x": 0, "z": 0, "hash": "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"},
                      {"x": 0, "z": 1, "hash": null},
                      {"x": 1, "z": 0, "hash": "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"}
                    ]
                  }
                }
              }
            }
            """;
        
        Manifest manifest = ManifestSerializer.fromJson(json);
        
        assertNotNull(manifest);
        assertEquals(1, manifest.regions().size());
        
        RegionEntry region = manifest.regions().get("overworld").get("r.0.0.mca");
        assertNotNull(region);
        assertEquals(3, region.chunks().size());
        
        // 验证null chunk
        ChunkEntry emptyChunk = region.chunks().get(1);
        assertEquals(0, emptyChunk.x());
        assertEquals(1, emptyChunk.z());
        assertNull(emptyChunk.hash());
        assertTrue(emptyChunk.isEmpty());
    }
    
    @Test
    @DisplayName("fromJson: Invalid JSON throws exception")
    void testFromJsonInvalidJson() {
        String invalidJson = "{ invalid json }";
        
        // Gson会抛出JsonSyntaxException，但它继承自RuntimeException
        assertThrows(RuntimeException.class,
            () -> ManifestSerializer.fromJson(invalidJson));
    }
    
    @Test
    @DisplayName("fromJson: Null string throws exception")
    void testFromJsonNullString() {
        assertThrows(NullPointerException.class,
            () -> ManifestSerializer.fromJson(null));
    }
    
    @Test
    @DisplayName("fromJson: Invalid manifest data throws exception")
    void testFromJsonInvalidManifest() {
        // Missing required field (snapshotId)
        String json = """
            {
              "timestamp": 1000,
              "gameVersion": "1.21.1",
              "files": {},
              "regions": {}
            }
            """;
        
        // Should throw exception when Manifest constructor validates
        assertThrows(Exception.class,
            () -> ManifestSerializer.fromJson(json));
    }
    
    // ==================== 往返测试 ====================
    
    @Test
    @DisplayName("Round-trip: Empty manifest")
    void testRoundTripEmpty() {
        Manifest original = new Manifest(
            "test",
            1000L,
            "1.21.1",
            null,
            Map.of(),
            Map.of()
        );
        
        String json = ManifestSerializer.toJson(original);
        Manifest restored = ManifestSerializer.fromJson(json);
        
        assertEquals(original.snapshotId(), restored.snapshotId());
        assertEquals(original.timestamp(), restored.timestamp());
        assertEquals(original.gameVersion(), restored.gameVersion());
        assertEquals(original.description(), restored.description());
        assertEquals(original.files(), restored.files());
        assertEquals(original.regions(), restored.regions());
    }
    
    @Test
    @DisplayName("Round-trip: Complex manifest")
    void testRoundTripComplex() {
        String hash1 = "a".repeat(64);
        String hash2 = "b".repeat(64);
        String hash3 = "c".repeat(64);
        
        Map<String, String> files = Map.of(
            "level.dat", hash1,
            "playerdata/uuid.dat", hash2
        );
        
        List<ChunkEntry> chunks = List.of(
            new ChunkEntry(0, 0, hash3),
            new ChunkEntry(0, 1, null),
            new ChunkEntry(1, 0, hash1)
        );
        
        RegionEntry region = new RegionEntry("r.0.0.mca", "anvil", chunks);
        
        Map<String, Map<String, RegionEntry>> regions = Map.of(
            "overworld", Map.of("r.0.0.mca", region),
            "nether", Map.of()
        );
        
        Manifest original = new Manifest(
            "20260405_153000",
            1775203800L,
            "1.21.1",
            "Test backup",
            files,
            regions
        );
        
        String json = ManifestSerializer.toJson(original);
        Manifest restored = ManifestSerializer.fromJson(json);
        
        // 验证顶层字段
        assertEquals(original.snapshotId(), restored.snapshotId());
        assertEquals(original.timestamp(), restored.timestamp());
        assertEquals(original.gameVersion(), restored.gameVersion());
        assertEquals(original.description(), restored.description());
        
        // 验证文件
        assertEquals(original.files(), restored.files());
        
        // 验证regions结构
        assertEquals(original.regions().size(), restored.regions().size());
        
        RegionEntry restoredRegion = restored.regions().get("overworld").get("r.0.0.mca");
        assertNotNull(restoredRegion);
        assertEquals(region.filename(), restoredRegion.filename());
        assertEquals(region.format(), restoredRegion.format());
        assertEquals(region.chunks().size(), restoredRegion.chunks().size());
        
        // 验证chunks
        for (int i = 0; i < chunks.size(); i++) {
            ChunkEntry originalChunk = chunks.get(i);
            ChunkEntry restoredChunk = restoredRegion.chunks().get(i);
            
            assertEquals(originalChunk.x(), restoredChunk.x());
            assertEquals(originalChunk.z(), restoredChunk.z());
            assertEquals(originalChunk.hash(), restoredChunk.hash());
        }
    }
    
    // ==================== 文件I/O测试 ====================
    
    @Test
    @DisplayName("save/load: Round-trip with file I/O")
    void testSaveAndLoad(@TempDir Path tempDir) throws IOException {
        Manifest original = new Manifest(
            "20260405_120000",
            1775203200L,
            "1.21.1",
            "Test backup",
            Map.of("level.dat", "a".repeat(64)),
            Map.of()
        );
        
        Path filePath = tempDir.resolve("20260405_120000.json");
        
        // Save
        ManifestSerializer.save(original, filePath);
        
        // 验证文件存在
        assertTrue(Files.exists(filePath));
        
        // Load
        Manifest restored = ManifestSerializer.load(filePath);
        
        assertEquals(original.snapshotId(), restored.snapshotId());
        assertEquals(original.timestamp(), restored.timestamp());
        assertEquals(original.gameVersion(), restored.gameVersion());
        assertEquals(original.files(), restored.files());
    }
    
    @Test
    @DisplayName("save: Creates parent directories")
    void testSaveCreatesParentDirs(@TempDir Path tempDir) throws IOException {
        Manifest manifest = new Manifest(
            "test",
            1000L,
            "1.21.1",
            null,
            Map.of(),
            Map.of()
        );
        
        Path filePath = tempDir.resolve("snapshots/test.json");
        
        // 父目录不存在
        assertFalse(Files.exists(filePath.getParent()));
        
        // Save应该创建父目录
        ManifestSerializer.save(manifest, filePath);
        
        assertTrue(Files.exists(filePath.getParent()));
        assertTrue(Files.exists(filePath));
    }
    
    @Test
    @DisplayName("save: Atomic write (no partial files on error)")
    void testSaveAtomicWrite(@TempDir Path tempDir) throws IOException {
        Manifest manifest = new Manifest(
            "test",
            1000L,
            "1.21.1",
            null,
            Map.of(),
            Map.of()
        );
        
        Path filePath = tempDir.resolve("test.json");
        
        // 正常保存
        ManifestSerializer.save(manifest, filePath);
        assertTrue(Files.exists(filePath));
        
        // 验证没有临时文件残留
        Path tempFile = tempDir.resolve("test.json.tmp");
        assertFalse(Files.exists(tempFile));
    }
    
    @Test
    @DisplayName("load: Non-existent file throws exception")
    void testLoadNonExistentFile(@TempDir Path tempDir) {
        Path filePath = tempDir.resolve("nonexistent.json");
        
        assertThrows(IOException.class,
            () -> ManifestSerializer.load(filePath));
    }
    
    @Test
    @DisplayName("save/load: Null parameters throw exception")
    void testSaveLoadNullParameters(@TempDir Path tempDir) {
        Manifest manifest = new Manifest(
            "test",
            1000L,
            "1.21.1",
            null,
            Map.of(),
            Map.of()
        );
        
        Path filePath = tempDir.resolve("test.json");
        
        assertThrows(NullPointerException.class,
            () -> ManifestSerializer.save(null, filePath));
        
        assertThrows(NullPointerException.class,
            () -> ManifestSerializer.save(manifest, null));
        
        assertThrows(NullPointerException.class,
            () -> ManifestSerializer.load(null));
    }
    
    // ==================== listSnapshots测试 ====================
    
    @Test
    @DisplayName("listSnapshots: Empty directory returns empty list")
    void testListSnapshotsEmpty(@TempDir Path tempDir) throws IOException {
        List<String> snapshots = ManifestSerializer.listSnapshots(tempDir);
        
        assertNotNull(snapshots);
        assertTrue(snapshots.isEmpty());
    }
    
    @Test
    @DisplayName("listSnapshots: Non-existent directory returns empty list")
    void testListSnapshotsNonExistent(@TempDir Path tempDir) throws IOException {
        Path nonExistent = tempDir.resolve("nonexistent");
        
        List<String> snapshots = ManifestSerializer.listSnapshots(nonExistent);
        
        assertNotNull(snapshots);
        assertTrue(snapshots.isEmpty());
    }
    
    @Test
    @DisplayName("listSnapshots: Lists all snapshot files")
    void testListSnapshotsMultipleFiles(@TempDir Path tempDir) throws IOException {
        // 创建多个快照文件
        Manifest manifest1 = new Manifest("20260405_120000", 1000L, "1.21.1", null, Map.of(), Map.of());
        Manifest manifest2 = new Manifest("20260405_130000", 2000L, "1.21.1", null, Map.of(), Map.of());
        Manifest manifest3 = new Manifest("20260405_140000", 3000L, "1.21.1", null, Map.of(), Map.of());
        
        ManifestSerializer.save(manifest1, tempDir.resolve("20260405_120000.json"));
        ManifestSerializer.save(manifest2, tempDir.resolve("20260405_130000.json"));
        ManifestSerializer.save(manifest3, tempDir.resolve("20260405_140000.json"));
        
        List<String> snapshots = ManifestSerializer.listSnapshots(tempDir);
        
        assertEquals(3, snapshots.size());
        assertTrue(snapshots.contains("20260405_120000"));
        assertTrue(snapshots.contains("20260405_130000"));
        assertTrue(snapshots.contains("20260405_140000"));
    }
    
    @Test
    @DisplayName("listSnapshots: Ignores non-JSON files")
    void testListSnapshotsIgnoresNonJson(@TempDir Path tempDir) throws IOException {
        // 创建JSON和非JSON文件
        Manifest manifest = new Manifest("test", 1000L, "1.21.1", null, Map.of(), Map.of());
        ManifestSerializer.save(manifest, tempDir.resolve("test.json"));
        
        Files.writeString(tempDir.resolve("readme.txt"), "Not a JSON file");
        Files.writeString(tempDir.resolve("data.xml"), "<data/>");
        
        List<String> snapshots = ManifestSerializer.listSnapshots(tempDir);
        
        assertEquals(1, snapshots.size());
        assertEquals("test", snapshots.get(0));
    }
    
    @Test
    @DisplayName("listSnapshots: Returns sorted list")
    void testListSnapshotsSorted(@TempDir Path tempDir) throws IOException {
        // 以非排序顺序创建文件
        Manifest m1 = new Manifest("20260405_140000", 1000L, "1.21.1", null, Map.of(), Map.of());
        Manifest m2 = new Manifest("20260405_120000", 2000L, "1.21.1", null, Map.of(), Map.of());
        Manifest m3 = new Manifest("20260405_130000", 3000L, "1.21.1", null, Map.of(), Map.of());
        
        ManifestSerializer.save(m1, tempDir.resolve("20260405_140000.json"));
        ManifestSerializer.save(m2, tempDir.resolve("20260405_120000.json"));
        ManifestSerializer.save(m3, tempDir.resolve("20260405_130000.json"));
        
        List<String> snapshots = ManifestSerializer.listSnapshots(tempDir);
        
        assertEquals(3, snapshots.size());
        assertEquals("20260405_120000", snapshots.get(0));
        assertEquals("20260405_130000", snapshots.get(1));
        assertEquals("20260405_140000", snapshots.get(2));
    }
    
    @Test
    @DisplayName("listSnapshots: Null directory throws exception")
    void testListSnapshotsNullDirectory() {
        assertThrows(NullPointerException.class,
            () -> ManifestSerializer.listSnapshots(null));
    }
    
    // ==================== loadAllSnapshots测试 ====================
    
    @Test
    @DisplayName("loadAllSnapshots: Empty directory returns empty map")
    void testLoadAllSnapshotsEmpty(@TempDir Path tempDir) throws IOException {
        Map<String, Manifest> snapshots = ManifestSerializer.loadAllSnapshots(tempDir);
        
        assertNotNull(snapshots);
        assertTrue(snapshots.isEmpty());
    }
    
    @Test
    @DisplayName("loadAllSnapshots: Loads all snapshots")
    void testLoadAllSnapshotsMultiple(@TempDir Path tempDir) throws IOException {
        // 创建多个快照
        Manifest m1 = new Manifest("20260405_120000", 1000L, "1.21.1", "First", Map.of(), Map.of());
        Manifest m2 = new Manifest("20260405_130000", 2000L, "1.21.1", "Second", Map.of(), Map.of());
        Manifest m3 = new Manifest("20260405_140000", 3000L, "1.21.1", "Third", Map.of(), Map.of());
        
        ManifestSerializer.save(m1, tempDir.resolve("20260405_120000.json"));
        ManifestSerializer.save(m2, tempDir.resolve("20260405_130000.json"));
        ManifestSerializer.save(m3, tempDir.resolve("20260405_140000.json"));
        
        Map<String, Manifest> snapshots = ManifestSerializer.loadAllSnapshots(tempDir);
        
        assertEquals(3, snapshots.size());
        
        assertTrue(snapshots.containsKey("20260405_120000"));
        assertTrue(snapshots.containsKey("20260405_130000"));
        assertTrue(snapshots.containsKey("20260405_140000"));
        
        assertEquals("First", snapshots.get("20260405_120000").description());
        assertEquals("Second", snapshots.get("20260405_130000").description());
        assertEquals("Third", snapshots.get("20260405_140000").description());
    }
    
    @Test
    @DisplayName("loadAllSnapshots: Null directory throws exception")
    void testLoadAllSnapshotsNullDirectory() {
        assertThrows(NullPointerException.class,
            () -> ManifestSerializer.loadAllSnapshots(null));
    }
    
    @Test
    @DisplayName("loadAllSnapshots: Corrupted file throws exception")
    void testLoadAllSnapshotsCorruptedFile(@TempDir Path tempDir) throws IOException {
        // 创建正常文件
        Manifest manifest = new Manifest("valid", 1000L, "1.21.1", null, Map.of(), Map.of());
        ManifestSerializer.save(manifest, tempDir.resolve("valid.json"));
        
        // 创建损坏的JSON文件
        Files.writeString(tempDir.resolve("corrupted.json"), "{ invalid json }");
        
        // 应该抛出异常（因为无法解析corrupted.json）
        assertThrows(RuntimeException.class,
            () -> ManifestSerializer.loadAllSnapshots(tempDir));
    }
    
    // ==================== JSON格式验证 ====================
    
    @Test
    @DisplayName("JSON format: Matches spec.md schema")
    void testJsonFormatMatchesSpec() {
        String hash1 = "a1b2c3".repeat(10) + "a1b2";  // 64 chars
        String hash2 = "d4e5f6".repeat(10) + "d4e5";  // 64 chars
        String hash3 = "3e2596".repeat(10) + "3e25";  // 64 chars
        String hash4 = "7f3b82".repeat(10) + "7f3b";  // 64 chars
        
        Map<String, String> files = Map.of(
            "level.dat", hash1,
            "playerdata/uuid.dat", hash2
        );
        
        List<ChunkEntry> chunks = List.of(
            new ChunkEntry(0, 0, hash3),
            new ChunkEntry(0, 1, null),
            new ChunkEntry(1, 0, hash4)
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
        
        // 验证关键字段存在
        assertTrue(json.contains("\"snapshotId\":"));
        assertTrue(json.contains("\"timestamp\":"));
        assertTrue(json.contains("\"gameVersion\":"));
        assertTrue(json.contains("\"description\":"));
        assertTrue(json.contains("\"files\":"));
        assertTrue(json.contains("\"regions\":"));
        
        // 验证嵌套结构
        assertTrue(json.contains("\"overworld\":"));
        assertTrue(json.contains("\"r.0.0.mca\":"));
        assertTrue(json.contains("\"filename\":"));
        assertTrue(json.contains("\"format\":"));
        assertTrue(json.contains("\"chunks\":"));
        
        // 验证chunk字段
        assertTrue(json.contains("\"x\":"));
        assertTrue(json.contains("\"z\":"));
        assertTrue(json.contains("\"hash\":"));
        assertTrue(json.contains("\"hash\": null"));
        
        // 验证格式化（包含换行和缩进）
        assertTrue(json.contains("\n"));
        assertTrue(json.contains("  "));
    }
    
    @Test
    @DisplayName("JSON format: UTF-8 encoding support")
    void testJsonUtf8Encoding(@TempDir Path tempDir) throws IOException {
        Manifest manifest = new Manifest(
            "test",
            1000L,
            "1.21.1",
            "测试备份 with 中文 and émojis 🎮",
            Map.of(),
            Map.of()
        );
        
        Path filePath = tempDir.resolve("test.json");
        
        ManifestSerializer.save(manifest, filePath);
        Manifest restored = ManifestSerializer.load(filePath);
        
        assertEquals(manifest.description(), restored.description());
        assertEquals("测试备份 with 中文 and émojis 🎮", restored.description());
    }
}
