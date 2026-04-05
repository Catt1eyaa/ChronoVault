package io.github.catt1eyaa.chronovault.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class BackupPathResolverTest {

    @Test
    void testResolveSimpleWorldName(@TempDir Path tempDir) {
        Path result = BackupPathResolver.resolve(tempDir, "MyWorld");
        assertEquals(tempDir.resolve("MyWorld"), result);
    }

    @Test
    void testResolveWorldNameWithSpaces(@TempDir Path tempDir) {
        Path result = BackupPathResolver.resolve(tempDir, "My World");
        assertEquals(tempDir.resolve("My World"), result);
    }

    @Test
    void testSanitizeNormalName() {
        assertEquals("MyWorld", BackupPathResolver.sanitizeWorldName("MyWorld"));
    }

    @Test
    void testSanitizeNameWithIllegalChars() {
        assertEquals("My_World_Test", BackupPathResolver.sanitizeWorldName("My<World>Test"));
        assertEquals("path_to_world", BackupPathResolver.sanitizeWorldName("path/to\\world"));
        assertEquals("world_v1.0", BackupPathResolver.sanitizeWorldName("world:v1.0"));
    }

    @Test
    void testSanitizeWindowsReservedNames() {
        assertEquals("CON_safe", BackupPathResolver.sanitizeWorldName("CON"));
        assertEquals("NUL_safe", BackupPathResolver.sanitizeWorldName("NUL"));
        assertEquals("COM1_safe", BackupPathResolver.sanitizeWorldName("COM1"));
        assertEquals("LPT1_safe", BackupPathResolver.sanitizeWorldName("LPT1"));
    }

    @Test
    void testSanitizeEmptyOrBlankName() {
        assertEquals("_unnamed", BackupPathResolver.sanitizeWorldName(""));
        assertEquals("_unnamed", BackupPathResolver.sanitizeWorldName("   "));
        assertEquals("_unnamed", BackupPathResolver.sanitizeWorldName(null));
    }

    @Test
    void testSanitizeLongName() {
        String longName = "A".repeat(250);
        String sanitized = BackupPathResolver.sanitizeWorldName(longName);
        assertTrue(sanitized.length() <= 200);
        assertTrue(sanitized.startsWith("AAAAAAAAAA"));
    }

    @Test
    void testResolveNullWorldNameThrows(@TempDir Path tempDir) {
        assertThrows(IllegalArgumentException.class,
            () -> BackupPathResolver.resolve(tempDir, null));
    }

    @Test
    void testResolveNullBackupRootThrows() {
        assertThrows(NullPointerException.class,
            () -> BackupPathResolver.resolve(null, "MyWorld"));
    }
}
