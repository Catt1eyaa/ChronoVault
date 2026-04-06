package io.github.catt1eyaa.chronovault.backup;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WorldStateManager 测试
 */
class WorldStateManagerTest {

    @Test
    void testPrepareForBackupExecutesCommandsInOrder() throws IOException {
        List<String> commands = new ArrayList<>();
        WorldStateManager manager = new WorldStateManager(commands::add);

        manager.prepareForBackup();

        assertEquals(List.of("save-all flush", "save-off"), commands);
        assertTrue(manager.isPrepared());
    }

    @Test
    void testFinishBackupExecutesSaveOn() throws IOException {
        List<String> commands = new ArrayList<>();
        WorldStateManager manager = new WorldStateManager(commands::add);

        manager.prepareForBackup();
        manager.finishBackup();

        assertEquals(List.of("save-all flush", "save-off", "save-on"), commands);
        assertFalse(manager.isPrepared());
    }

    @Test
    void testPrepareIsIdempotent() throws IOException {
        List<String> commands = new ArrayList<>();
        WorldStateManager manager = new WorldStateManager(commands::add);

        manager.prepareForBackup();
        manager.prepareForBackup();

        assertEquals(List.of("save-all flush", "save-off"), commands);
        assertTrue(manager.isPrepared());
    }

    @Test
    void testFinishWithoutPrepareDoesNothing() throws IOException {
        List<String> commands = new ArrayList<>();
        WorldStateManager manager = new WorldStateManager(commands::add);

        manager.finishBackup();

        assertTrue(commands.isEmpty());
        assertFalse(manager.isPrepared());
    }

    @Test
    void testPrepareRollbackOnSaveOffFailure() {
        List<String> commands = new ArrayList<>();
        WorldStateManager manager = new WorldStateManager(command -> {
            commands.add(command);
            if ("save-off".equals(command)) {
                throw new IOException("save-off failed");
            }
        });

        IOException exception = assertThrows(IOException.class, manager::prepareForBackup);
        assertTrue(exception.getMessage().contains("save-off"));
        assertEquals(List.of("save-all flush", "save-off", "save-on"), commands);
        assertFalse(manager.isPrepared());
    }
}
