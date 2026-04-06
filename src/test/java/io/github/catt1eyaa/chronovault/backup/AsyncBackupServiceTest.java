package io.github.catt1eyaa.chronovault.backup;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * AsyncBackupService 测试
 */
class AsyncBackupServiceTest {

    @Test
    void testBackupAsyncSuccess(@TempDir Path tempDir) throws ExecutionException, InterruptedException, TimeoutException, IOException {
        Path worldDir = tempDir.resolve("world");
        Files.createDirectories(worldDir);
        Files.writeString(worldDir.resolve("level.dat"), "test level");

        Path backupDir = tempDir.resolve("backups");

        try (AsyncBackupService service = new AsyncBackupService(backupDir)) {
            CompletableFuture<BackupResult> future = service.backupAsync(worldDir, "1.21.1", "async test");
            BackupResult result = future.get(10, TimeUnit.SECONDS);

            assertTrue(result.success());
            assertNotNull(result.snapshotId());
            assertEquals(1, result.stats().totalFiles());
        }
    }

    @Test
    void testProgressCallback(@TempDir Path tempDir) throws IOException, ExecutionException, InterruptedException, TimeoutException {
        Path worldDir = tempDir.resolve("world");
        Files.createDirectories(worldDir);
        Files.writeString(worldDir.resolve("level.dat"), "level");
        Files.writeString(worldDir.resolve("data.txt"), "data");

        Path backupDir = tempDir.resolve("backups");
        List<String> progressFiles = new CopyOnWriteArrayList<>();

        try (AsyncBackupService service = new AsyncBackupService(backupDir)) {
            CompletableFuture<BackupResult> future = service.backupAsync(
                    worldDir,
                    "1.21.1",
                    "progress test",
                    (current, total, file) -> progressFiles.add(current + "/" + total + ":" + file)
            );

            BackupResult result = future.get(10, TimeUnit.SECONDS);
            assertTrue(result.success());
            assertFalse(progressFiles.isEmpty());
            assertTrue(progressFiles.stream().anyMatch(p -> p.contains("level.dat") || p.contains("data.txt")));
        }
    }

    @Test
    void testShutdownRejectsNewTasks(@TempDir Path tempDir) {
        Path backupDir = tempDir.resolve("backups");
        AsyncBackupService service = new AsyncBackupService(backupDir);
        service.close();

        assertTrue(service.isShutdown());

        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> service.backupAsync(tempDir.resolve("world"), "1.21.1", "should fail")
        );
        assertTrue(ex.getMessage().contains("already shut down"));
    }

    @Test
    void testCancelBackupFuture(@TempDir Path tempDir) throws IOException {
        Path worldDir = tempDir.resolve("world");
        Files.createDirectories(worldDir);

        for (int i = 0; i < 200; i++) {
            Files.writeString(worldDir.resolve("file-" + i + ".dat"), "data-" + i + "-" + "x".repeat(1000));
        }

        Path backupDir = tempDir.resolve("backups");

        try (AsyncBackupService service = new AsyncBackupService(backupDir, 3, 2)) {
            CompletableFuture<BackupResult> future = service.backupAsync(worldDir, "1.21.1", "cancel test");

            boolean cancelled = future.cancel(true);
            assertTrue(cancelled || future.isCancelled());
        }
    }
}
