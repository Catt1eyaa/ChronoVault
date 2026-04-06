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

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * 备份操作的结果
 * 
 * <p>包含备份成功状态、快照 ID、统计信息和错误列表。
 */
public record BackupResult(
    boolean success,
    String snapshotId,
    BackupStats stats,
    List<String> errors
) {
    /**
     * 创建 BackupResult 实例
     * 
     * @param success 备份是否成功
     * @param snapshotId 快照 ID（失败时可为 null）
     * @param stats 备份统计信息（不能为 null）
     * @param errors 错误列表（不能为 null，成功时为空列表）
     * @throws IllegalArgumentException 如果 stats 或 errors 为 null
     */
    public BackupResult {
        Objects.requireNonNull(stats, "stats cannot be null");
        Objects.requireNonNull(errors, "errors cannot be null");
        errors = List.copyOf(errors); // 不可变副本
    }
    
    /**
     * 创建成功的备份结果
     * 
     * @param snapshotId 快照 ID
     * @param stats 备份统计信息
     * @return 成功的 BackupResult
     */
    public static BackupResult success(String snapshotId, BackupStats stats) {
        Objects.requireNonNull(snapshotId, "snapshotId cannot be null");
        return new BackupResult(true, snapshotId, stats, Collections.emptyList());
    }
    
    /**
     * 创建失败的备份结果
     * 
     * @param stats 备份统计信息
     * @param errors 错误列表
     * @return 失败的 BackupResult
     */
    public static BackupResult failure(BackupStats stats, List<String> errors) {
        Objects.requireNonNull(errors, "errors cannot be null");
        if (errors.isEmpty()) {
            throw new IllegalArgumentException("errors cannot be empty for failure result");
        }
        return new BackupResult(false, null, stats, errors);
    }
    
    /**
     * 备份统计信息
     */
    public static record BackupStats(
        int totalFiles,
        int totalRegions,
        int totalChunks,
        int uniqueObjects,
        long originalSize,
        long compressedSize,
        long durationMs
    ) {
        /**
         * 创建空的统计信息
         */
        public BackupStats() {
            this(0, 0, 0, 0, 0L, 0L, 0L);
        }
        
        /**
         * 计算压缩率（百分比）
         * 
         * @return 压缩率 (0-100)，如果原始大小为 0 则返回 0
         */
        public double compressionRatio() {
            if (originalSize == 0) {
                return 0.0;
            }
            return 100.0 * (1.0 - (double) compressedSize / originalSize);
        }
        
        /**
         * 格式化为可读字符串
         */
        @Override
        public String toString() {
            return String.format(
                "BackupStats[files=%d, regions=%d, chunks=%d, objects=%d, " +
                "original=%d bytes, compressed=%d bytes (%.2f%%), duration=%dms]",
                totalFiles, totalRegions, totalChunks, uniqueObjects,
                originalSize, compressedSize, compressionRatio(), durationMs
            );
        }
    }
    
    /**
     * 格式化为可读字符串
     */
    @Override
    public String toString() {
        if (success) {
            return String.format("BackupResult[SUCCESS, snapshot=%s, %s]", snapshotId, stats);
        } else {
            return String.format("BackupResult[FAILURE, errors=%d, %s]", errors.size(), stats);
        }
    }
}
