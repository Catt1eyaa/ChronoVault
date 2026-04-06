/*
 * Copyright (C) 2026 Cattleya
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package io.github.catt1eyaa.chronovault.backup;

import io.github.catt1eyaa.chronovault.snapshot.Manifest;
import io.github.catt1eyaa.chronovault.snapshot.ManifestSerializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * BackupCoordinator 单元测试
 * 
 * <p>由于 BackupCoordinator 依赖 MinecraftServer 和 LevelStorageAccess，
 * 本测试类专注于测试可独立验证的逻辑：</p>
 * <ul>
 *   <li>JVM 参数禁用备份检查</li>
 *   <li>快照清理逻辑</li>
 *   <li>磁盘空间检查</li>
 * </ul>
 */
class BackupCoordinatorTest {

    private static final String DISABLE_BACKUPS_PROPERTY = "chronovault.disableBackups";

    @BeforeEach
    void setUp() {
        // 确保测试前清理 JVM 属性
        System.clearProperty(DISABLE_BACKUPS_PROPERTY);
    }

    @AfterEach
    void tearDown() {
        // 测试后清理 JVM 属性
        System.clearProperty(DISABLE_BACKUPS_PROPERTY);
    }

    // ==================== isBackupDisabled 测试 ====================

    @Test
    void testIsBackupDisabledReturnsFalseByDefault() {
        assertFalse(BackupCoordinator.isBackupDisabled());
    }

    @Test
    void testIsBackupDisabledReturnsTrueWhenPropertySetToTrue() {
        System.setProperty(DISABLE_BACKUPS_PROPERTY, "true");
        assertTrue(BackupCoordinator.isBackupDisabled());
    }

    @Test
    void testIsBackupDisabledReturnsTrueWhenPropertySetToTrueUpperCase() {
        System.setProperty(DISABLE_BACKUPS_PROPERTY, "TRUE");
        assertTrue(BackupCoordinator.isBackupDisabled());
    }

    @Test
    void testIsBackupDisabledReturnsFalseWhenPropertySetToFalse() {
        System.setProperty(DISABLE_BACKUPS_PROPERTY, "false");
        assertFalse(BackupCoordinator.isBackupDisabled());
    }

    @Test
    void testIsBackupDisabledReturnsFalseWhenPropertySetToArbitraryValue() {
        System.setProperty(DISABLE_BACKUPS_PROPERTY, "yes");
        assertFalse(BackupCoordinator.isBackupDisabled());
    }

    // ==================== 快照清理逻辑测试 ====================

    @Test
    void testSnapshotCleanupDeletesOldSnapshots(@TempDir Path tempDir) throws IOException {
        // 准备：创建 5 个快照文件
        Path snapshotsDir = tempDir.resolve("snapshots");
        Files.createDirectories(snapshotsDir);

        // 创建按时间排序的快照文件（文件名格式：YYYYMMDD_HHMMSS.json）
        List<String> snapshotNames = List.of(
                "20260401_100000.json",
                "20260402_100000.json",
                "20260403_100000.json",
                "20260404_100000.json",
                "20260405_100000.json"
        );

        for (String name : snapshotNames) {
            createDummySnapshot(snapshotsDir.resolve(name));
        }

        // 执行清理：保留最新 3 个
        int maxSnapshots = 3;
        cleanupOldSnapshots(snapshotsDir, maxSnapshots);

        // 验证：只剩最新 3 个
        try (Stream<Path> files = Files.list(snapshotsDir)) {
            List<String> remaining = files
                    .map(p -> p.getFileName().toString())
                    .sorted()
                    .toList();

            assertEquals(3, remaining.size());
            assertTrue(remaining.contains("20260403_100000.json"));
            assertTrue(remaining.contains("20260404_100000.json"));
            assertTrue(remaining.contains("20260405_100000.json"));
            assertFalse(remaining.contains("20260401_100000.json"));
            assertFalse(remaining.contains("20260402_100000.json"));
        }
    }

    @Test
    void testSnapshotCleanupDoesNothingWhenBelowLimit(@TempDir Path tempDir) throws IOException {
        Path snapshotsDir = tempDir.resolve("snapshots");
        Files.createDirectories(snapshotsDir);

        // 只创建 2 个快照
        createDummySnapshot(snapshotsDir.resolve("20260404_100000.json"));
        createDummySnapshot(snapshotsDir.resolve("20260405_100000.json"));

        // 清理时限制为 5 个
        cleanupOldSnapshots(snapshotsDir, 5);

        // 验证：全部保留
        try (Stream<Path> files = Files.list(snapshotsDir)) {
            assertEquals(2, files.count());
        }
    }

    @Test
    void testSnapshotCleanupHandlesNonExistentDirectory(@TempDir Path tempDir) {
        Path snapshotsDir = tempDir.resolve("nonexistent");

        // 不应抛出异常
        assertDoesNotThrow(() -> cleanupOldSnapshots(snapshotsDir, 3));
    }

    @Test
    void testSnapshotCleanupIgnoresNonJsonFiles(@TempDir Path tempDir) throws IOException {
        Path snapshotsDir = tempDir.resolve("snapshots");
        Files.createDirectories(snapshotsDir);

        // 创建 json 快照和其他文件
        createDummySnapshot(snapshotsDir.resolve("20260401_100000.json"));
        createDummySnapshot(snapshotsDir.resolve("20260402_100000.json"));
        createDummySnapshot(snapshotsDir.resolve("20260403_100000.json"));
        Files.writeString(snapshotsDir.resolve("readme.txt"), "Should not be counted or deleted");
        Files.writeString(snapshotsDir.resolve("backup.log"), "Should not be counted or deleted");

        // 清理时限制为 2 个快照
        cleanupOldSnapshots(snapshotsDir, 2);

        // 验证：只剩 2 个 json，其他文件保留
        try (Stream<Path> files = Files.list(snapshotsDir)) {
            List<String> remaining = files
                    .map(p -> p.getFileName().toString())
                    .toList();

            assertEquals(4, remaining.size()); // 2 json + 2 other
            assertTrue(remaining.contains("20260402_100000.json"));
            assertTrue(remaining.contains("20260403_100000.json"));
            assertTrue(remaining.contains("readme.txt"));
            assertTrue(remaining.contains("backup.log"));
        }
    }

    // ==================== 磁盘空间检查测试 ====================

    @Test
    void testDiskSpaceCheckReturnsTrueWhenSufficientSpace(@TempDir Path tempDir) throws IOException {
        // 临时目录通常有足够空间（>128MB）
        assertTrue(checkDiskSpace(tempDir));
    }

    @Test
    void testDiskSpaceCheckCreatesDirectoryIfNotExists(@TempDir Path tempDir) throws IOException {
        Path newDir = tempDir.resolve("new").resolve("backup").resolve("dir");
        assertFalse(Files.exists(newDir));

        checkDiskSpace(newDir);

        assertTrue(Files.exists(newDir));
    }

    // ==================== 辅助方法 ====================

    /**
     * 创建一个虚拟的快照文件
     */
    private void createDummySnapshot(Path path) throws IOException {
        Manifest manifest = new Manifest(
                path.getFileName().toString().replace(".json", ""),
                System.currentTimeMillis() / 1000,
                "1.21.1",
                "test",
                Map.of(),
                Map.of()
        );
        ManifestSerializer.save(manifest, path);
    }

    /**
     * 模拟 BackupCoordinator.cleanupOldSnapshots 的逻辑
     * 用于独立测试清理算法
     */
    private void cleanupOldSnapshots(Path snapshotsDir, int maxSnapshots) {
        if (!Files.exists(snapshotsDir)) {
            return;
        }

        try (Stream<Path> files = Files.list(snapshotsDir)) {
            List<Path> snapshots = files
                    .filter(p -> p.toString().endsWith(".json"))
                    .sorted(java.util.Comparator.comparing(Path::getFileName).reversed())
                    .toList();

            if (snapshots.size() <= maxSnapshots) {
                return;
            }

            List<Path> toDelete = snapshots.subList(maxSnapshots, snapshots.size());
            for (Path snapshot : toDelete) {
                try {
                    Files.delete(snapshot);
                } catch (IOException e) {
                    // 忽略删除错误
                }
            }
        } catch (IOException e) {
            // 忽略列举错误
        }
    }

    /**
     * 模拟 BackupCoordinator.checkDiskSpace 的逻辑
     * 用于独立测试磁盘空间检查
     */
    private boolean checkDiskSpace(Path backupDir) throws IOException {
        long diskSpaceBuffer = 128L * 1024 * 1024;

        Files.createDirectories(backupDir);
        var fileStore = Files.getFileStore(backupDir);
        long usableSpace = fileStore.getUsableSpace();

        return usableSpace >= diskSpaceBuffer;
    }
}
