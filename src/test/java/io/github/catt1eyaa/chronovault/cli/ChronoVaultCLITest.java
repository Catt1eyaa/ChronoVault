package io.github.catt1eyaa.chronovault.cli;

import io.github.catt1eyaa.chronovault.backup.BackupExecutor;
import io.github.catt1eyaa.chronovault.backup.BackupResult;
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
        Path savesDir = tempDir.resolve("saves");
        Path sourceWorld = savesDir.resolve("TestWorld");
        Files.createDirectories(sourceWorld);
        Files.writeString(sourceWorld.resolve("level.dat"), "test-data");

        Path backupRoot = tempDir.resolve("backups");
        Path worldBackupDir = backupRoot.resolve("TestWorld");
        BackupExecutor backupExecutor = new BackupExecutor(worldBackupDir);
        BackupResult result = backupExecutor.execute(sourceWorld, "1.21.1", "CLI test backup");
        assertTrue(result.success());

        ByteArrayOutputStream listOutput = new ByteArrayOutputStream();
        PrintStream originalOut = System.out;
        System.setOut(new PrintStream(listOutput));

        try {
            ChronoVaultCLI.main(new String[]{"list", backupRoot.toString(), "TestWorld"});
        } finally {
            System.setOut(originalOut);
        }

        String listResult = listOutput.toString();
        assertTrue(listResult.contains(result.snapshotId()));
        assertTrue(listResult.contains("CLI test backup"));

        ByteArrayOutputStream restoreOutput = new ByteArrayOutputStream();
        System.setOut(new PrintStream(restoreOutput));

        try {
            ChronoVaultCLI.main(new String[]{
                "restore",
                backupRoot.toString(),
                "TestWorld",
                result.snapshotId(),
                savesDir.toString()
            });
        } finally {
            System.setOut(originalOut);
        }

        String restoreResult = restoreOutput.toString();
        assertTrue(restoreResult.contains("Restore completed successfully"));
        assertTrue(restoreResult.contains("TestWorld-restored-" + result.snapshotId()));

        Path expectedNewWorld = savesDir.resolve("TestWorld-restored-" + result.snapshotId());
        assertTrue(Files.exists(expectedNewWorld));
        assertTrue(Files.exists(expectedNewWorld.resolve("level.dat")));
        assertEquals("test-data", Files.readString(expectedNewWorld.resolve("level.dat")));

        assertEquals("test-data", Files.readString(sourceWorld.resolve("level.dat")));
    }

    @Test
    void testListCommandShowsWorldSnapshots(@TempDir Path tempDir) throws IOException {
        Path savesRoot = tempDir.resolve("saves");
        Path world1 = savesRoot.resolve("World1");
        Path world2 = savesRoot.resolve("World2");
        Files.createDirectories(world1);
        Files.createDirectories(world2);
        Files.writeString(world1.resolve("level.dat"), "world1");
        Files.writeString(world2.resolve("level.dat"), "world2");

        Path backupRoot = tempDir.resolve("backups");

        BackupExecutor executor1 = new BackupExecutor(backupRoot.resolve("World1"));
        BackupResult result1 = executor1.execute(world1, "1.21.1", "World1 backup");
        assertTrue(result1.success());

        BackupExecutor executor2 = new BackupExecutor(backupRoot.resolve("World2"));
        BackupResult result2 = executor2.execute(world2, "1.21.1", "World2 backup");
        assertTrue(result2.success());

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        PrintStream originalOut = System.out;
        System.setOut(new PrintStream(output));

        try {
            ChronoVaultCLI.main(new String[]{"list", backupRoot.toString(), "World1"});
        } finally {
            System.setOut(originalOut);
        }

        String listResult = output.toString();
        assertTrue(listResult.contains("World1 backup"));
        assertFalse(listResult.contains("World2 backup"));
    }

    @Test
    void testRestoreCreatesNewWorld(@TempDir Path tempDir) throws IOException {
        Path savesRoot = tempDir.resolve("saves");
        Path sourceWorld = savesRoot.resolve("OriginalWorld");
        Files.createDirectories(sourceWorld);
        Files.writeString(sourceWorld.resolve("level.dat"), "original-data");

        Path backupRoot = tempDir.resolve("backups");
        BackupExecutor backupExecutor = new BackupExecutor(backupRoot.resolve("OriginalWorld"));
        BackupResult result = backupExecutor.execute(sourceWorld, "1.21.1", "test");
        assertTrue(result.success());

        Files.writeString(sourceWorld.resolve("level.dat"), "modified-data");

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        PrintStream originalOut = System.out;
        System.setOut(new PrintStream(output));

        try {
            ChronoVaultCLI.main(new String[]{
                "restore",
                backupRoot.toString(),
                "OriginalWorld",
                result.snapshotId(),
                savesRoot.toString()
            });
        } finally {
            System.setOut(originalOut);
        }

        Path newWorld = savesRoot.resolve("OriginalWorld-restored-" + result.snapshotId());
        assertTrue(Files.exists(newWorld));
        assertEquals("original-data", Files.readString(newWorld.resolve("level.dat")));

        assertEquals("modified-data", Files.readString(sourceWorld.resolve("level.dat")));
    }
}
