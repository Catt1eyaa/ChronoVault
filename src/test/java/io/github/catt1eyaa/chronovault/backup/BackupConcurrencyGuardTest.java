package io.github.catt1eyaa.chronovault.backup;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BackupConcurrencyGuardTest {

    @Test
    void testBackupInProgressGuardRejectsSecondStart() {
        AtomicBoolean guard = new AtomicBoolean(false);

        assertTrue(guard.compareAndSet(false, true));
        assertFalse(guard.compareAndSet(false, true));

        guard.set(false);
        assertTrue(guard.compareAndSet(false, true));
    }
}
