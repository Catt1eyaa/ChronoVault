package io.github.catt1eyaa.chronovault.backup;

import io.github.catt1eyaa.chronovault.region.AnvilWriter;
import io.github.catt1eyaa.chronovault.region.ChunkData;
import io.github.catt1eyaa.chronovault.snapshot.Manifest;
import io.github.catt1eyaa.chronovault.snapshot.ManifestSerializer;
import io.github.catt1eyaa.chronovault.snapshot.RegionEntry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 测试 BackupExecutor 的备份执行功能
 */
class BackupExecutorTest {
    
    @Test
    void testBackupEmptyWorld(@TempDir Path tempDir) throws IOException {
        // 创建空世界目录
        Path worldDir = tempDir.resolve("world");
        Files.createDirectories(worldDir);
        
        // 创建备份目录
        Path backupDir = tempDir.resolve("backups");
        
        // 执行备份
        BackupExecutor executor = new BackupExecutor(backupDir);
        BackupResult result = executor.execute(worldDir, "1.21.1", "Test backup");
        
        // 验证结果
        assertTrue(result.success());
        assertNotNull(result.snapshotId());
        assertEquals(0, result.stats().totalFiles());
        assertEquals(0, result.stats().totalRegions());
        assertEquals(0, result.stats().totalChunks());
        assertTrue(result.errors().isEmpty());
    }
    
    @Test
    void testBackupWithRegularFiles(@TempDir Path tempDir) throws IOException {
        // 创建世界目录和文件
        Path worldDir = tempDir.resolve("world");
        Files.createDirectories(worldDir);
        
        // 创建 level.dat
        Path levelDat = worldDir.resolve("level.dat");
        Files.writeString(levelDat, "fake level data");
        
        // 创建 playerdata 目录和文件
        Path playerDataDir = worldDir.resolve("playerdata");
        Files.createDirectories(playerDataDir);
        Path playerFile = playerDataDir.resolve("player1.dat");
        Files.writeString(playerFile, "fake player data");
        
        // 创建备份目录
        Path backupDir = tempDir.resolve("backups");
        
        // 执行备份
        BackupExecutor executor = new BackupExecutor(backupDir);
        BackupResult result = executor.execute(worldDir, "1.21.1", "Test with files");
        
        // 验证结果
        assertTrue(result.success());
        assertNotNull(result.snapshotId());
        assertEquals(2, result.stats().totalFiles());
        assertEquals(0, result.stats().totalRegions());
        assertTrue(result.errors().isEmpty());
        
        // 验证 manifest 已保存
        Path manifestPath = backupDir.resolve("snapshots").resolve(result.snapshotId() + ".json");
        assertTrue(Files.exists(manifestPath));
        
        // 加载并验证 manifest
        Manifest manifest = ManifestSerializer.load(manifestPath);
        assertEquals(result.snapshotId(), manifest.snapshotId());
        assertEquals("1.21.1", manifest.gameVersion());
        assertEquals("Test with files", manifest.description());
        assertEquals(2, manifest.files().size());
        assertTrue(manifest.files().containsKey("level.dat"));
        assertTrue(manifest.files().containsKey("playerdata/player1.dat"));
    }
    
    @Test
    void testBackupWithRegionFiles(@TempDir Path tempDir) throws IOException {
        // 创建世界目录
        Path worldDir = tempDir.resolve("world");
        Files.createDirectories(worldDir);
        
        // 创建 region 目录
        Path regionDir = worldDir.resolve("region");
        Files.createDirectories(regionDir);
        
        // 创建一个简单的 region 文件
        Path regionFile = regionDir.resolve("r.0.0.mca");
        createTestRegionFile(regionFile);
        
        // 创建备份目录
        Path backupDir = tempDir.resolve("backups");
        
        // 执行备份
        BackupExecutor executor = new BackupExecutor(backupDir);
        BackupResult result = executor.execute(worldDir, "1.21.1", "Test with region");
        
        // 验证结果
        assertTrue(result.success());
        assertNotNull(result.snapshotId());
        assertEquals(0, result.stats().totalFiles());
        assertEquals(1, result.stats().totalRegions());
        assertEquals(3, result.stats().totalChunks()); // 3 non-empty chunks
        assertTrue(result.errors().isEmpty());
        
        // 验证 manifest
        Path manifestPath = backupDir.resolve("snapshots").resolve(result.snapshotId() + ".json");
        assertTrue(Files.exists(manifestPath));
        
        Manifest manifest = ManifestSerializer.load(manifestPath);
        assertTrue(manifest.regions().containsKey("overworld"));
        assertTrue(manifest.regions().get("overworld").containsKey("region/r.0.0.mca"));
    }

    @Test
    void testBackupIncludesZeroByteRegionInManifest(@TempDir Path tempDir) throws IOException {
        Path worldDir = tempDir.resolve("world");
        Files.createDirectories(worldDir.resolve("region"));
        Files.write(worldDir.resolve("region").resolve("r.0.-1.mca"), new byte[0]);

        Path backupDir = tempDir.resolve("backups");
        BackupExecutor executor = new BackupExecutor(backupDir);
        BackupResult result = executor.execute(worldDir, "1.21.1", "zero-byte region");

        assertTrue(result.success());
        assertEquals(1, result.stats().totalRegions());
        assertEquals(0, result.stats().totalChunks());

        Manifest manifest = ManifestSerializer.load(
            backupDir.resolve("snapshots").resolve(result.snapshotId() + ".json")
        );
        RegionEntry regionEntry = manifest.regions().get("overworld").get("region/r.0.-1.mca");
        assertNotNull(regionEntry);
        assertEquals("anvil-zero-byte", regionEntry.format());
    }
    
    @Test
    void testBackupWithMultipleDimensions(@TempDir Path tempDir) throws IOException {
        // 创建世界目录
        Path worldDir = tempDir.resolve("world");
        Files.createDirectories(worldDir);
        
        // 创建 Overworld region
        Path overworldRegion = worldDir.resolve("region");
        Files.createDirectories(overworldRegion);
        createTestRegionFile(overworldRegion.resolve("r.0.0.mca"));
        
        // 创建 Nether region
        Path netherRegion = worldDir.resolve("DIM-1").resolve("region");
        Files.createDirectories(netherRegion);
        createTestRegionFile(netherRegion.resolve("r.0.0.mca"));
        
        // 创建 End region
        Path endRegion = worldDir.resolve("DIM1").resolve("region");
        Files.createDirectories(endRegion);
        createTestRegionFile(endRegion.resolve("r.0.0.mca"));
        
        // 创建备份目录
        Path backupDir = tempDir.resolve("backups");
        
        // 执行备份
        BackupExecutor executor = new BackupExecutor(backupDir);
        BackupResult result = executor.execute(worldDir, "1.21.1", "Multi-dimension");
        
        // 验证结果
        assertTrue(result.success());
        assertEquals(3, result.stats().totalRegions());
        assertEquals(9, result.stats().totalChunks()); // 3 chunks × 3 dimensions
        
        // 验证 manifest 包含所有维度
        Path manifestPath = backupDir.resolve("snapshots").resolve(result.snapshotId() + ".json");
        Manifest manifest = ManifestSerializer.load(manifestPath);
        
        assertEquals(3, manifest.regions().size());
        assertTrue(manifest.regions().containsKey("overworld"));
        assertTrue(manifest.regions().containsKey("nether"));
        assertTrue(manifest.regions().containsKey("end"));
    }
    
    @Test
    void testBackupDeduplication(@TempDir Path tempDir) throws IOException {
        // 创建世界目录
        Path worldDir = tempDir.resolve("world");
        Files.createDirectories(worldDir);
        
        // 创建两个相同内容的文件
        Files.writeString(worldDir.resolve("file1.txt"), "same content");
        Files.writeString(worldDir.resolve("file2.txt"), "same content");
        
        // 创建备份目录
        Path backupDir = tempDir.resolve("backups");
        
        // 执行备份
        BackupExecutor executor = new BackupExecutor(backupDir);
        BackupResult result = executor.execute(worldDir, "1.21.1", "Dedup test");
        
        // 验证结果
        assertTrue(result.success());
        assertEquals(2, result.stats().totalFiles());
        assertEquals(1, result.stats().uniqueObjects()); // 应该去重为 1 个对象
        
        // 验证压缩率 - 相同内容应该只存储一次
        assertTrue(result.stats().compressedSize() < result.stats().originalSize());
    }
    
    @Test
    void testBackupNonExistentWorld(@TempDir Path tempDir) {
        Path worldDir = tempDir.resolve("nonexistent");
        Path backupDir = tempDir.resolve("backups");
        
        BackupExecutor executor = new BackupExecutor(backupDir);
        BackupResult result = executor.execute(worldDir, "1.21.1", "Should fail");
        
        // 验证失败
        assertFalse(result.success());
        assertNull(result.snapshotId());
        assertFalse(result.errors().isEmpty());
        assertTrue(result.errors().get(0).contains("Failed to scan world directory"));
    }

    @Test
    void testBackupFailsWhenAnyRegionFails(@TempDir Path tempDir) throws IOException {
        Path worldDir = tempDir.resolve("world");
        Files.createDirectories(worldDir);

        Path regionDir = worldDir.resolve("region");
        Files.createDirectories(regionDir);
        Files.writeString(regionDir.resolve("r.0.0.mca"), "invalid-anvil-data");

        Path backupDir = tempDir.resolve("backups");
        BackupExecutor executor = new BackupExecutor(backupDir);
        BackupResult result = executor.execute(worldDir, "1.21.1", "Should fail on bad region");

        assertFalse(result.success());
        assertNull(result.snapshotId());
        assertFalse(result.errors().isEmpty());
        assertTrue(result.errors().get(0).contains("Failed to backup region"));
    }
    
    @Test
    void testBackupStatistics(@TempDir Path tempDir) throws IOException {
        // 创建复杂的世界
        Path worldDir = tempDir.resolve("world");
        Files.createDirectories(worldDir);
        
        // 添加文件
        Files.writeString(worldDir.resolve("level.dat"), "level data");
        
        // 添加 region
        Path regionDir = worldDir.resolve("region");
        Files.createDirectories(regionDir);
        createTestRegionFile(regionDir.resolve("r.0.0.mca"));
        
        // 创建备份目录
        Path backupDir = tempDir.resolve("backups");
        
        // 执行备份
        BackupExecutor executor = new BackupExecutor(backupDir);
        BackupResult result = executor.execute(worldDir, "1.21.1", "Stats test");
        
        // 验证统计信息
        assertTrue(result.success());
        BackupResult.BackupStats stats = result.stats();
        
        assertEquals(1, stats.totalFiles());
        assertEquals(1, stats.totalRegions());
        assertEquals(3, stats.totalChunks());
        assertTrue(stats.uniqueObjects() > 0);
        assertTrue(stats.originalSize() > 0);
        assertTrue(stats.compressedSize() > 0);
        assertTrue(stats.durationMs() >= 0);
        
        // 验证压缩率计算
        double compressionRatio = stats.compressionRatio();
        assertTrue(Double.isFinite(compressionRatio));
        assertTrue(compressionRatio <= 100);
    }
    
    @Test
    void testBackupResultToString() {
        // 测试成功结果的 toString
        BackupResult.BackupStats stats = new BackupResult.BackupStats(
            10, 5, 100, 50, 1000000L, 500000L, 5000L
        );
        BackupResult success = BackupResult.success("20260405_120000", stats);
        
        String str = success.toString();
        assertTrue(str.contains("SUCCESS"));
        assertTrue(str.contains("20260405_120000"));
        
        // 测试失败结果的 toString
        BackupResult failure = BackupResult.failure(stats, List.of("Error 1", "Error 2"));
        str = failure.toString();
        assertTrue(str.contains("FAILURE"));
        assertTrue(str.contains("errors=2"));
    }

    @Test
    void testIsVanishedFileErrorWithNoSuchFileException() {
        IOException error = new IOException(new NoSuchFileException("missing.tmp"));
        assertTrue(BackupExecutor.isVanishedFileError(error));
    }

    @Test
    void testIsVanishedFileErrorWithUncheckedIOExceptionChain() {
        IOException error = new IOException(new UncheckedIOException(new NoSuchFileException("missing.tmp")));
        assertTrue(BackupExecutor.isVanishedFileError(error));
    }

    @Test
    void testIsVanishedFileErrorWithNormalIOException() {
        IOException error = new IOException("permission denied");
        assertFalse(BackupExecutor.isVanishedFileError(error));
    }
    
    /**
     * 创建一个测试用的 region 文件，包含 3 个 chunk
     */
    private void createTestRegionFile(Path regionFile) throws IOException {
        List<ChunkData> chunks = new ArrayList<>();
        
        // 创建 3 个测试 chunk
        for (int i = 0; i < 3; i++) {
            byte[] data = ("Chunk data " + i).getBytes();
            chunks.add(new ChunkData(i, 0, (byte) 2, data)); // Zlib compression
        }
        
        AnvilWriter.writeRegion(regionFile, chunks);
    }
}
