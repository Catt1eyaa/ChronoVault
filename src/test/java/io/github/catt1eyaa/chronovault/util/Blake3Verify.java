package io.github.catt1eyaa.chronovault.util;

/**
 * 简单的程序来验证BLAKE3哈希值
 */
public class Blake3Verify {
    public static void main(String[] args) {
        // 测试空数据
        byte[] empty = new byte[0];
        String emptyHash = HashUtil.hashToHex(empty);
        System.out.println("Empty data hash: " + emptyHash);
        
        // 测试 "Hello, World!"
        String test = "Hello, World!";
        String testHash = HashUtil.hashToHex(test.getBytes());
        System.out.println("\"Hello, World!\" hash: " + testHash);
    }
}
