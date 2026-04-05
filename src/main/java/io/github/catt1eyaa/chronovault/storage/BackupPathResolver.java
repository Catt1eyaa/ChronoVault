package io.github.catt1eyaa.chronovault.storage;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 统一计算世界专属备份目录路径。
 *
 * <p>处理世界名规范化（非法字符、Windows 保留名、超长名）。</p>
 */
public class BackupPathResolver {

    private static final int MAX_NAME_LENGTH = 200;
    private static final int TRUNCATE_LENGTH = 180;

    private static final Set<String> WINDOWS_RESERVED_NAMES = Set.of(
        "CON", "PRN", "AUX", "NUL",
        "COM1", "COM2", "COM3", "COM4", "COM5", "COM6", "COM7", "COM8", "COM9",
        "LPT1", "LPT2", "LPT3", "LPT4", "LPT5", "LPT6", "LPT7", "LPT8", "LPT9"
    );

    private static final Pattern ILLEGAL_CHARS = Pattern.compile("[<>:\"/\\\\|?*]");

    private BackupPathResolver() {
    }

    /**
     * 计算世界专属备份目录。
     *
     * @param backupRoot 备份根目录（如 "backups"）
     * @param worldName 世界文件夹名
     * @return backupRoot/sanitizedWorldName
     * @throws NullPointerException 如果 backupRoot 为 null
     * @throws IllegalArgumentException 如果 worldName 为 null 或空白
     */
    public static Path resolve(Path backupRoot, String worldName) {
        Objects.requireNonNull(backupRoot, "backupRoot cannot be null");
        if (worldName == null || worldName.isBlank()) {
            throw new IllegalArgumentException("worldName cannot be null or blank");
        }
        String sanitized = sanitizeWorldName(worldName);
        return backupRoot.resolve(sanitized);
    }

    /**
     * 规范化世界名。
     *
     * <p>处理规则：</p>
     * <ul>
     *   <li>null/空白 → "_unnamed"</li>
     *   <li>非法字符替换为 "_"</li>
     *   <li>Windows 保留名追加 "_safe"</li>
     *   <li>超长名截断并添加 hash 后缀</li>
     * </ul>
     *
     * @param worldName 原始世界名
     * @return 规范化后的世界名
     */
    public static String sanitizeWorldName(String worldName) {
        if (worldName == null || worldName.isBlank()) {
            return "_unnamed";
        }

        String sanitized = worldName.trim();
        sanitized = ILLEGAL_CHARS.matcher(sanitized).replaceAll("_");

        if (WINDOWS_RESERVED_NAMES.contains(sanitized.toUpperCase())) {
            sanitized = sanitized + "_safe";
        }

        if (sanitized.length() > MAX_NAME_LENGTH) {
            String hash = Integer.toHexString(worldName.hashCode());
            sanitized = sanitized.substring(0, TRUNCATE_LENGTH) + "_" + hash;
        }

        return sanitized;
    }
}
