package io.github.catt1eyaa.chronovault.restore;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * RestoreResult 测试。
 */
class RestoreResultTest {

    @TempDir
    Path tempDir;

    @Test
    void testValidConstruction() {
        Path worldPath = tempDir.resolve("MyWorld");
        RestoreResult result = new RestoreResult(
            "snapshot-123",
            10,
            5,
            200,
            "MyWorld",
            worldPath
        );

        assertEquals("snapshot-123", result.snapshotId());
        assertEquals(10, result.restoredFiles());
        assertEquals(5, result.restoredRegions());
        assertEquals(200, result.restoredChunks());
        assertEquals("MyWorld", result.targetWorldName());
        assertEquals(worldPath, result.targetWorldPath());
    }

    @Test
    void testZeroCountsAllowed() {
        Path worldPath = tempDir.resolve("EmptyWorld");
        RestoreResult result = new RestoreResult(
            "snapshot-000",
            0,
            0,
            0,
            "EmptyWorld",
            worldPath
        );

        assertEquals(0, result.restoredFiles());
        assertEquals(0, result.restoredRegions());
        assertEquals(0, result.restoredChunks());
    }

    @Test
    void testNullSnapshotIdThrows() {
        Path worldPath = tempDir.resolve("World");
        Exception ex = assertThrows(IllegalArgumentException.class, () -> {
            new RestoreResult(null, 1, 1, 1, "World", worldPath);
        });
        assertTrue(ex.getMessage().contains("snapshotId"));
    }

    @Test
    void testEmptySnapshotIdThrows() {
        Path worldPath = tempDir.resolve("World");
        Exception ex = assertThrows(IllegalArgumentException.class, () -> {
            new RestoreResult("", 1, 1, 1, "World", worldPath);
        });
        assertTrue(ex.getMessage().contains("snapshotId"));
    }

    @Test
    void testNegativeRestoredFilesThrows() {
        Path worldPath = tempDir.resolve("World");
        Exception ex = assertThrows(IllegalArgumentException.class, () -> {
            new RestoreResult("snapshot-1", -1, 1, 1, "World", worldPath);
        });
        assertTrue(ex.getMessage().contains("restoredFiles"));
        assertTrue(ex.getMessage().contains("-1"));
    }

    @Test
    void testNegativeRestoredRegionsThrows() {
        Path worldPath = tempDir.resolve("World");
        Exception ex = assertThrows(IllegalArgumentException.class, () -> {
            new RestoreResult("snapshot-1", 1, -5, 1, "World", worldPath);
        });
        assertTrue(ex.getMessage().contains("restoredRegions"));
        assertTrue(ex.getMessage().contains("-5"));
    }

    @Test
    void testNegativeRestoredChunksThrows() {
        Path worldPath = tempDir.resolve("World");
        Exception ex = assertThrows(IllegalArgumentException.class, () -> {
            new RestoreResult("snapshot-1", 1, 1, -100, "World", worldPath);
        });
        assertTrue(ex.getMessage().contains("restoredChunks"));
        assertTrue(ex.getMessage().contains("-100"));
    }

    @Test
    void testNullTargetWorldNameThrows() {
        Path worldPath = tempDir.resolve("World");
        Exception ex = assertThrows(IllegalArgumentException.class, () -> {
            new RestoreResult("snapshot-1", 1, 1, 1, null, worldPath);
        });
        assertTrue(ex.getMessage().contains("targetWorldName"));
    }

    @Test
    void testEmptyTargetWorldNameThrows() {
        Path worldPath = tempDir.resolve("World");
        Exception ex = assertThrows(IllegalArgumentException.class, () -> {
            new RestoreResult("snapshot-1", 1, 1, 1, "", worldPath);
        });
        assertTrue(ex.getMessage().contains("targetWorldName"));
    }

    @Test
    void testNullTargetWorldPathThrows() {
        Exception ex = assertThrows(IllegalArgumentException.class, () -> {
            new RestoreResult("snapshot-1", 1, 1, 1, "World", null);
        });
        assertTrue(ex.getMessage().contains("targetWorldPath"));
    }
}
