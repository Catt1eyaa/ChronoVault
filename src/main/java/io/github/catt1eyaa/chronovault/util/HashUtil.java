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

import org.bouncycastle.crypto.digests.Blake3Digest;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 哈希工具类，提供基于BLAKE3的内容哈希功能。
 * BLAKE3是一种高速加密哈希函数，适用于内容寻址存储(CAS)系统。
 * 
 * <p>所有方法都是线程安全的，不使用共享可变状态。</p>
 */
public class HashUtil {
    
    /**
     * BLAKE3哈希输出长度（字节）
     */
    public static final int HASH_LENGTH = 32;
    
    /**
     * 十六进制哈希字符串长度
     */
    public static final int HEX_LENGTH = HASH_LENGTH * 2;
    
    /**
     * 流式读取的缓冲区大小（8KB）
     */
    private static final int BUFFER_SIZE = 8192;
    
    /**
     * 十六进制字符数组
     */
    private static final char[] HEX_ARRAY = "0123456789abcdef".toCharArray();
    
    /**
     * 计算数据的BLAKE3哈希（32字节）
     * 
     * @param data 输入数据
     * @return 32字节的BLAKE3哈希
     */
    public static byte[] hash(byte[] data) {
        if (data == null) {
            throw new IllegalArgumentException("Input data cannot be null");
        }
        
        Blake3Digest digest = new Blake3Digest();
        digest.update(data, 0, data.length);
        
        byte[] hash = new byte[HASH_LENGTH];
        digest.doFinal(hash, 0);
        
        return hash;
    }
    
    /**
     * 计算数据的BLAKE3哈希并返回十六进制字符串（64字符）
     * 
     * @param data 输入数据
     * @return 64字符的十六进制字符串（小写）
     */
    public static String hashToHex(byte[] data) {
        return bytesToHex(hash(data));
    }
    
    /**
     * 从输入流计算BLAKE3哈希（用于大文件）
     * 使用流式处理，避免一次性加载整个文件到内存
     * 
     * @param inputStream 输入流（调用者负责关闭）
     * @return 64字符的十六进制哈希字符串
     * @throws IOException 如果读取流时发生错误
     */
    public static String hashStream(InputStream inputStream) throws IOException {
        if (inputStream == null) {
            throw new IllegalArgumentException("InputStream cannot be null");
        }
        
        Blake3Digest digest = new Blake3Digest();
        byte[] buffer = new byte[BUFFER_SIZE];
        int bytesRead;
        
        while ((bytesRead = inputStream.read(buffer)) != -1) {
            digest.update(buffer, 0, bytesRead);
        }
        
        byte[] hash = new byte[HASH_LENGTH];
        digest.doFinal(hash, 0);
        
        return bytesToHex(hash);
    }
    
    /**
     * 计算文件的BLAKE3哈希
     * 
     * @param filePath 文件路径
     * @return 64字符的十六进制哈希字符串
     * @throws IOException 如果读取文件时发生错误
     */
    public static String hashFile(Path filePath) throws IOException {
        if (filePath == null) {
            throw new IllegalArgumentException("File path cannot be null");
        }
        if (!Files.exists(filePath)) {
            throw new IOException("File does not exist: " + filePath);
        }
        if (!Files.isRegularFile(filePath)) {
            throw new IOException("Path is not a regular file: " + filePath);
        }
        
        try (InputStream inputStream = Files.newInputStream(filePath)) {
            return hashStream(inputStream);
        }
    }
    
    /**
     * 将字节数组转换为十六进制字符串
     * 使用查找表优化性能
     * 
     * @param bytes 字节数组
     * @return 十六进制字符串（小写，无分隔符）
     */
    public static String bytesToHex(byte[] bytes) {
        if (bytes == null) {
            throw new IllegalArgumentException("Byte array cannot be null");
        }
        
        char[] hexChars = new char[bytes.length * 2];
        for (int i = 0; i < bytes.length; i++) {
            int v = bytes[i] & 0xFF;
            hexChars[i * 2] = HEX_ARRAY[v >>> 4];
            hexChars[i * 2 + 1] = HEX_ARRAY[v & 0x0F];
        }
        return new String(hexChars);
    }
    
    /**
     * 将十六进制字符串转换为字节数组
     * 
     * @param hex 十六进制字符串（大小写不敏感）
     * @return 字节数组
     * @throws IllegalArgumentException 如果输入不是有效的十六进制字符串
     */
    public static byte[] hexToBytes(String hex) {
        if (hex == null) {
            throw new IllegalArgumentException("Hex string cannot be null");
        }
        if (hex.length() % 2 != 0) {
            throw new IllegalArgumentException("Hex string must have even length");
        }
        
        int len = hex.length();
        byte[] data = new byte[len / 2];
        
        for (int i = 0; i < len; i += 2) {
            int high = Character.digit(hex.charAt(i), 16);
            int low = Character.digit(hex.charAt(i + 1), 16);
            
            if (high == -1 || low == -1) {
                throw new IllegalArgumentException("Invalid hex character at position " + i);
            }
            
            data[i / 2] = (byte) ((high << 4) + low);
        }
        
        return data;
    }
}
