package io.github.catt1eyaa.chronovault.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;

import io.github.catt1eyaa.chronovault.backup.AsyncBackupService;
import io.github.catt1eyaa.chronovault.backup.BackupResult;
import io.github.catt1eyaa.chronovault.storage.BackupPathResolver;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.storage.LevelResource;

import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

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
 * </pre>
 *
 * <p>权限要求：OP level 4</p>
 */
public class ChronoVaultCommands {

    private static final String COMMAND_NAME = "chronovault";

    private AsyncBackupService backupService;
    private Path backupRoot;
    private int compressionLevel;

    /**
     * 创建命令注册器
     *
     * @param backupRoot 备份根目录
     * @param compressionLevel 压缩级别
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
                .requires(source -> source.hasPermission(4));

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
                        .executes(context -> executeInfo(
                                context.getSource(),
                                StringArgumentType.getString(context, "snapshot_id")
                        ))
                )
        );

        dispatcher.register(root);
    }

    /**
     * 初始化备份服务（服务器启动时调用）
     */
    public void initBackupService(Path backupRoot, int compressionLevel) {
        this.backupRoot = backupRoot;
        this.compressionLevel = compressionLevel;
    }

    /**
     * 关闭备份服务（服务器关闭时调用）
     */
    public void shutdownBackupService() {
        if (backupService != null) {
            backupService.close();
            backupService = null;
        }
    }

    /**
     * 获取当前世界的备份目录
     */
    private Path getWorldBackupDir(CommandSourceStack source) {
        Path worldPath = source.getServer().getWorldPath(LevelResource.ROOT);
        String worldName = worldPath.getFileName().toString();
        return BackupPathResolver.resolve(backupRoot, worldName);
    }

    /**
     * 获取或创建当前世界的备份服务
     */
    private AsyncBackupService getOrCreateBackupService(CommandSourceStack source) {
        Path worldBackupDir = getWorldBackupDir(source);
        
        // 如果服务不存在或指向不同的世界，重新创建
        if (backupService == null) {
            backupService = new AsyncBackupService(worldBackupDir, compressionLevel);
        }
        return backupService;
    }

    /**
     * 执行备份命令
     */
    private int executeBackup(CommandSourceStack source, String description) {
        if (backupRoot == null) {
            source.sendFailure(Component.literal("备份服务未初始化"));
            return 0;
        }

        try {
            source.getServer().executeBlocking(() -> source.getServer().saveEverything(true, false, true));
        } catch (Exception e) {
            source.sendFailure(Component.literal("备份前保存失败: " + e.getMessage()));
            return 0;
        }

        String desc = description != null ? description : "";
        String backupMessage = "开始备份" + (desc.isEmpty() ? "" : "：" + desc);
        source.sendSuccess(() -> Component.literal(backupMessage), true);

        AsyncBackupService service = getOrCreateBackupService(source);
        
        CompletableFuture<BackupResult> future = service.backupAsync(
                source.getServer().getWorldPath(LevelResource.ROOT),
                source.getServer().getServerVersion(),
                desc,
                (current, total, file) -> {
                    if (current % 10 == 0 || current == total) {
                        source.sendSuccess(
                                () -> Component.literal(String.format("进度: %d/%d - %s", current, total, file)),
                                false
                        );
                    }
                }
        );

        future.whenComplete((result, throwable) -> source.getServer().execute(() -> {
            if (throwable != null) {
                source.sendFailure(Component.literal("备份异常: " + throwable.getMessage()));
                return;
            }

            if (result == null) {
                source.sendFailure(Component.literal("备份异常: 返回结果为空"));
                return;
            }

            if (result.success()) {
                source.sendSuccess(() -> Component.literal(
                        String.format("备份完成！快照 ID: %s, 耗时: %dms",
                                result.snapshotId(),
                                result.stats().durationMs())
                ), true);
            } else {
                String firstError = result.errors().isEmpty() ? "未知错误" : result.errors().get(0);
                source.sendFailure(Component.literal(
                        String.format("备份失败（%d 个错误）: %s", result.errors().size(), firstError)
                ));
            }
        }));

        return 1;
    }

    /**
     * 执行列表命令
     */
    private int executeList(CommandSourceStack source) {
        if (backupRoot == null) {
            source.sendFailure(Component.literal("备份目录未初始化"));
            return 0;
        }
        Path worldBackupDir = getWorldBackupDir(source);
        return ListCommand.execute(source, worldBackupDir);
    }

    /**
     * 执行信息命令
     */
    private int executeInfo(CommandSourceStack source, String snapshotId) {
        if (backupRoot == null) {
            source.sendFailure(Component.literal("备份目录未初始化"));
            return 0;
        }
        Path worldBackupDir = getWorldBackupDir(source);
        return InfoCommand.execute(source, worldBackupDir, snapshotId);
    }
}
