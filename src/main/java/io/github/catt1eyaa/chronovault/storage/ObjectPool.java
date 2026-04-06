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

import io.github.catt1eyaa.chronovault.util.CompressionUtil;
import io.github.catt1eyaa.chronovault.util.HashUtil;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * 对象池（Object Pool）- 内容寻址存储系统的核心组件。
 * 
 * <p>对象池使用BLAKE3哈希作为键，以Git风格的2级目录结构存储压缩后的数据块。
 * 目录结构示例：</p>
 * 
 * <pre>
 * backups/
 * └── objects/
 *     ├── a1/           # 哈希的前2个字符
 *     │   ├── b2c3d4... # 剩余62个字符
 *     │   └── f5e6d7...
 *     └── ff/
 *         └── 123456...
 * </pre>
 * 
 * <p>主要功能：</p>
 * <ul>
 *   <li>内容去重 - 相同内容只存储一次</li>
 *   <li>数据压缩 - 使用Zstd压缩节省空间</li>
 *   <li>线程安全 - 使用临时文件+原子重命名确保并发安全</li>
 *   <li>哈希验证 - 数据完整性保证</li>
 * </ul>
 * 
 * <p>线程安全：所有公共方法都是线程安全的。多个线程可以并发读写不同对象，
 * 对同一对象的并发写入会自动去重（只有一个成功，其他跳过）。</p>
 */
public class ObjectPool {
    
    /**
     * 对象池根目录（通常是 backups/objects/）
     */
    private final Path objectsDir;
    
    /**
     * Zstd压缩级别（1-22，默认3）
     */
    private final int compressionLevel;
    
    /**
     * 临时文件后缀
     */
    private static final String TEMP_SUFFIX = ".tmp";
    
    /**
     * 哈希前缀长度（用于目录名）
     */
    private static final int PREFIX_LENGTH = 2;
    
    /**
     * 构造函数
     * 
     * @param objectsDir 对象池根目录（通常是 backups/objects/）
     * @param compressionLevel Zstd压缩级别（1-22，推荐3）
     * @throws IllegalArgumentException 如果参数无效
     */
    public ObjectPool(Path objectsDir, int compressionLevel) {
        if (objectsDir == null) {
            throw new IllegalArgumentException("Objects directory cannot be null");
        }
        if (!CompressionUtil.isValidLevel(compressionLevel)) {
            throw new IllegalArgumentException(
                "Invalid compression level: " + compressionLevel + 
                " (must be between " + CompressionUtil.MIN_LEVEL + 
                " and " + CompressionUtil.MAX_LEVEL + ")"
            );
        }
        
        this.objectsDir = objectsDir;
        this.compressionLevel = compressionLevel;
    }
    
    /**
     * 使用默认压缩级别构造对象池
     * 
     * @param objectsDir 对象池根目录
     */
    public ObjectPool(Path objectsDir) {
        this(objectsDir, CompressionUtil.DEFAULT_LEVEL);
    }
    
    /**
     * 写入数据到对象池（如果不存在）
     * 
     * <p>写入流程：</p>
     * <ol>
     *   <li>计算数据的BLAKE3哈希</li>
     *   <li>检查对象是否已存在</li>
     *   <li>如果存在，直接返回哈希（去重）</li>
     *   <li>如果不存在：压缩数据 → 写入临时文件 → 原子重命名</li>
     * </ol>
     * 
     * @param data 原始数据（不能为null）
     * @return 数据的BLAKE3哈希（64字符十六进制）
     * @throws IOException 如果写入失败
     * @throws IllegalArgumentException 如果data为null
     */
    public String write(byte[] data) throws IOException {
        if (data == null) {
            throw new IllegalArgumentException("Data cannot be null");
        }
        
        // 1. 计算BLAKE3哈希
        String hash = HashUtil.hashToHex(data);
        
        // 2. 检查对象是否已存在（去重）
        if (exists(hash)) {
            return hash;
        }
        
        // 3. 压缩数据
        byte[] compressedData = CompressionUtil.compress(data, compressionLevel);
        
        // 4. 获取目标路径和临时路径
        Path objectPath = getObjectPath(hash);
        Path parentDir = objectPath.getParent();
        
        // 5. 确保父目录存在
        if (!Files.exists(parentDir)) {
            Files.createDirectories(parentDir);
        }
        
        // 6. 写入临时文件
        Path tempPath = objectPath.resolveSibling(objectPath.getFileName() + TEMP_SUFFIX);
        Files.write(tempPath, compressedData, 
                    StandardOpenOption.CREATE, 
                    StandardOpenOption.TRUNCATE_EXISTING);
        
        // 7. 原子重命名到最终路径（线程安全）
        // 如果目标文件已存在（并发写入情况），会覆盖临时文件
        // 这样可以确保即使多个线程同时写入相同对象，也只有一个版本
        try {
            Files.move(tempPath, objectPath, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            // 如果重命名失败（可能是另一个线程已经创建了该对象），
            // 检查目标文件是否存在
            if (Files.exists(objectPath)) {
                // 目标文件已存在，删除临时文件
                Files.deleteIfExists(tempPath);
                return hash;
            }
            // 真实的错误，重新抛出
            throw e;
        }
        
        return hash;
    }
    
    /**
     * 从对象池读取数据
     * 
     * <p>读取流程：</p>
     * <ol>
     *   <li>根据哈希计算文件路径</li>
     *   <li>读取压缩数据</li>
     *   <li>解压数据</li>
     *   <li>返回原始数据</li>
     * </ol>
     * 
     * @param hash BLAKE3哈希（64字符十六进制）
     * @return 原始数据（解压后）
     * @throws IOException 如果读取失败或对象不存在
     * @throws IllegalArgumentException 如果哈希格式无效
     */
    public byte[] read(String hash) throws IOException {
        validateHash(hash);
        
        // 1. 获取对象路径
        Path objectPath = getObjectPath(hash);
        
        // 2. 检查文件是否存在
        if (!Files.exists(objectPath)) {
            throw new IOException("Object not found: " + hash);
        }
        
        // 3. 读取压缩数据
        byte[] compressedData = Files.readAllBytes(objectPath);
        
        // 4. 解压数据
        try {
            return CompressionUtil.decompress(compressedData);
        } catch (IOException e) {
            throw new IOException("Failed to decompress object " + hash + 
                                  ": data may be corrupted", e);
        }
    }
    
    /**
     * 检查对象是否存在
     * 
     * @param hash BLAKE3哈希（64字符十六进制）
     * @return true如果对象存在
     * @throws IllegalArgumentException 如果哈希格式无效
     */
    public boolean exists(String hash) {
        validateHash(hash);
        return Files.exists(getObjectPath(hash));
    }
    
    /**
     * 删除对象（用于垃圾回收）
     * 
     * <p>注意：删除操作不会自动删除空目录。</p>
     * 
     * @param hash BLAKE3哈希（64字符十六进制）
     * @return true如果删除成功，false如果对象不存在
     * @throws IOException 如果删除失败
     * @throws IllegalArgumentException 如果哈希格式无效
     */
    public boolean delete(String hash) throws IOException {
        validateHash(hash);
        
        Path objectPath = getObjectPath(hash);
        
        if (!Files.exists(objectPath)) {
            return false;
        }
        
        Files.delete(objectPath);
        
        // 尝试删除空的父目录（可选优化）
        Path parentDir = objectPath.getParent();
        try {
            if (Files.isDirectory(parentDir) && isDirectoryEmpty(parentDir)) {
                Files.delete(parentDir);
            }
        } catch (IOException e) {
            // 忽略删除空目录的错误（可能有并发操作）
        }
        
        return true;
    }
    
    /**
     * 列出所有对象的哈希
     * 
     * <p>注意：此操作会遍历整个对象池，对于大型对象池可能较慢。</p>
     * 
     * @return 所有哈希的列表
     * @throws IOException 如果遍历目录失败
     */
    public List<String> listAllObjects() throws IOException {
        List<String> hashes = new ArrayList<>();
        
        // 如果对象池目录不存在，返回空列表
        if (!Files.exists(objectsDir)) {
            return hashes;
        }
        
        // 遍历所有2级目录
        try (Stream<Path> prefixDirs = Files.list(objectsDir)) {
            prefixDirs
                .filter(Files::isDirectory)
                .forEach(prefixDir -> {
                    String prefix = prefixDir.getFileName().toString();
                    
                    // 遍历该目录下的所有对象文件
                    try (Stream<Path> objects = Files.list(prefixDir)) {
                        objects
                            .filter(Files::isRegularFile)
                            .filter(path -> !path.getFileName().toString().endsWith(TEMP_SUFFIX))
                            .forEach(objectFile -> {
                                String suffix = objectFile.getFileName().toString();
                                String hash = prefix + suffix;
                                
                                // 验证哈希格式（防止意外文件）
                                if (isValidHashFormat(hash)) {
                                    hashes.add(hash);
                                }
                            });
                    } catch (IOException e) {
                        // 包装为运行时异常（因为在Stream中）
                        throw new RuntimeException("Failed to list objects in " + prefixDir, e);
                    }
                });
        }
        
        return hashes;
    }
    
    /**
     * 获取对象池统计信息
     * 
     * <p>注意：此操作会遍历整个对象池，对于大型对象池可能较慢。</p>
     * 
     * @return ObjectPoolStats统计信息
     * @throws IOException 如果遍历目录失败
     */
    public ObjectPoolStats getStats() throws IOException {
        long objectCount = 0;
        long totalSizeBytes = 0;
        long totalSizeOriginal = 0;
        
        // 如果对象池目录不存在，返回空统计
        if (!Files.exists(objectsDir)) {
            return new ObjectPoolStats(0, 0, 0);
        }
        
        // 遍历所有对象文件
        try (Stream<Path> prefixDirs = Files.list(objectsDir)) {
            for (Path prefixDir : (Iterable<Path>) prefixDirs.filter(Files::isDirectory)::iterator) {
                try (Stream<Path> objects = Files.list(prefixDir)) {
                    for (Path objectFile : (Iterable<Path>) objects
                            .filter(Files::isRegularFile)
                            .filter(path -> !path.getFileName().toString().endsWith(TEMP_SUFFIX))::iterator) {
                        
                        objectCount++;
                        long compressedSize = Files.size(objectFile);
                        totalSizeBytes += compressedSize;
                        
                        // 尝试获取原始大小（通过解压，可能较慢）
                        // 对于性能考虑，可以只统计压缩大小
                        try {
                            byte[] compressedData = Files.readAllBytes(objectFile);
                            byte[] originalData = CompressionUtil.decompress(compressedData);
                            totalSizeOriginal += originalData.length;
                        } catch (IOException e) {
                            // 如果解压失败，估算原始大小（假设压缩率50%）
                            totalSizeOriginal += compressedSize * 2;
                        }
                    }
                }
            }
        }
        
        return new ObjectPoolStats(objectCount, totalSizeBytes, totalSizeOriginal);
    }
    
    /**
     * 获取对象的文件路径（用于调试）
     * 
     * <p>路径格式：objects/[前2个字符]/[剩余62个字符]</p>
     * <p>示例：a1b2c3d4... → objects/a1/b2c3d4...</p>
     * 
     * @param hash BLAKE3哈希（64字符十六进制）
     * @return 对象的完整路径
     * @throws IllegalArgumentException 如果哈希格式无效
     */
    public Path getObjectPath(String hash) {
        validateHash(hash);
        
        // 提取前2个字符作为目录名
        String prefix = hash.substring(0, PREFIX_LENGTH);
        // 剩余62个字符作为文件名
        String suffix = hash.substring(PREFIX_LENGTH);
        
        return objectsDir.resolve(prefix).resolve(suffix);
    }
    
    /**
     * 获取对象池根目录
     * 
     * @return 对象池根目录路径
     */
    public Path getObjectsDir() {
        return objectsDir;
    }
    
    /**
     * 获取压缩级别
     * 
     * @return Zstd压缩级别
     */
    public int getCompressionLevel() {
        return compressionLevel;
    }
    
    // ==================== 私有辅助方法 ====================
    
    /**
     * 验证哈希格式
     * 
     * @param hash 哈希字符串
     * @throws IllegalArgumentException 如果哈希格式无效
     */
    private void validateHash(String hash) {
        if (hash == null) {
            throw new IllegalArgumentException("Hash cannot be null");
        }
        if (!isValidHashFormat(hash)) {
            throw new IllegalArgumentException(
                "Invalid hash format: must be " + HashUtil.HEX_LENGTH + 
                " lowercase hexadecimal characters, got: " + hash
            );
        }
    }
    
    /**
     * 检查哈希格式是否有效
     * 
     * @param hash 哈希字符串
     * @return true如果格式有效
     */
    private boolean isValidHashFormat(String hash) {
        if (hash == null || hash.length() != HashUtil.HEX_LENGTH) {
            return false;
        }
        
        // 检查是否全为小写十六进制字符
        for (int i = 0; i < hash.length(); i++) {
            char c = hash.charAt(i);
            if (!((c >= '0' && c <= '9') || (c >= 'a' && c <= 'f'))) {
                return false;
            }
        }
        
        return true;
    }
    
    /**
     * 检查目录是否为空
     * 
     * @param dir 目录路径
     * @return true如果目录为空
     * @throws IOException 如果检查失败
     */
    private boolean isDirectoryEmpty(Path dir) throws IOException {
        try (Stream<Path> entries = Files.list(dir)) {
            return !entries.findAny().isPresent();
        }
    }
}
