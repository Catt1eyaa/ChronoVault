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

import java.io.IOException;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 世界状态管理器
 *
 * <p>在备份期间控制世界保存状态，避免写入与备份并发导致的数据不一致。</p>
 *
 * <p>标准流程：</p>
 * <ol>
 *   <li>{@code prepareForBackup()}：执行 {@code save-all flush} 后执行 {@code save-off}</li>
 *   <li>执行备份逻辑</li>
 *   <li>{@code finishBackup()}：执行 {@code save-on}</li>
 * </ol>
 */
public class WorldStateManager {

    private final CommandRunner commandRunner;
    private final AtomicBoolean prepared;

    /**
     * 创建世界状态管理器
     *
     * @param commandRunner 命令执行器
     */
    public WorldStateManager(CommandRunner commandRunner) {
        this.commandRunner = Objects.requireNonNull(commandRunner, "commandRunner cannot be null");
        this.prepared = new AtomicBoolean(false);
    }

    /**
     * 进入备份前状态：先 flush，再关闭自动保存。
     *
     * @throws IOException 命令执行失败
     */
    public void prepareForBackup() throws IOException {
        if (prepared.get()) {
            return;
        }

        runCommand("save-all flush");
        try {
            runCommand("save-off");
            prepared.set(true);
        } catch (IOException e) {
            try {
                runCommand("save-on");
            } catch (IOException rollbackError) {
                e.addSuppressed(rollbackError);
            }
            throw e;
        }
    }

    /**
     * 结束备份状态：重新开启自动保存。
     *
     * @throws IOException 命令执行失败
     */
    public void finishBackup() throws IOException {
        if (!prepared.get()) {
            return;
        }

        runCommand("save-on");
        prepared.set(false);
    }

    /**
     * 当前是否处于备份保护状态（已执行 save-off）。
     */
    public boolean isPrepared() {
        return prepared.get();
    }

    private void runCommand(String command) throws IOException {
        try {
            commandRunner.run(command);
        } catch (Exception e) {
            throw new IOException("Failed to execute server command: " + command, e);
        }
    }

    /**
     * 服务端命令执行器。
     */
    @FunctionalInterface
    public interface CommandRunner {
        void run(String command) throws Exception;
    }
}
