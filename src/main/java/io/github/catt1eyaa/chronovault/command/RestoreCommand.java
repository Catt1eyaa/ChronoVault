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

        Path savesDir = source.getServer().getWorldPath(LevelResource.ROOT).getParent();
        if (savesDir == null) {
            source.sendFailure(Component.literal("无法解析存档目录"));
            return 0;
        }

        source.sendSuccess(() -> Component.literal("正在恢复快照: " + snapshotId), true);

        CompletableFuture<RestoreResult> future = CompletableFuture.supplyAsync(() -> {
            try {
                RestoreExecutor executor = new RestoreExecutor(worldBackupDir);
                return executor.restoreToNewWorld(snapshotId, savesDir, newWorldName != null ? newWorldName : worldName);
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
