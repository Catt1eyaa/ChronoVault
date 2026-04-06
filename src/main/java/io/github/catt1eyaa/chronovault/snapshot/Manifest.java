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

package io.github.catt1eyaa.chronovault.snapshot;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * 快照清单 - 记录某个时间点世界的完整状态
 * @param snapshotId 快照ID（格式：YYYYMMDD_HHmmss）
 * @param timestamp Unix时间戳（秒）
 * @param gameVersion Minecraft版本
 * @param description 描述（用户提供）
 * @param files 普通文件映射：相对路径 -> 对象池哈希
 * @param regions 区域文件映射：维度 -> (region文件名 -> RegionEntry)
 */
public record Manifest(
    String snapshotId,
    long timestamp,
    String gameVersion,
    String description,
    Map<String, String> files,           // 文件路径 -> 哈希
    Map<String, Map<String, RegionEntry>> regions  // 维度 -> (文件名 -> RegionEntry)
) {
    public Manifest {
        if (snapshotId == null || snapshotId.isEmpty()) {
            throw new IllegalArgumentException("Snapshot ID cannot be null or empty");
        }
        if (timestamp <= 0) {
            throw new IllegalArgumentException("Timestamp must be positive");
        }
        if (files == null) {
            files = Map.of();
        }
        if (regions == null) {
            regions = Map.of();
        }
    }
    
    /**
     * 生成默认的快照ID（基于时间戳）
     * 格式：YYYYMMDD_HHmmss
     */
    public static String generateSnapshotId(long timestamp) {
        java.time.Instant instant = java.time.Instant.ofEpochSecond(timestamp);
        java.time.ZonedDateTime zdt = instant.atZone(java.time.ZoneId.systemDefault());
        return zdt.format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
    }
    
    /**
     * 获取所有被引用的对象哈希（用于垃圾回收）
     */
    public Set<String> getAllReferencedHashes() {
        Set<String> hashes = new HashSet<>(files.values());
        
        regions.values().forEach(regionMap -> 
            regionMap.values().forEach(regionEntry ->
                regionEntry.chunks().stream()
                    .map(ChunkEntry::hash)
                    .filter(hash -> hash != null)
                    .forEach(hashes::add)
            )
        );
        
        return hashes;
    }
    
    /**
     * 获取统计信息
     */
    public ManifestStats getStats() {
        int totalChunks = regions.values().stream()
            .flatMap(m -> m.values().stream())
            .mapToInt(r -> r.chunks().size())
            .sum();
            
        int nonEmptyChunks = regions.values().stream()
            .flatMap(m -> m.values().stream())
            .mapToInt(RegionEntry::getNonEmptyChunkCount)
            .sum();
            
        return new ManifestStats(
            files.size(),
            regions.values().stream().mapToInt(Map::size).sum(),
            totalChunks,
            nonEmptyChunks,
            getAllReferencedHashes().size()
        );
    }
}
