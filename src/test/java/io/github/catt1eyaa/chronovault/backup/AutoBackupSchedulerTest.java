package io.github.catt1eyaa.chronovault.backup;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * AutoBackupScheduler 测试。
 */
class AutoBackupSchedulerTest {

    @Test
    void testResolveWorldBackupDirUsesBackupPathResolver(@TempDir Path tempDir) {
        Path backupRoot = tempDir.resolve("backups");
        Path worldDir = tempDir.resolve("saves").resolve("My World");

        Path resolved = AutoBackupScheduler.resolveWorldBackupDir(backupRoot, worldDir);

        assertEquals(backupRoot.resolve("My World"), resolved);
    }

    @Test
    void testResolveWorldBackupDirRejectsNullWorldDir(@TempDir Path tempDir) {
        Path backupRoot = tempDir.resolve("backups");
        assertThrows(NullPointerException.class, () -> AutoBackupScheduler.resolveWorldBackupDir(backupRoot, null));
    }

    @Test
    void testResolveWorldBackupDirRejectsPathWithoutFileName(@TempDir Path tempDir) {
        Path backupRoot = tempDir.resolve("backups");
        assertThrows(IllegalArgumentException.class, () -> AutoBackupScheduler.resolveWorldBackupDir(backupRoot, Path.of("/")));
    }

    @Test
    void testResolveWorldBackupDirUsesFolderNameNotDisplayName(@TempDir Path tempDir) {
        Path backupRoot = tempDir.resolve("backups");
        Path worldDir = tempDir.resolve("saves").resolve("test");

        Path resolved = AutoBackupScheduler.resolveWorldBackupDir(backupRoot, worldDir);

        assertEquals(backupRoot.resolve("test"), resolved);
        assertTrue(resolved.startsWith(backupRoot));
    }
}
