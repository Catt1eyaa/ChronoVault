package io.github.catt1eyaa.chronovault.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 测试HashUtil工具类的所有功能
 */
class HashUtilTest {
    
    /**
     * 测试空数据的哈希
     */
    @Test
    void testHashEmptyData() {
        byte[] empty = new byte[0];
        byte[] hash = HashUtil.hash(empty);
        
        // 验证哈希长度为32字节
        assertEquals(HashUtil.HASH_LENGTH, hash.length, "Hash should be 32 bytes");
        
        // BLAKE3空数据的已知哈希值
        String expectedHex = "af1349b9f5f9a1a6a0404dea36dcc9499bcb25c9adc112b7cc9a93cae41f3262";
        String actualHex = HashUtil.bytesToHex(hash);
        
        assertEquals(expectedHex, actualHex, "Empty data hash mismatch");
    }
    
    /**
     * 测试已知数据的哈希
     */
    @Test
    void testHashKnownData() {
        String testData = "Hello, World!";
        byte[] data = testData.getBytes(StandardCharsets.UTF_8);
        
        String hex = HashUtil.hashToHex(data);
        
        // 验证十六进制长度为64字符
        assertEquals(HashUtil.HEX_LENGTH, hex.length(), "Hex hash should be 64 characters");
        
        // 验证只包含有效的十六进制字符（小写）
        assertTrue(hex.matches("^[0-9a-f]{64}$"), "Hash should be lowercase hex");
        
        // BLAKE3 "Hello, World!" 的哈希值（使用Bouncy Castle实现）
        String expectedHex = "288a86a79f20a3d6dccdca7713beaed178798296bdfa7913fa2a62d9727bf8f8";
        assertEquals(expectedHex, hex, "Known data hash mismatch");
        
        // 测试一致性 - 相同输入应产生相同输出
        String hex2 = HashUtil.hashToHex(data);
        assertEquals(hex, hex2, "Hash should be consistent");
    }
    
    /**
     * 测试null输入的处理
     */
    @Test
    void testHashNullInput() {
        assertThrows(IllegalArgumentException.class, () -> {
            HashUtil.hash(null);
        }, "Should throw exception for null input");
        
        assertThrows(IllegalArgumentException.class, () -> {
            HashUtil.hashToHex(null);
        }, "Should throw exception for null input");
    }
    
    /**
     * 测试十六进制转换的往返
     */
    @Test
    void testHexConversionRoundTrip() {
        byte[] original = new byte[]{
            (byte) 0x00, (byte) 0x11, (byte) 0x22, (byte) 0x33,
            (byte) 0x44, (byte) 0x55, (byte) 0x66, (byte) 0x77,
            (byte) 0x88, (byte) 0x99, (byte) 0xAA, (byte) 0xBB,
            (byte) 0xCC, (byte) 0xDD, (byte) 0xEE, (byte) 0xFF
        };
        
        String hex = HashUtil.bytesToHex(original);
        assertEquals("00112233445566778899aabbccddeeff", hex, "Hex conversion failed");
        
        byte[] roundTrip = HashUtil.hexToBytes(hex);
        assertArrayEquals(original, roundTrip, "Round trip conversion failed");
    }
    
    /**
     * 测试十六进制字符串格式（小写，无分隔符）
     */
    @Test
    void testHexFormat() {
        byte[] data = "test".getBytes(StandardCharsets.UTF_8);
        String hex = HashUtil.hashToHex(data);
        
        // 验证小写
        assertEquals(hex, hex.toLowerCase(), "Hex should be lowercase");
        
        // 验证无分隔符
        assertFalse(hex.contains(":"), "Hex should not contain colons");
        assertFalse(hex.contains("-"), "Hex should not contain dashes");
        assertFalse(hex.contains(" "), "Hex should not contain spaces");
    }
    
    /**
     * 测试十六进制转换的大小写不敏感
     */
    @Test
    void testHexToBytesCase() {
        String lowercase = "abcdef123456";
        String uppercase = "ABCDEF123456";
        String mixed = "AbCdEf123456";
        
        byte[] fromLower = HashUtil.hexToBytes(lowercase);
        byte[] fromUpper = HashUtil.hexToBytes(uppercase);
        byte[] fromMixed = HashUtil.hexToBytes(mixed);
        
        assertArrayEquals(fromLower, fromUpper, "Case should not matter");
        assertArrayEquals(fromLower, fromMixed, "Case should not matter");
    }
    
    /**
     * 测试无效的十六进制字符串
     */
    @Test
    void testInvalidHexStrings() {
        // 奇数长度
        assertThrows(IllegalArgumentException.class, () -> {
            HashUtil.hexToBytes("abc");
        }, "Should reject odd length hex string");
        
        // 无效字符
        assertThrows(IllegalArgumentException.class, () -> {
            HashUtil.hexToBytes("gg");
        }, "Should reject invalid hex characters");
        
        // null
        assertThrows(IllegalArgumentException.class, () -> {
            HashUtil.hexToBytes(null);
        }, "Should reject null input");
    }
    
    /**
     * 测试流式哈希与直接哈希的一致性
     */
    @Test
    void testStreamHashConsistency() throws IOException {
        String testData = "This is a test string for stream hashing";
        byte[] data = testData.getBytes(StandardCharsets.UTF_8);
        
        // 直接哈希
        String directHash = HashUtil.hashToHex(data);
        
        // 流式哈希
        ByteArrayInputStream stream = new ByteArrayInputStream(data);
        String streamHash = HashUtil.hashStream(stream);
        stream.close();
        
        assertEquals(directHash, streamHash, "Stream hash should match direct hash");
    }
    
    /**
     * 测试大数据的流式哈希
     */
    @Test
    void testStreamHashLargeData() throws IOException {
        // 创建1MB的测试数据
        byte[] largeData = new byte[1024 * 1024];
        for (int i = 0; i < largeData.length; i++) {
            largeData[i] = (byte) (i % 256);
        }
        
        String directHash = HashUtil.hashToHex(largeData);
        
        ByteArrayInputStream stream = new ByteArrayInputStream(largeData);
        String streamHash = HashUtil.hashStream(stream);
        stream.close();
        
        assertEquals(directHash, streamHash, "Large data stream hash should match");
    }
    
    /**
     * 测试文件哈希功能
     */
    @Test
    void testHashFile(@TempDir Path tempDir) throws IOException {
        // 创建临时测试文件
        Path testFile = tempDir.resolve("test.txt");
        String content = "File content for hashing test";
        Files.writeString(testFile, content);
        
        // 计算文件哈希
        String fileHash = HashUtil.hashFile(testFile);
        
        // 验证与直接哈希一致
        String directHash = HashUtil.hashToHex(content.getBytes(StandardCharsets.UTF_8));
        assertEquals(directHash, fileHash, "File hash should match direct hash");
    }
    
    /**
     * 测试文件哈希的多次计算一致性
     */
    @Test
    void testHashFileConsistency(@TempDir Path tempDir) throws IOException {
        Path testFile = tempDir.resolve("consistent.dat");
        byte[] data = "Consistency test data".getBytes(StandardCharsets.UTF_8);
        Files.write(testFile, data);
        
        String hash1 = HashUtil.hashFile(testFile);
        String hash2 = HashUtil.hashFile(testFile);
        String hash3 = HashUtil.hashFile(testFile);
        
        assertEquals(hash1, hash2, "Multiple hashes should be consistent");
        assertEquals(hash2, hash3, "Multiple hashes should be consistent");
    }
    
    /**
     * 测试不存在的文件
     */
    @Test
    void testHashNonExistentFile(@TempDir Path tempDir) {
        Path nonExistent = tempDir.resolve("does-not-exist.txt");
        
        assertThrows(IOException.class, () -> {
            HashUtil.hashFile(nonExistent);
        }, "Should throw IOException for non-existent file");
    }
    
    /**
     * 测试目录路径（非文件）
     */
    @Test
    void testHashDirectory(@TempDir Path tempDir) {
        assertThrows(IOException.class, () -> {
            HashUtil.hashFile(tempDir);
        }, "Should throw IOException for directory");
    }
    
    /**
     * 测试null输入流
     */
    @Test
    void testHashNullStream() {
        assertThrows(IllegalArgumentException.class, () -> {
            HashUtil.hashStream(null);
        }, "Should throw exception for null stream");
    }
    
    /**
     * 测试null文件路径
     */
    @Test
    void testHashNullPath() {
        assertThrows(IllegalArgumentException.class, () -> {
            HashUtil.hashFile(null);
        }, "Should throw exception for null path");
    }
    
    /**
     * 测试真实.mca文件的哈希（如果存在）
     * 这个测试会尝试读取实际的Minecraft区域文件
     */
    @Test
    void testHashRealMcaFile() {
        Path mcaFile = Path.of("run/saves/New World/region/r.0.0.mca");
        
        if (Files.exists(mcaFile)) {
            try {
                String hash1 = HashUtil.hashFile(mcaFile);
                String hash2 = HashUtil.hashFile(mcaFile);
                
                assertEquals(hash1, hash2, "MCA file hash should be consistent");
                assertEquals(HashUtil.HEX_LENGTH, hash1.length(), "Hash should be 64 characters");
                assertTrue(hash1.matches("^[0-9a-f]{64}$"), "Hash should be lowercase hex");
                
                System.out.println("MCA file hash: " + hash1);
            } catch (IOException e) {
                fail("Failed to hash MCA file: " + e.getMessage());
            }
        } else {
            System.out.println("Skipping real MCA file test - file not found");
        }
    }
    
    /**
     * 测试不同数据产生不同哈希
     */
    @Test
    void testDifferentDataDifferentHash() {
        String data1 = "First string";
        String data2 = "Second string";
        
        String hash1 = HashUtil.hashToHex(data1.getBytes(StandardCharsets.UTF_8));
        String hash2 = HashUtil.hashToHex(data2.getBytes(StandardCharsets.UTF_8));
        
        assertNotEquals(hash1, hash2, "Different data should produce different hashes");
    }
    
    /**
     * 测试即使微小差异也会产生完全不同的哈希（雪崩效应）
     */
    @Test
    void testAvalancheEffect() {
        String data1 = "test";
        String data2 = "Test"; // 仅大小写不同
        
        String hash1 = HashUtil.hashToHex(data1.getBytes(StandardCharsets.UTF_8));
        String hash2 = HashUtil.hashToHex(data2.getBytes(StandardCharsets.UTF_8));
        
        assertNotEquals(hash1, hash2, "Small change should produce different hash");
        
        // 计算不同位的数量（应该约为50%）
        int differentBits = 0;
        byte[] bytes1 = HashUtil.hexToBytes(hash1);
        byte[] bytes2 = HashUtil.hexToBytes(hash2);
        
        for (int i = 0; i < bytes1.length; i++) {
            int xor = bytes1[i] ^ bytes2[i];
            differentBits += Integer.bitCount(xor & 0xFF);
        }
        
        // 雪崩效应：至少应该有25%的位不同（理想情况约50%）
        int totalBits = HashUtil.HASH_LENGTH * 8;
        assertTrue(differentBits > totalBits / 4, 
            "Avalanche effect: expected >25% bits different, got " + 
            (100.0 * differentBits / totalBits) + "%");
    }
}
