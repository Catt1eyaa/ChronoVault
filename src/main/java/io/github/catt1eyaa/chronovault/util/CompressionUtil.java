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

package io.github.catt1eyaa.chronovault.util;

import com.github.luben.zstd.Zstd;
import com.github.luben.zstd.ZstdInputStream;
import com.github.luben.zstd.ZstdOutputStream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * 压缩工具类，提供基于Zstd的数据压缩和解压缩功能。
 * Zstd（Zstandard）是一种高速压缩算法，适合用于对象池的数据存储。
 * 
 * <p>所有方法都是线程安全的，不使用共享可变状态。</p>
 * 
 * <p>性能特征（典型值，取决于硬件和数据类型）：
 * <ul>
 *   <li>压缩速度（级别3）：>100 MB/s</li>
 *   <li>解压速度：>500 MB/s</li>
 *   <li>Minecraft NBT数据压缩率：约50-70%</li>
 * </ul>
 * </p>
 */
public class CompressionUtil {
    
    /**
     * 默认压缩级别（平衡速度和压缩率）
     * 级别3在spec.md中建议，适合Minecraft chunk数据
     */
    public static final int DEFAULT_LEVEL = 3;
    
    /**
     * 最小有效压缩级别
     */
    public static final int MIN_LEVEL = 1;
    
    /**
     * 最大有效压缩级别
     */
    public static final int MAX_LEVEL = 22;
    
    /**
     * 流式压缩的缓冲区大小（64KB）
     * 用于流式API以平衡内存使用和性能
     */
    private static final int BUFFER_SIZE = 64 * 1024;
    
    /**
     * 使用默认压缩级别压缩数据
     * 
     * @param data 原始数据
     * @return 压缩后的数据
     * @throws IOException 如果压缩失败
     * @throws IllegalArgumentException 如果输入为null
     */
    public static byte[] compress(byte[] data) throws IOException {
        return compress(data, DEFAULT_LEVEL);
    }
    
    /**
     * 使用指定压缩级别压缩数据
     * 
     * <p>压缩级别说明：
     * <ul>
     *   <li>1-3：快速压缩，较低压缩率</li>
     *   <li>3-6：平衡（推荐用于Minecraft chunk）</li>
     *   <li>7-22：更高压缩率，较慢</li>
     * </ul>
     * </p>
     * 
     * @param data 原始数据
     * @param level 压缩级别 (1-22，推荐3-6)
     * @return 压缩后的数据
     * @throws IOException 如果压缩失败
     * @throws IllegalArgumentException 如果输入为null或压缩级别无效
     */
    public static byte[] compress(byte[] data, int level) throws IOException {
        if (data == null) {
            throw new IllegalArgumentException("Input data cannot be null");
        }
        if (!isValidLevel(level)) {
            throw new IllegalArgumentException(
                "Invalid compression level: " + level + 
                " (must be between " + MIN_LEVEL + " and " + MAX_LEVEL + ")"
            );
        }
        
        // 对于空数据，直接返回空数组
        if (data.length == 0) {
            return new byte[0];
        }
        
        // 使用Zstd直接压缩
        return Zstd.compress(data, level);
    }
    
    /**
     * 解压Zstd压缩的数据
     * 
     * @param compressedData 压缩数据
     * @return 原始数据
     * @throws IOException 如果解压失败（例如数据损坏）
     * @throws IllegalArgumentException 如果输入为null
     */
    public static byte[] decompress(byte[] compressedData) throws IOException {
        if (compressedData == null) {
            throw new IllegalArgumentException("Input compressed data cannot be null");
        }
        
        // 对于空数据，直接返回空数组
        if (compressedData.length == 0) {
            return new byte[0];
        }
        
        try {
            // 获取解压后的大小（Zstd在压缩数据头部存储此信息）
            long decompressedSize = Zstd.decompressedSize(compressedData);
            
            if (decompressedSize == 0) {
                // 如果无法获取大小信息，使用流式API
                return decompressWithStream(compressedData);
            }
            
            // 如果大小已知，直接分配缓冲区并解压
            byte[] decompressed = new byte[(int) decompressedSize];
            long actualSize = Zstd.decompress(decompressed, compressedData);
            
            if (actualSize != decompressedSize) {
                throw new IOException(
                    "Decompression size mismatch: expected " + decompressedSize + 
                    ", got " + actualSize
                );
            }
            
            return decompressed;
        } catch (Exception e) {
            throw new IOException("Failed to decompress data: " + e.getMessage(), e);
        }
    }
    
    /**
     * 使用流式API解压数据（用于未知大小的数据）
     * 
     * @param compressedData 压缩数据
     * @return 解压后的数据
     * @throws IOException 如果解压失败
     */
    private static byte[] decompressWithStream(byte[] compressedData) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ByteArrayInputStream input = new ByteArrayInputStream(compressedData)) {
            decompressStream(input, output);
        }
        return output.toByteArray();
    }
    
    /**
     * 压缩输入流到输出流（用于大文件）
     * 适用于文件大小 >1MB 的情况
     * 
     * @param input 输入流（原始数据，调用者负责关闭）
     * @param output 输出流（压缩数据，调用者负责关闭）
     * @param level 压缩级别 (1-22)
     * @throws IOException 如果压缩失败
     * @throws IllegalArgumentException 如果输入为null或压缩级别无效
     */
    public static void compressStream(InputStream input, OutputStream output, int level) 
            throws IOException {
        if (input == null) {
            throw new IllegalArgumentException("Input stream cannot be null");
        }
        if (output == null) {
            throw new IllegalArgumentException("Output stream cannot be null");
        }
        if (!isValidLevel(level)) {
            throw new IllegalArgumentException(
                "Invalid compression level: " + level + 
                " (must be between " + MIN_LEVEL + " and " + MAX_LEVEL + ")"
            );
        }
        
        try (ZstdOutputStream zstdOutput = new ZstdOutputStream(output, level)) {
            byte[] buffer = new byte[BUFFER_SIZE];
            int bytesRead;
            
            while ((bytesRead = input.read(buffer)) != -1) {
                zstdOutput.write(buffer, 0, bytesRead);
            }
            
            // 确保所有数据都被写入
            zstdOutput.flush();
        } catch (Exception e) {
            throw new IOException("Failed to compress stream: " + e.getMessage(), e);
        }
    }
    
    /**
     * 解压输入流到输出流（用于大文件）
     * 适用于文件大小 >1MB 的情况
     * 
     * @param input 输入流（压缩数据，调用者负责关闭）
     * @param output 输出流（原始数据，调用者负责关闭）
     * @throws IOException 如果解压失败
     * @throws IllegalArgumentException 如果输入为null
     */
    public static void decompressStream(InputStream input, OutputStream output) 
            throws IOException {
        if (input == null) {
            throw new IllegalArgumentException("Input stream cannot be null");
        }
        if (output == null) {
            throw new IllegalArgumentException("Output stream cannot be null");
        }
        
        try (ZstdInputStream zstdInput = new ZstdInputStream(input)) {
            byte[] buffer = new byte[BUFFER_SIZE];
            int bytesRead;
            
            while ((bytesRead = zstdInput.read(buffer)) != -1) {
                output.write(buffer, 0, bytesRead);
            }
            
            // 确保所有数据都被写入
            output.flush();
        } catch (Exception e) {
            throw new IOException("Failed to decompress stream: " + e.getMessage(), e);
        }
    }
    
    /**
     * 获取默认压缩级别
     * 
     * @return 默认压缩级别（3）
     */
    public static int getDefaultLevel() {
        return DEFAULT_LEVEL;
    }
    
    /**
     * 验证压缩级别是否有效
     * 
     * @param level 压缩级别
     * @return true如果级别在有效范围内（1-22）
     */
    public static boolean isValidLevel(int level) {
        return level >= MIN_LEVEL && level <= MAX_LEVEL;
    }
}
