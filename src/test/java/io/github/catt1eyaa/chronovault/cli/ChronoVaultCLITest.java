package io.github.catt1eyaa.chronovault.cli;

import io.github.catt1eyaa.chronovault.backup.BackupExecutor;
import io.github.catt1eyaa.chronovault.backup.BackupResult;
import io.github.catt1eyaa.chronovault.restore.RestoreExecutor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * CLI 集成测试
 */
class ChronoVaultCLITest {

    @Test
    void testListAndRestoreIntegration(@TempDir Path tempDir) throws IOException {
        Path backupDir = tempDir.resolve("backup");
        Path worldDir = tempDir.resolve("world");
        Path restoreDir = tempDir.resolve("restored");

        // 创建世界数据
        Files.createDirectories(worldDir);
        String testData = "test world content";
        Files.writeString(worldDir.resolve("level.dat"), testData);

        // 执行备份
        BackupExecutor backupExecutor = new BackupExecutor(backupDir, 3);
        BackupResult backupResult = backupExecutor.execute(worldDir, "1.21.1", "Integration Test");
        assertTrue(backupResult.success());
        assertNotNull(backupResult.snapshotId());

        // 使用 RestoreExecutor 验证快照可以列出
        RestoreExecutor restoreExecutor = new RestoreExecutor(backupDir);
        var snapshots = restoreExecutor.listSnapshots();
        assertEquals(1, snapshots.size());
        assertEquals(backupResult.snapshotId(), snapshots.get(0));

        // 使用 RestoreExecutor 恢复到新世界
        Path savesDir = tempDir.resolve("saves");
        Files.createDirectories(savesDir);
        var result = restoreExecutor.restoreToNewWorld(backupResult.snapshotId(), savesDir, "TestWorld");
        assertEquals(backupResult.snapshotId(), result.snapshotId());
        assertEquals(1, result.restoredFiles());
        assertEquals("TestWorld-restored-" + backupResult.snapshotId(), result.targetWorldName());

        // 验证恢复的数据
        assertTrue(Files.exists(result.targetWorldPath().resolve("level.dat")));
        String restoredData = Files.readString(result.targetWorldPath().resolve("level.dat"));
        assertEquals(testData, restoredData);
    }

    @Test
    void testCLIListCommand(@TempDir Path tempDir) throws IOException {
        Path backupDir = tempDir.resolve("backup");
        Path worldDir = tempDir.resolve("world");

        // 创建简单世界数据
        Files.createDirectories(worldDir);
        Files.writeString(worldDir.resolve("level.dat"), "test world data");

        // 执行备份创建快照
        BackupExecutor executor = new BackupExecutor(backupDir, 3);
        BackupResult result = executor.execute(worldDir, "1.21.1", "Test Snapshot");
        assertTrue(result.success());

        // 测试 list 命令 - 捕获输出
        ByteArrayOutputStream outContent = new ByteArrayOutputStream();
        PrintStream originalOut = System.out;
        System.setOut(new PrintStream(outContent));

        try {
            ChronoVaultCLI.main(new String[]{"list", backupDir.toString()});
        } catch (Exception e) {
            // Ignore any exceptions from System.exit() calls
        } finally {
            System.setOut(originalOut);
        }

        String output = outContent.toString();
        assertTrue(output.contains("Available snapshots") || output.contains(result.snapshotId()));
    }

    @Test
    void testCLIRestoreCommand(@TempDir Path tempDir) throws IOException {
        Path backupDir = tempDir.resolve("backup");
        Path worldDir = tempDir.resolve("world");
        Path restoreDir = tempDir.resolve("restored");

        // 创建世界数据
        Files.createDirectories(worldDir);
        String testData = "cli restore test";
        Files.writeString(worldDir.resolve("level.dat"), testData);

        // 执行备份
        BackupExecutor backupExecutor = new BackupExecutor(backupDir, 3);
        BackupResult backupResult = backupExecutor.execute(worldDir, "1.21.1", "CLI Restore Test");
        assertTrue(backupResult.success());

        // 测试 restore 命令
        ByteArrayOutputStream outContent = new ByteArrayOutputStream();
        PrintStream originalOut = System.out;
        System.setOut(new PrintStream(outContent));

        try {
            ChronoVaultCLI.main(new String[]{
                "restore",
                backupDir.toString(),
                backupResult.snapshotId(),
                restoreDir.toString()
            });
        } catch (Exception e) {
            // Ignore any exceptions from System.exit() calls
        } finally {
            System.setOut(originalOut);
        }

        String output = outContent.toString();
        assertTrue(output.contains("Restore completed successfully") || 
                   output.contains("Files restored"));

        // 验证恢复的数据
        assertTrue(Files.exists(restoreDir.resolve("level.dat")));
        String restoredData = Files.readString(restoreDir.resolve("level.dat"));
        assertEquals(testData, restoredData);
    }
}
