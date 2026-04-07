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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.suggestion.SuggestionProvider;

import io.github.catt1eyaa.Config;
import io.github.catt1eyaa.chronovault.backup.BackupCoordinator;
import io.github.catt1eyaa.chronovault.backup.BackupResult;
import io.github.catt1eyaa.chronovault.storage.BackupPathResolver;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.storage.LevelResource;

/**
 * ChronoVault 命令注册器
 *
 * <p>注册所有 ChronoVault 命令到服务器命令分发器。</p>
 *
 * <p>命令结构：</p>
 * <pre>
 * /chronovault backup [description]  - 手动触发备份
 * /chronovault list                  - 列出所有快照
 * /chronovault info &lt;snapshot_id&gt;    - 显示快照详细信息
 * /chronovault restore &lt;snapshot_id&gt; [new_world_name] - 恢复快照
 * </pre>
 *
 * <p>权限要求：由配置文件控制（默认 OP level 2）</p>
 */
public class ChronoVaultCommands {

    private static final String COMMAND_NAME = "chronovault";
    private static volatile Path staticBackupRoot;

    public static final SuggestionProvider<CommandSourceStack> SNAPSHOT_SUGGESTIONS = (context, builder) -> {
        CommandSourceStack source = context.getSource();
        if (source == null || source.getServer() == null) {
            return builder.buildFuture();
        }

        Path backupRoot = staticBackupRoot;
        if (backupRoot == null) {
            return builder.buildFuture();
        }

        try {
            Path worldPath = source.getServer().getWorldPath(LevelResource.LEVEL_DATA_FILE).getParent();
            if (worldPath == null || worldPath.getFileName() == null) {
                return builder.buildFuture();
            }
            String worldName = worldPath.getFileName().toString();
            Path worldBackupDir = BackupPathResolver.resolve(backupRoot, worldName);
            if (Files.exists(worldBackupDir)) {
                Path snapshotsDir = worldBackupDir.resolve("snapshots");
                if (Files.exists(snapshotsDir)) {
                    try (Stream<Path> files = Files.list(snapshotsDir)) {
                        files.filter(p -> p.toString().endsWith(".json"))
                            .map(p -> p.getFileName().toString().replace(".json", ""))
                            .filter(id -> id.toLowerCase().startsWith(builder.getRemaining().toLowerCase()))
                            .forEach(id -> builder.suggest(id));
                    }
                }
            }
        } catch (IOException e) {
            // 忽略错误
        }

        return builder.buildFuture();
    };

    private volatile BackupCoordinator coordinator;
    private volatile Path backupRoot;

    /**
     * 创建命令注册器。
     */
    public ChronoVaultCommands() {
    }

    /**
     * 注册所有命令到命令分发器
     *
     * @param dispatcher 命令分发器
     */
    public void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal(COMMAND_NAME)
                .requires(source -> source.hasPermission(Config.getCommandPermissionLevel()));

        root.then(Commands.literal("backup")
                .executes(context -> executeBackup(context.getSource(), null))
                .then(Commands.argument("description", StringArgumentType.greedyString())
                        .executes(context -> executeBackup(
                                context.getSource(),
                                StringArgumentType.getString(context, "description")
                        ))
                )
        );

        root.then(Commands.literal("list")
                .executes(context -> executeList(context.getSource()))
        );

        root.then(Commands.literal("info")
                .then(Commands.argument("snapshot_id", StringArgumentType.string())
                        .suggests(SNAPSHOT_SUGGESTIONS)
                        .executes(context -> executeInfo(
                                context.getSource(),
                                StringArgumentType.getString(context, "snapshot_id")
                        ))
                )
        );

        root.then(Commands.literal("restore")
                .then(Commands.argument("snapshot_id", StringArgumentType.string())
                        .suggests(SNAPSHOT_SUGGESTIONS)
                        .executes(context -> executeRestore(
                                context.getSource(),
                                StringArgumentType.getString(context, "snapshot_id"),
                                null
                        ))
                        .then(Commands.argument("new_world_name", StringArgumentType.string())
                                .executes(context -> executeRestore(
                                        context.getSource(),
                                        StringArgumentType.getString(context, "snapshot_id"),
                                        StringArgumentType.getString(context, "new_world_name")
                                ))
                        )
                )
        );

        dispatcher.register(root);
    }

    /**
     * 设置备份协调器
     *
     * @param coordinator 备份协调器
     * @param backupRoot  备份根目录
     */
    public void setCoordinator(BackupCoordinator coordinator, Path backupRoot) {
        this.coordinator = coordinator;
        this.backupRoot = backupRoot;
        staticBackupRoot = backupRoot;
    }

    /**
     * 关闭命令处理器（服务器关闭时调用）
     */
    public void shutdown() {
        // 协调器的关闭由 ChronoVault 主类管理
        this.coordinator = null;
        this.backupRoot = null;
    }

    /**
     * 获取当前世界的备份目录。
     *
     * @param source 命令源
     * @return 当前世界的备份目录
     */
    private Path getWorldBackupDir(CommandSourceStack source) {
        Path worldPath = resolveCurrentWorldDir(source);
        String worldName = worldPath.getFileName().toString();
        return BackupPathResolver.resolve(backupRoot, worldName);
    }

    private Path resolveCurrentWorldDir(CommandSourceStack source) {
        Path levelDatPath = source.getServer().getWorldPath(LevelResource.LEVEL_DATA_FILE);
        Path worldDir = levelDatPath.getParent();
        if (worldDir == null || worldDir.getFileName() == null) {
            throw new IllegalStateException("Unable to resolve current world directory from level.dat path: " + levelDatPath);
        }
        return worldDir;
    }

    /**
     * 执行备份命令。
     *
     * @param source      命令源
     * @param description 备份描述
     * @return 命令结果
     */
    private int executeBackup(CommandSourceStack source, String description) {
        if (coordinator == null) {
            source.sendFailure(Component.translatable("chrono_vault.command.backup.service_not_initialized"));
            return 0;
        }

        if (BackupCoordinator.isBackupDisabled()) {
            source.sendFailure(Component.translatable("chrono_vault.command.backup.disabled"));
            return 0;
        }

        if (coordinator.isBackupInProgress()) {
            source.sendFailure(Component.translatable("chrono_vault.command.backup.in_progress"));
            return 0;
        }

        if (description != null && !description.isEmpty()) {
            source.sendSuccess(() -> Component.translatable("chrono_vault.command.backup.starting_with_desc", description), true);
        } else {
            source.sendSuccess(() -> Component.translatable("chrono_vault.command.backup.starting"), true);
        }

        // 使用 BackupCoordinator 执行异步备份
        CompletableFuture<BackupResult> future = coordinator.backupAsync(
                description != null ? description : "",
                (current, total, file) -> {
                    if (current % 10 == 0 || current == total) {
                        source.getServer().execute(() -> source.sendSuccess(
                                () -> Component.translatable("chrono_vault.command.backup.progress", 
                                        String.valueOf(current), String.valueOf(total), file),
                                false
                        ));
                    }
                }
        );

        future.whenComplete((result, throwable) -> source.getServer().execute(() -> {
            if (throwable != null) {
                source.sendFailure(Component.translatable("chrono_vault.command.backup.exception", throwable.getMessage()));
                return;
            }

            if (result == null) {
                source.sendFailure(Component.translatable("chrono_vault.command.backup.exception_null_result"));
                return;
            }

            if (result.success()) {
                source.sendSuccess(() -> Component.translatable("chrono_vault.command.backup.success",
                        result.snapshotId(),
                        String.valueOf(result.stats().durationMs())
                ), true);
            } else {
                String firstError = result.errors().isEmpty() 
                        ? Component.translatable("chrono_vault.command.backup.unknown_error").getString() 
                        : result.errors().get(0);
                source.sendFailure(Component.translatable("chrono_vault.command.backup.failed", 
                        String.valueOf(result.errors().size()), firstError
                ));
            }
        }));

        return 1;
    }

    /**
     * 执行列表命令。
     *
     * @param source 命令源
     * @return 命令结果
     */
    private int executeList(CommandSourceStack source) {
        if (backupRoot == null) {
            source.sendFailure(Component.translatable("chrono_vault.command.error.backup_root_not_initialized"));
            return 0;
        }
        Path worldBackupDir = getWorldBackupDir(source);
        return ListCommand.execute(source, worldBackupDir);
    }

    /**
     * 执行信息命令。
     *
     * @param source     命令源
     * @param snapshotId 快照 ID
     * @return 命令结果
     */
    private int executeInfo(CommandSourceStack source, String snapshotId) {
        if (backupRoot == null) {
            source.sendFailure(Component.translatable("chrono_vault.command.error.backup_root_not_initialized"));
            return 0;
        }
        Path worldBackupDir = getWorldBackupDir(source);
        return InfoCommand.execute(source, worldBackupDir, snapshotId);
    }

    private int executeRestore(CommandSourceStack source, String snapshotId, String newWorldName) {
        return RestoreCommand.execute(source, backupRoot, snapshotId, newWorldName);
    }
}
