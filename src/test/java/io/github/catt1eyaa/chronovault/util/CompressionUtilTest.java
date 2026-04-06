package io.github.catt1eyaa.chronovault.util;

import io.github.catt1eyaa.chronovault.region.AnvilReader;
import io.github.catt1eyaa.chronovault.region.ChunkData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 测试CompressionUtil的压缩和解压缩功能
 */
class CompressionUtilTest {
    
    @Test
    @DisplayName("基本压缩测试 - Hello World")
    void testBasicCompression() throws IOException {
        String testString = "Hello, World!";
        byte[] original = testString.getBytes(StandardCharsets.UTF_8);
        
        // 压缩
        byte[] compressed = CompressionUtil.compress(original);
        assertNotNull(compressed);
        
        // 解压
        byte[] decompressed = CompressionUtil.decompress(compressed);
        assertNotNull(decompressed);
        
        // 验证解压后的数据与原始数据一致
        assertArrayEquals(original, decompressed);
        assertEquals(testString, new String(decompressed, StandardCharsets.UTF_8));
        
        System.out.println("基本压缩测试:");
        System.out.println("  原始大小: " + original.length + " 字节");
        System.out.println("  压缩大小: " + compressed.length + " 字节");
        System.out.println("  压缩率: " + String.format("%.2f%%", 
            (1.0 - (double)compressed.length / original.length) * 100));
    }
    
    @Test
    @DisplayName("往返测试 - 100字节随机数据")
    void testRoundTrip100Bytes() throws IOException {
        testRoundTrip(100);
    }
    
    @Test
    @DisplayName("往返测试 - 1KB随机数据")
    void testRoundTrip1KB() throws IOException {
        testRoundTrip(1024);
    }
    
    @Test
    @DisplayName("往返测试 - 10KB随机数据")
    void testRoundTrip10KB() throws IOException {
        testRoundTrip(10 * 1024);
    }
    
    @Test
    @DisplayName("往返测试 - 100KB随机数据")
    void testRoundTrip100KB() throws IOException {
        testRoundTrip(100 * 1024);
    }
    
    /**
     * 测试压缩-解压往返一致性
     */
    private void testRoundTrip(int size) throws IOException {
        Random random = new Random(42); // 使用固定种子以确保可重复性
        byte[] original = new byte[size];
        random.nextBytes(original);
        
        // 压缩
        byte[] compressed = CompressionUtil.compress(original);
        
        // 解压
        byte[] decompressed = CompressionUtil.decompress(compressed);
        
        // 验证
        assertArrayEquals(original, decompressed, 
            "Round-trip failed for " + size + " bytes");
        
        double ratio = (1.0 - (double)compressed.length / original.length) * 100;
        System.out.println("往返测试 (" + formatSize(size) + "):");
        System.out.println("  压缩率: " + String.format("%.2f%%", ratio));
    }
    
    @Test
    @DisplayName("压缩级别测试 - 不同级别的压缩率")
    void testCompressionLevels() throws IOException {
        // 创建一个包含重复数据的测试数据（这种数据压缩效果好）
        String repeated = "The quick brown fox jumps over the lazy dog. ".repeat(100);
        byte[] original = repeated.getBytes(StandardCharsets.UTF_8);
        
        System.out.println("压缩级别测试 (原始大小: " + formatSize(original.length) + "):");
        
        int[] levels = {1, 3, 6, 10, 22};
        
        for (int level : levels) {
            long startTime = System.nanoTime();
            byte[] compressed = CompressionUtil.compress(original, level);
            long compressTime = System.nanoTime() - startTime;
            
            startTime = System.nanoTime();
            byte[] decompressed = CompressionUtil.decompress(compressed);
            long decompressTime = System.nanoTime() - startTime;
            
            // 验证解压后数据一致
            assertArrayEquals(original, decompressed);
            
            double ratio = (1.0 - (double)compressed.length / original.length) * 100;
            double compressSpeed = (original.length / 1024.0 / 1024.0) / (compressTime / 1e9);
            double decompressSpeed = (original.length / 1024.0 / 1024.0) / (decompressTime / 1e9);
            
            System.out.println(String.format(
                "  级别 %2d: 压缩后 %s (%.2f%%) | 压缩速度: %.1f MB/s | 解压速度: %.1f MB/s",
                level, formatSize(compressed.length), ratio, compressSpeed, decompressSpeed
            ));
        }
    }
    
    @Test
    @DisplayName("真实数据测试 - Minecraft chunk")
    void testRealChunkData() throws IOException {
        Path regionFile = Paths.get("run/saves/New World/region/r.0.0.mca");
        
        if (!Files.exists(regionFile)) {
            System.out.println("跳过真实chunk测试: 找不到 " + regionFile);
            return;
        }
        
        // 读取所有chunk并获取第一个
        var chunks = AnvilReader.readAllChunks(regionFile);
        
        if (chunks.isEmpty()) {
            System.out.println("跳过真实chunk测试: 区域文件中没有chunk");
            return;
        }
        
        ChunkData chunk = chunks.get(0);
        assertNotNull(chunk, "未找到任何chunk");
        
        byte[] original = chunk.rawData();
        assertNotNull(original);
        assertTrue(original.length > 0, "Chunk数据为空");
        
        System.out.println("真实Minecraft Chunk测试:");
        System.out.println("  区块位置: (" + chunk.x() + ", " + chunk.z() + ")");
        System.out.println("  压缩类型: " + chunk.getCompressionName());
        System.out.println("  原始大小: " + formatSize(original.length));
        
        // 测试不同压缩级别
        int[] levels = {1, 3, 5, 6};
        
        for (int level : levels) {
            long startTime = System.nanoTime();
            byte[] compressed = CompressionUtil.compress(original, level);
            long compressTime = System.nanoTime() - startTime;
            
            startTime = System.nanoTime();
            byte[] decompressed = CompressionUtil.decompress(compressed);
            long decompressTime = System.nanoTime() - startTime;
            
            // 验证数据一致性
            assertArrayEquals(original, decompressed, 
                "Chunk round-trip failed at level " + level);
            
            double ratio = (1.0 - (double)compressed.length / original.length) * 100;
            double compressSpeed = (original.length / 1024.0 / 1024.0) / (compressTime / 1e9);
            double decompressSpeed = (original.length / 1024.0 / 1024.0) / (decompressTime / 1e9);
            
            System.out.println(String.format(
                "  级别 %d: %s (%.2f%%) | 压缩: %.1f MB/s | 解压: %.1f MB/s",
                level, formatSize(compressed.length), ratio, compressSpeed, decompressSpeed
            ));
        }
    }
    
    @Test
    @DisplayName("流式压缩测试 - 往返一致性")
    void testStreamCompression() throws IOException {
        // 创建测试数据
        String repeated = "Lorem ipsum dolor sit amet. ".repeat(1000);
        byte[] original = repeated.getBytes(StandardCharsets.UTF_8);
        
        // 使用stream方法压缩
        ByteArrayOutputStream streamOutput = new ByteArrayOutputStream();
        try (ByteArrayInputStream input = new ByteArrayInputStream(original)) {
            CompressionUtil.compressStream(input, streamOutput, 3);
        }
        byte[] compressedStream = streamOutput.toByteArray();
        
        // 测试stream解压
        ByteArrayOutputStream decompressOutput = new ByteArrayOutputStream();
        try (ByteArrayInputStream input = new ByteArrayInputStream(compressedStream)) {
            CompressionUtil.decompressStream(input, decompressOutput);
        }
        byte[] decompressed = decompressOutput.toByteArray();
        
        // 验证解压后数据一致（这是最重要的）
        assertArrayEquals(original, decompressed,
            "Stream round-trip failed");
        
        // 同时验证byte[]方法也能正确往返
        byte[] compressedBytes = CompressionUtil.compress(original, 3);
        byte[] decompressedBytes = CompressionUtil.decompress(compressedBytes);
        assertArrayEquals(original, decompressedBytes,
            "Byte array round-trip failed");
        
        // 验证两种方法的压缩大小在合理范围内（允许有轻微差异）
        double sizeDiff = Math.abs(compressedStream.length - compressedBytes.length);
        double relDiff = sizeDiff / Math.min(compressedStream.length, compressedBytes.length);
        assertTrue(relDiff < 0.1, "Stream and byte[] compression sizes differ significantly");
        
        System.out.println("流式压缩测试:");
        System.out.println("  原始大小: " + formatSize(original.length));
        System.out.println("  Stream压缩: " + formatSize(compressedStream.length));
        System.out.println("  Byte[]压缩: " + formatSize(compressedBytes.length));
        System.out.println("  往返一致性: ✓");
    }
    
    @Test
    @DisplayName("边界情况 - 空数据")
    void testEmptyData() throws IOException {
        byte[] empty = new byte[0];
        
        byte[] compressed = CompressionUtil.compress(empty);
        assertNotNull(compressed);
        assertEquals(0, compressed.length);
        
        byte[] decompressed = CompressionUtil.decompress(compressed);
        assertNotNull(decompressed);
        assertEquals(0, decompressed.length);
        
        System.out.println("空数据测试: ✓");
    }
    
    @Test
    @DisplayName("边界情况 - 单字节数据")
    void testSingleByte() throws IOException {
        byte[] single = new byte[] { 42 };
        
        byte[] compressed = CompressionUtil.compress(single);
        assertNotNull(compressed);
        
        byte[] decompressed = CompressionUtil.decompress(compressed);
        assertNotNull(decompressed);
        assertArrayEquals(single, decompressed);
        
        System.out.println("单字节测试: ✓");
    }
    
    @Test
    @DisplayName("边界情况 - 无效压缩级别")
    void testInvalidCompressionLevel() {
        byte[] data = "test".getBytes(StandardCharsets.UTF_8);
        
        // 测试低于最小值
        assertThrows(IllegalArgumentException.class, () -> {
            CompressionUtil.compress(data, 0);
        });
        
        // 测试高于最大值
        assertThrows(IllegalArgumentException.class, () -> {
            CompressionUtil.compress(data, 23);
        });
        
        // 测试负数
        assertThrows(IllegalArgumentException.class, () -> {
            CompressionUtil.compress(data, -1);
        });
        
        System.out.println("无效压缩级别测试: ✓");
    }
    
    @Test
    @DisplayName("边界情况 - null输入")
    void testNullInput() {
        assertThrows(IllegalArgumentException.class, () -> {
            CompressionUtil.compress(null);
        });
        
        assertThrows(IllegalArgumentException.class, () -> {
            CompressionUtil.decompress(null);
        });
        
        System.out.println("null输入测试: ✓");
    }
    
    @Test
    @DisplayName("工具方法测试 - isValidLevel和getDefaultLevel")
    void testUtilityMethods() {
        // 测试getDefaultLevel
        assertEquals(3, CompressionUtil.getDefaultLevel());
        
        // 测试isValidLevel
        assertTrue(CompressionUtil.isValidLevel(1));
        assertTrue(CompressionUtil.isValidLevel(3));
        assertTrue(CompressionUtil.isValidLevel(22));
        
        assertFalse(CompressionUtil.isValidLevel(0));
        assertFalse(CompressionUtil.isValidLevel(23));
        assertFalse(CompressionUtil.isValidLevel(-1));
        assertFalse(CompressionUtil.isValidLevel(100));
        
        System.out.println("工具方法测试: ✓");
    }
    
    @Test
    @DisplayName("性能测试 - 大数据压缩")
    void testLargeDataPerformance() throws IOException {
        // 创建1MB的测试数据（模拟真实场景的混合数据）
        int size = 1024 * 1024;
        byte[] original = new byte[size];
        Random random = new Random(42);
        
        // 50%重复数据，50%随机数据（模拟Minecraft数据）
        for (int i = 0; i < size / 2; i++) {
            original[i] = (byte)(i % 256);
        }
        random.nextBytes(Arrays.copyOfRange(original, size / 2, size));
        System.arraycopy(original, size / 2, original, size / 2, size / 2);
        
        System.out.println("性能测试 (1MB混合数据):");
        
        int level = 3;
        long startTime = System.nanoTime();
        byte[] compressed = CompressionUtil.compress(original, level);
        long compressTime = System.nanoTime() - startTime;
        
        startTime = System.nanoTime();
        byte[] decompressed = CompressionUtil.decompress(compressed);
        long decompressTime = System.nanoTime() - startTime;
        
        assertArrayEquals(original, decompressed);
        
        double compressSpeed = (original.length / 1024.0 / 1024.0) / (compressTime / 1e9);
        double decompressSpeed = (original.length / 1024.0 / 1024.0) / (decompressTime / 1e9);
        double ratio = (1.0 - (double)compressed.length / original.length) * 100;
        
        System.out.println("  压缩速度: " + String.format("%.1f MB/s", compressSpeed));
        System.out.println("  解压速度: " + String.format("%.1f MB/s", decompressSpeed));
        System.out.println("  压缩率: " + String.format("%.2f%%", ratio));
        System.out.println("  压缩后大小: " + formatSize(compressed.length));
        
        // 验证性能目标（这些是保守估计，实际性能应该更好）
        assertTrue(compressSpeed > 50, "压缩速度应 >50 MB/s (实际: " + 
            String.format("%.1f", compressSpeed) + " MB/s)");
        assertTrue(decompressSpeed > 200, "解压速度应 >200 MB/s (实际: " + 
            String.format("%.1f", decompressSpeed) + " MB/s)");
    }
    
    /**
     * 格式化字节大小为可读格式
     */
    private String formatSize(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        } else if (bytes < 1024 * 1024) {
            return String.format("%.2f KB", bytes / 1024.0);
        } else {
            return String.format("%.2f MB", bytes / 1024.0 / 1024.0);
        }
    }
}
