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

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.storage.LevelResource;

/**
 * ChronoVault 恢复命令
 */
public class RestoreCommand {

    public static int execute(CommandSourceStack source, Path backupRoot, String snapshotId, String newWorldName) {
        if (backupRoot == null) {
            source.sendFailure(Component.translatable("chrono_vault.command.error.backup_root_not_initialized"));
            return 0;
        }

        Path worldDir = source.getServer().getWorldPath(LevelResource.LEVEL_DATA_FILE).getParent();
        String worldName = worldDir.getFileName().toString();
        Path worldBackupDir = BackupPathResolver.resolve(backupRoot, worldName);

        if (!Files.exists(worldBackupDir)) {
            source.sendFailure(Component.translatable("chrono_vault.command.restore.world_backup_not_found", worldName));
            return 0;
        }

        // 验证快照是否存在
        Path snapshotFile = worldBackupDir.resolve("snapshots").resolve(snapshotId + ".json");
        if (!Files.exists(snapshotFile)) {
            source.sendFailure(Component.translatable("chrono_vault.command.restore.snapshot_not_found", snapshotId));
            return 0;
        }

        // 验证快照文件是否可读
        try {
            ManifestSerializer.load(snapshotFile);
        } catch (IOException e) {
            source.sendFailure(Component.translatable("chrono_vault.command.restore.snapshot_corrupted", e.getMessage()));
            return 0;
        }

        // 对 newWorldName 参数进行校验
        String sanitizedNewWorldName;
        if (newWorldName != null) {
            sanitizedNewWorldName = BackupPathResolver.sanitizeWorldName(newWorldName);
            if (sanitizedNewWorldName.isEmpty()) {
                source.sendFailure(Component.translatable("chrono_vault.command.restore.invalid_world_name", newWorldName));
                return 0;
            }
        } else {
            sanitizedNewWorldName = worldName;
        }

        // 获取 saves 目录：从当前世界目录向上一级
        // worldDir 是 saves/test，savesDir 应该是 saves/
        Path savesDir = worldDir.getParent();
        if (savesDir == null) {
            source.sendFailure(Component.translatable("chrono_vault.command.restore.saves_dir_error"));
            return 0;
        }

        source.sendSuccess(() -> Component.translatable("chrono_vault.command.restore.starting", snapshotId), true);

        // 使用 ForkJoinPool.commonPool() 而不是静态 EXECUTOR，避免生命周期管理问题
        CompletableFuture<RestoreResult> future = CompletableFuture.supplyAsync(() -> {
            try {
                RestoreExecutor executor = new RestoreExecutor(worldBackupDir);
                return executor.restoreToNewWorld(snapshotId, savesDir, sanitizedNewWorldName);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });

        future.whenComplete((result, throwable) -> source.getServer().execute(() -> {
            if (throwable != null) {
                source.sendFailure(Component.translatable("chrono_vault.command.restore.failed_exception", throwable.getMessage()));
                return;
            }

            if (result == null) {
                source.sendFailure(Component.translatable("chrono_vault.command.restore.failed_null_result"));
                return;
            }

            source.sendSuccess(() -> Component.translatable("chrono_vault.command.restore.success",
                    result.targetWorldName(),
                    result.targetWorldPath()
            ), true);
        }));

        return 1;
    }
}
