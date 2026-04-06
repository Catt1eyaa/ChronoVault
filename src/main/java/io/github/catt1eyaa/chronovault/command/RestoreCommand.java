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

package io.github.catt1eyaa.chronovault.command;

import io.github.catt1eyaa.chronovault.restore.RestoreExecutor;
import io.github.catt1eyaa.chronovault.restore.RestoreResult;
import io.github.catt1eyaa.chronovault.snapshot.ManifestSerializer;
import io.github.catt1eyaa.chronovault.storage.BackupPathResolver;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.storage.LevelResource;

/**
 * ChronoVault 恢复命令
 */
public class RestoreCommand {

    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();

    /**
     * 关闭执行器（服务器停止时调用）
     */
    public static void shutdown() {
        EXECUTOR.shutdown();
    }

    public static int execute(CommandSourceStack source, Path backupRoot, String snapshotId, String newWorldName) {
        if (backupRoot == null) {
            source.sendFailure(Component.literal("备份目录未初始化"));
            return 0;
        }

        Path worldDir = source.getServer().getWorldPath(LevelResource.LEVEL_DATA_FILE).getParent();
        String worldName = worldDir.getFileName().toString();
        Path worldBackupDir = BackupPathResolver.resolve(backupRoot, worldName);

        if (!Files.exists(worldBackupDir)) {
            source.sendFailure(Component.literal("未找到世界 " + worldName + " 的备份"));
            return 0;
        }

        // 验证快照是否存在
        Path snapshotFile = worldBackupDir.resolve("snapshots").resolve(snapshotId + ".json");
        if (!Files.exists(snapshotFile)) {
            source.sendFailure(Component.literal("快照不存在: " + snapshotId));
            return 0;
        }

        // 验证快照文件是否可读
        try {
            ManifestSerializer.load(snapshotFile);
        } catch (IOException e) {
            source.sendFailure(Component.literal("快照文件损坏: " + e.getMessage()));
            return 0;
        }

        // 对 newWorldName 参数进行校验
        String sanitizedNewWorldName;
        if (newWorldName != null) {
            sanitizedNewWorldName = BackupPathResolver.sanitizeWorldName(newWorldName);
            if (sanitizedNewWorldName.isEmpty()) {
                source.sendFailure(Component.literal("无效的世界名称: " + newWorldName));
                return 0;
            }
        } else {
            sanitizedNewWorldName = worldName;
        }

        Path savesDir = source.getServer().getWorldPath(LevelResource.ROOT).getParent();
        if (savesDir == null) {
            source.sendFailure(Component.literal("无法解析存档目录"));
            return 0;
        }

        source.sendSuccess(() -> Component.literal("正在恢复快照: " + snapshotId), true);

        CompletableFuture<RestoreResult> future = CompletableFuture.supplyAsync(() -> {
            try {
                RestoreExecutor executor = new RestoreExecutor(worldBackupDir);
                return executor.restoreToNewWorld(snapshotId, savesDir, sanitizedNewWorldName);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }, EXECUTOR);

        future.whenComplete((result, throwable) -> source.getServer().execute(() -> {
            if (throwable != null) {
                source.sendFailure(Component.literal("恢复失败: " + throwable.getMessage()));
                return;
            }

            if (result == null) {
                source.sendFailure(Component.literal("恢复失败: 返回结果为空"));
                return;
            }

            source.sendSuccess(() -> Component.literal(
                    String.format("恢复完成！新世界: %s (路径: %s)",
                            result.targetWorldName(),
                            result.targetWorldPath())
            ), true);
        }));

        return 1;
    }
}
