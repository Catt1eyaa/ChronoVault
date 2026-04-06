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

package io.github.catt1eyaa.chronovault.storage;

/**
 * 对象池统计信息（不可变数据类）
 * 
 * <p>此record类封装了对象池的统计数据，包括：</p>
 * <ul>
 *   <li>objectCount - 对象总数</li>
 *   <li>totalSizeBytes - 总大小（压缩后，字节）</li>
 *   <li>totalSizeOriginal - 总大小（原始数据，字节）</li>
 * </ul>
 * 
 * <p>通过 {@link ObjectPool#getStats()} 方法获取。</p>
 * 
 * @param objectCount 对象总数
 * @param totalSizeBytes 总大小（压缩后，字节）
 * @param totalSizeOriginal 总大小（原始数据，字节）
 */
public record ObjectPoolStats(
    long objectCount,
    long totalSizeBytes,
    long totalSizeOriginal
) {
    
    /**
     * 计算压缩率
     * 
     * @return 压缩率（0.0-1.0），如果原始大小为0则返回0.0
     */
    public double getCompressionRatio() {
        if (totalSizeOriginal == 0) {
            return 0.0;
        }
        return (double) totalSizeBytes / (double) totalSizeOriginal;
    }
    
    /**
     * 计算节省的空间（字节）
     * 
     * @return 节省的字节数（原始大小 - 压缩大小）
     */
    public long getSavedBytes() {
        return totalSizeOriginal - totalSizeBytes;
    }
    
    /**
     * 计算平均对象大小（压缩后）
     * 
     * @return 平均对象大小（字节），如果对象数为0则返回0
     */
    public long getAverageObjectSize() {
        if (objectCount == 0) {
            return 0;
        }
        return totalSizeBytes / objectCount;
    }
    
    /**
     * 格式化为人类可读的字符串
     * 
     * @return 格式化的统计信息
     */
    @Override
    public String toString() {
        return String.format(
            "ObjectPoolStats[objects=%d, compressed=%s, original=%s, ratio=%.2f%%, saved=%s]",
            objectCount,
            formatBytes(totalSizeBytes),
            formatBytes(totalSizeOriginal),
            getCompressionRatio() * 100,
            formatBytes(getSavedBytes())
        );
    }
    
    /**
     * 格式化字节数为人类可读格式（KB, MB, GB）
     * 
     * @param bytes 字节数
     * @return 格式化的字符串
     */
    private static String formatBytes(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        } else if (bytes < 1024 * 1024) {
            return String.format("%.2f KB", bytes / 1024.0);
        } else if (bytes < 1024 * 1024 * 1024) {
            return String.format("%.2f MB", bytes / (1024.0 * 1024.0));
        } else {
            return String.format("%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0));
        }
    }
}
