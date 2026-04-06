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

import io.github.catt1eyaa.chronovault.region.AnvilReader;
import io.github.catt1eyaa.chronovault.region.ChunkData;
import io.github.catt1eyaa.chronovault.region.ChunkLocation;
import io.github.catt1eyaa.chronovault.snapshot.ChunkEntry;
import io.github.catt1eyaa.chronovault.snapshot.Manifest;
import io.github.catt1eyaa.chronovault.snapshot.ManifestSerializer;
import io.github.catt1eyaa.chronovault.snapshot.RegionEntry;
import io.github.catt1eyaa.chronovault.storage.ObjectPool;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.function.BooleanSupplier;

/**
 * 备份执行器 - 负责执行完整的世界备份流程
 * 
 * <p>备份流程：</p>
 * <ol>
 *   <li>扫描世界目录（使用 WorldScanner）</li>
 *   <li>处理普通文件（哈希 + 存储到 ObjectPool）</li>
 *   <li>处理 region 文件（解析 chunk → 哈希 + 存储）</li>
 *   <li>生成 Manifest（快照清单）</li>
 *   <li>保存 Manifest 到备份目录</li>
 * </ol>
 * 
 * <p>线程安全：此类本身不是线程安全的，不应该并发执行备份操作。
 * 但内部使用的 ObjectPool 是线程安全的。</p>
 */
public class BackupExecutor {
    
    private final ObjectPool objectPool;
    private final Path backupDir;
    
    /**
     * 创建备份执行器
     * 
     * @param backupDir 备份根目录（包含 objects/ 和 snapshots/）
     * @param compressionLevel Zstd 压缩级别（1-22）
     * @throws IllegalArgumentException 如果参数无效
     */
    public BackupExecutor(Path backupDir, int compressionLevel) {
        Objects.requireNonNull(backupDir, "backupDir cannot be null");
        this.backupDir = backupDir;
        this.objectPool = new ObjectPool(backupDir.resolve("objects"), compressionLevel);
    }
    
    /**
     * 使用默认压缩级别创建备份执行器
     * 
     * @param backupDir 备份根目录
     */
    public BackupExecutor(Path backupDir) {
        this(backupDir, 3);
    }
    
    /**
     * 执行完整备份
     * 
     * @param worldDir 世界目录（例如：saves/MyWorld）
     * @param gameVersion Minecraft 版本（例如："1.21.1"）
     * @param description 备份描述（可选，可为 null）
     * @return 备份结果
     */
    public BackupResult execute(Path worldDir, String gameVersion, String description) {
        return execute(worldDir, gameVersion, description, null, () -> false);
    }

    /**
     * 执行完整备份（支持进度回调和取消）
     *
     * @param worldDir 世界目录
     * @param gameVersion Minecraft 版本
     * @param description 备份描述
     * @param progressListener 进度回调（可为 null）
     * @param cancelRequested 取消检查函数（可为 null）
     * @return 备份结果
     */
    public BackupResult execute(
            Path worldDir,
            String gameVersion,
            String description,
            BackupProgressListener progressListener,
            BooleanSupplier cancelRequested
    ) {
        Objects.requireNonNull(worldDir, "worldDir cannot be null");
        Objects.requireNonNull(gameVersion, "gameVersion cannot be null");
        if (cancelRequested == null) {
            cancelRequested = () -> false;
        }
        
        long startTime = System.currentTimeMillis();
        List<String> errors = new ArrayList<>();
        
        // 统计信息
        int totalFiles = 0;
        int totalRegions = 0;
        int totalChunks = 0;
        int uniqueObjects = 0;
        long originalSize = 0;
        long compressedSize = 0;
        Set<String> countedCompressedHashes = new HashSet<>();
        
        try {
            // 1. 扫描世界目录
            WorldFiles worldFiles;
            try {
                worldFiles = WorldScanner.scan(worldDir);
            } catch (IOException e) {
                errors.add("Failed to scan world directory: " + e.getMessage());
                return createFailureResult(errors, startTime);
            }

            int totalWork = worldFiles.getTotalFileCount();
            int completedWork = 0;
            
            // 2. 生成快照 ID 和时间戳
            long timestamp = Instant.now().getEpochSecond();
            String snapshotId = Manifest.generateSnapshotId(timestamp);
            
            // 3. 处理普通文件
            Map<String, String> fileHashes = new HashMap<>();
            for (Map.Entry<String, Path> entry : worldFiles.regularFiles().entrySet()) {
                checkCancelled(cancelRequested);

                String relativePath = entry.getKey();
                Path filePath = entry.getValue();
                
                try {
                    byte[] fileData = Files.readAllBytes(filePath);
                    String hash = objectPool.write(fileData);
                    fileHashes.put(relativePath, hash);
                    
                    totalFiles++;
                    originalSize += fileData.length;
                    
                    // 计算压缩后的大小（检查对象是否已存在）
                    Path objectPath = getObjectPath(hash);
                    if (countedCompressedHashes.add(hash) && Files.exists(objectPath)) {
                        compressedSize += Files.size(objectPath);
                    }
                } catch (IOException e) {
                    if (isVanishedFileError(e)) {
                        continue;
                    }
                    errors.add("Failed to backup file " + relativePath + ": " + e.getMessage());
                }

                completedWork++;
                reportProgress(progressListener, completedWork, totalWork, relativePath);
            }

            if (!errors.isEmpty()) {
                return createFailureResultWithStats(
                    errors,
                    startTime,
                    totalFiles,
                    totalRegions,
                    totalChunks,
                    countedCompressedHashes.size(),
                    originalSize,
                    compressedSize
                );
            }
            
            // 4. 处理 region 文件
            Map<String, Map<String, RegionEntry>> regionMap = new HashMap<>();
            
            for (Map.Entry<String, Map<String, Path>> dimensionEntry : worldFiles.regionFiles().entrySet()) {
                String dimension = dimensionEntry.getKey();
                Map<String, Path> regionFiles = dimensionEntry.getValue();
                Map<String, RegionEntry> dimensionRegions = new HashMap<>();
                
                for (Map.Entry<String, Path> regionEntry : regionFiles.entrySet()) {
                    checkCancelled(cancelRequested);

                    String regionFileName = regionEntry.getKey();
                    Path regionPath = regionEntry.getValue();
                    
                    try {
                        // 读取 region 文件的所有 chunk
                        List<ChunkLocation> allChunks = AnvilReader.readHeader(regionPath);
                        boolean zeroByteRegion = AnvilReader.isZeroByteRegion(regionPath);
                        List<ChunkEntry> chunkEntries = new ArrayList<>();
                        
                        for (ChunkLocation loc : allChunks) {
                            if (loc.isEmpty()) {
                                // 空 chunk，记录为 null
                                chunkEntries.add(new ChunkEntry(loc.x(), loc.z(), null));
                            } else {
                                // 读取 chunk 数据
                                ChunkData chunkData = AnvilReader.readChunk(regionPath, loc);
                                if (chunkData != null) {
                                    // 写入对象池
                                    String hash = objectPool.write(chunkData.rawData());
                                    chunkEntries.add(new ChunkEntry(loc.x(), loc.z(), hash));
                                    
                                    totalChunks++;
                                    originalSize += chunkData.rawData().length;
                                    
                                    // 计算压缩后的大小
                                    Path objectPath = getObjectPath(hash);
                                    if (countedCompressedHashes.add(hash) && Files.exists(objectPath)) {
                                        compressedSize += Files.size(objectPath);
                                    }
                                } else {
                                    chunkEntries.add(new ChunkEntry(loc.x(), loc.z(), null));
                                }
                            }
                        }
                        
                        // 创建 RegionEntry
                        String regionFormat = zeroByteRegion
                                ? AnvilReader.zeroByteRegionFormat()
                                : "anvil";
                        RegionEntry entry = new RegionEntry(regionFileName, regionFormat, chunkEntries);
                        dimensionRegions.put(regionFileName, entry);
                        totalRegions++;
                        
                    } catch (IOException e) {
                        if (isVanishedFileError(e)) {
                            continue;
                        }
                        errors.add("Failed to backup region " + regionFileName + ": " + e.getMessage());
                    }

                    completedWork++;
                    reportProgress(progressListener, completedWork, totalWork, regionFileName);
                }
                
                if (!dimensionRegions.isEmpty()) {
                    regionMap.put(dimension, dimensionRegions);
                }
            }

            if (!errors.isEmpty()) {
                return createFailureResultWithStats(
                    errors,
                    startTime,
                    totalFiles,
                    totalRegions,
                    totalChunks,
                    countedCompressedHashes.size(),
                    originalSize,
                    compressedSize
                );
            }
            
            // 5. 创建 Manifest
            Manifest manifest = new Manifest(
                snapshotId,
                timestamp,
                gameVersion,
                description != null ? description : "",
                fileHashes,
                regionMap
            );
            
            // 计算唯一对象数
            uniqueObjects = manifest.getAllReferencedHashes().size();
            
            // 6. 保存 Manifest
            try {
                Path snapshotsDir = backupDir.resolve("snapshots");
                Files.createDirectories(snapshotsDir);
                
                Path manifestPath = snapshotsDir.resolve(snapshotId + ".json");
                ManifestSerializer.save(manifest, manifestPath);
            } catch (IOException e) {
                errors.add("Failed to save manifest: " + e.getMessage());
                return createFailureResult(errors, startTime);
            }
            
            // 7. 生成结果
            long duration = System.currentTimeMillis() - startTime;
            
            BackupResult.BackupStats stats = new BackupResult.BackupStats(
                totalFiles,
                totalRegions,
                totalChunks,
                uniqueObjects,
                originalSize,
                compressedSize,
                duration
            );
            
            return BackupResult.success(snapshotId, stats);
            
        } catch (CancellationException e) {
            errors.add("Backup cancelled");
            return createFailureResult(errors, startTime);
        } catch (Exception e) {
            errors.add("Unexpected error: " + e.getMessage());
            return createFailureResult(errors, startTime);
        }
    }

    private void checkCancelled(BooleanSupplier cancelRequested) {
        if (Thread.currentThread().isInterrupted() || cancelRequested.getAsBoolean()) {
            throw new CancellationException("Backup was cancelled");
        }
    }

    private void reportProgress(BackupProgressListener listener, int current, int total, String file) {
        if (listener == null) {
            return;
        }
        listener.onProgress(current, total, file);
    }

    static boolean isVanishedFileError(IOException error) {
        Throwable current = error;
        while (current != null) {
            if (current instanceof NoSuchFileException) {
                return true;
            }
            if (current instanceof UncheckedIOException unchecked && unchecked.getCause() instanceof NoSuchFileException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
    
    /**
     * 创建失败的备份结果
     */
    private BackupResult createFailureResult(List<String> errors, long startTime) {
        long duration = System.currentTimeMillis() - startTime;
        BackupResult.BackupStats stats = new BackupResult.BackupStats(
            0, 0, 0, 0, 0L, 0L, duration
        );
        return BackupResult.failure(stats, errors);
    }

    private BackupResult createFailureResultWithStats(
            List<String> errors,
            long startTime,
            int totalFiles,
            int totalRegions,
            int totalChunks,
            int uniqueObjects,
            long originalSize,
            long compressedSize
    ) {
        long duration = System.currentTimeMillis() - startTime;
        BackupResult.BackupStats stats = new BackupResult.BackupStats(
            totalFiles,
            totalRegions,
            totalChunks,
            uniqueObjects,
            originalSize,
            compressedSize,
            duration
        );
        return BackupResult.failure(stats, errors);
    }
    
    /**
     * 获取对象池中对象的路径（用于计算大小）
     * 
     * @param hash 对象哈希（64字符十六进制）
     * @return 对象文件路径
     */
    private Path getObjectPath(String hash) {
        String prefix = hash.substring(0, 2);
        String suffix = hash.substring(2);
        return backupDir.resolve("objects").resolve(prefix).resolve(suffix);
    }
}
