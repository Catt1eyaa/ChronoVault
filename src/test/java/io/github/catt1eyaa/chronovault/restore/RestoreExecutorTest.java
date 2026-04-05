package io.github.catt1eyaa.chronovault.restore;

import io.github.catt1eyaa.chronovault.backup.BackupExecutor;
import io.github.catt1eyaa.chronovault.backup.BackupResult;
import io.github.catt1eyaa.chronovault.region.AnvilReader;
import io.github.catt1eyaa.chronovault.region.AnvilWriter;
import io.github.catt1eyaa.chronovault.region.ChunkData;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 测试 RestoreExecutor 的核心恢复行为。
 */
class RestoreExecutorTest {

    @Test
    void testRestoreToNewWorldCreatesNewDirectory(@TempDir Path tempDir) throws IOException {
        Path savesRoot = tempDir.resolve("saves");
        Path sourceWorld = savesRoot.resolve("TestWorld");
        Files.createDirectories(sourceWorld);
        Files.writeString(sourceWorld.resolve("level.dat"), "level-data");

        Path sourceRegionDir = sourceWorld.resolve("region");
        Files.createDirectories(sourceRegionDir);
        createRegion(sourceRegionDir.resolve("r.0.0.mca"));

        Path backupDir = tempDir.resolve("backups").resolve("TestWorld");
        BackupExecutor backupExecutor = new BackupExecutor(backupDir);
        BackupResult backupResult = backupExecutor.execute(sourceWorld, "1.21.1", "test");
        assertTrue(backupResult.success());

        RestoreExecutor restoreExecutor = new RestoreExecutor(backupDir);
        RestoreResult result = restoreExecutor.restoreToNewWorld(
            backupResult.snapshotId(),
            savesRoot,
            "TestWorld"
        );

        assertEquals("TestWorld-restored-" + backupResult.snapshotId(), result.targetWorldName());
        assertTrue(Files.exists(result.targetWorldPath()));
        assertTrue(Files.exists(result.targetWorldPath().resolve("level.dat")));
        assertTrue(Files.exists(result.targetWorldPath().resolve("region").resolve("r.0.0.mca")));

        // 验证源世界未被修改
        assertEquals("level-data", Files.readString(sourceWorld.resolve("level.dat")));
    }

    @Test
    void testRestoreToNewWorldHandlesNameConflict(@TempDir Path tempDir) throws IOException {
        Path savesRoot = tempDir.resolve("saves");
        Path sourceWorld = savesRoot.resolve("TestWorld");
        Files.createDirectories(sourceWorld);
        Files.writeString(sourceWorld.resolve("level.dat"), "data");

        Path backupDir = tempDir.resolve("backups").resolve("TestWorld");
        BackupExecutor backupExecutor = new BackupExecutor(backupDir);
        BackupResult backupResult = backupExecutor.execute(sourceWorld, "1.21.1", "test");
        assertTrue(backupResult.success());

        String expectedBaseName = "TestWorld-restored-" + backupResult.snapshotId();
        Files.createDirectories(savesRoot.resolve(expectedBaseName));

        RestoreExecutor restoreExecutor = new RestoreExecutor(backupDir);
        RestoreResult result = restoreExecutor.restoreToNewWorld(
            backupResult.snapshotId(),
            savesRoot,
            "TestWorld"
        );

        assertEquals(expectedBaseName + "-2", result.targetWorldName());
        assertTrue(Files.exists(result.targetWorldPath()));
    }

    @Test
    void testRestoreToNewWorldDoesNotModifySourceWorld(@TempDir Path tempDir) throws IOException {
        Path savesRoot = tempDir.resolve("saves");
        Path sourceWorld = savesRoot.resolve("OriginalWorld");
        Files.createDirectories(sourceWorld);
        Files.writeString(sourceWorld.resolve("level.dat"), "original-content");
        Files.writeString(sourceWorld.resolve("extra.txt"), "extra-file");

        Path backupDir = tempDir.resolve("backups").resolve("OriginalWorld");
        BackupExecutor backupExecutor = new BackupExecutor(backupDir);
        BackupResult backupResult = backupExecutor.execute(sourceWorld, "1.21.1", "test");
        assertTrue(backupResult.success());

        Files.writeString(sourceWorld.resolve("level.dat"), "modified-content");

        RestoreExecutor restoreExecutor = new RestoreExecutor(backupDir);
        restoreExecutor.restoreToNewWorld(backupResult.snapshotId(), savesRoot, "OriginalWorld");

        assertEquals("modified-content", Files.readString(sourceWorld.resolve("level.dat")));
        assertEquals("extra-file", Files.readString(sourceWorld.resolve("extra.txt")));
    }

    @Test
    void testRestoreKeepsRegionEntitiesPoiSeparated(@TempDir Path tempDir) throws IOException {
        Path savesRoot = tempDir.resolve("saves");
        Path sourceWorld = savesRoot.resolve("TestWorld");
        Files.createDirectories(sourceWorld);
        Files.writeString(sourceWorld.resolve("level.dat"), "level-data");

        createSingleChunkRegion(sourceWorld.resolve("region").resolve("r.0.0.mca"), 0, 0, "region-chunk");
        createSingleChunkRegion(sourceWorld.resolve("entities").resolve("r.0.0.mca"), 1, 0, "entities-chunk");
        createSingleChunkRegion(sourceWorld.resolve("poi").resolve("r.0.0.mca"), 2, 0, "poi-chunk");

        Path backupDir = tempDir.resolve("backups").resolve("TestWorld");
        BackupExecutor backupExecutor = new BackupExecutor(backupDir);
        BackupResult backupResult = backupExecutor.execute(sourceWorld, "1.21.1", "restore-separation-test");
        assertTrue(backupResult.success());

        RestoreExecutor restoreExecutor = new RestoreExecutor(backupDir);
        RestoreResult restoreResult = restoreExecutor.restoreToNewWorld(
            backupResult.snapshotId(),
            savesRoot,
            "TestWorld"
        );

        assertEquals(1, restoreResult.restoredFiles());
        assertEquals(3, restoreResult.restoredRegions());
        assertEquals(3, restoreResult.restoredChunks());

        assertChunkPayloadEquals(restoreResult.targetWorldPath().resolve("region").resolve("r.0.0.mca"), "region-chunk");
        assertChunkPayloadEquals(restoreResult.targetWorldPath().resolve("entities").resolve("r.0.0.mca"), "entities-chunk");
        assertChunkPayloadEquals(restoreResult.targetWorldPath().resolve("poi").resolve("r.0.0.mca"), "poi-chunk");
    }

    @Test
    void testListSnapshots(@TempDir Path tempDir) throws IOException {
        Path savesRoot = tempDir.resolve("saves");
        Path world = savesRoot.resolve("world");
        Files.createDirectories(world);
        Files.writeString(world.resolve("level.dat"), "data");

        Path backupDir = tempDir.resolve("backups").resolve("world");
        BackupExecutor backupExecutor = new BackupExecutor(backupDir);
        BackupResult first = backupExecutor.execute(world, "1.21.1", "first");

        try {
            Thread.sleep(1100L);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            fail("Interrupted while waiting", e);
        }

        BackupResult second = backupExecutor.execute(world, "1.21.1", "second");

        assertTrue(first.success());
        assertTrue(second.success());

        RestoreExecutor restoreExecutor = new RestoreExecutor(backupDir);
        List<String> snapshots = restoreExecutor.listSnapshots();

        assertEquals(2, snapshots.size());
        assertTrue(snapshots.contains(first.snapshotId()));
        assertTrue(snapshots.contains(second.snapshotId()));
    }

    private void createRegion(Path regionFile) throws IOException {
        List<ChunkData> chunks = List.of(
            new ChunkData(0, 0, ChunkData.COMPRESSION_ZLIB, "chunk-a".getBytes()),
            new ChunkData(1, 0, ChunkData.COMPRESSION_ZLIB, "chunk-b".getBytes())
        );
        AnvilWriter.writeRegion(regionFile, chunks);
    }

    private void createSingleChunkRegion(Path regionFile, int chunkX, int chunkZ, String payload) throws IOException {
        List<ChunkData> chunks = List.of(
            new ChunkData(chunkX, chunkZ, ChunkData.COMPRESSION_ZLIB, payload.getBytes(StandardCharsets.UTF_8))
        );
        AnvilWriter.writeRegion(regionFile, chunks);
    }

    private void assertChunkPayloadEquals(Path regionFile, String expectedPayload) throws IOException {
        assertTrue(Files.exists(regionFile));
        List<ChunkData> chunks = AnvilReader.readAllChunks(regionFile);
        assertEquals(1, chunks.size());
        assertEquals(expectedPayload, new String(chunks.get(0).rawData(), StandardCharsets.UTF_8));
    }
}
