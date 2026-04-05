package io.github.catt1eyaa.chronovault.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BackupPathWorldFolderContractTest {

    @Test
    void testBackupPathUsesWorldFolderName(@TempDir Path tempDir) {
        Path backupRoot = tempDir.resolve("backups");
        String worldFolderName = "test";

        Path resolved = BackupPathResolver.resolve(backupRoot, worldFolderName);

        assertEquals(backupRoot.resolve("test"), resolved);
    }
}
