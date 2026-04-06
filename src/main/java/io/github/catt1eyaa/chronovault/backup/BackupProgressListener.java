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

/**
 * 备份进度回调接口
 */
@FunctionalInterface
public interface BackupProgressListener {

    /**
     * 进度更新回调
     *
     * @param current 已完成的文件数量
     * @param total 总文件数量
     * @param file 当前处理的文件（相对路径）
     */
    void onProgress(int current, int total, String file);
}
