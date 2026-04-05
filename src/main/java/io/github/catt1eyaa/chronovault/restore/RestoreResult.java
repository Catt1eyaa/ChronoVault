package io.github.catt1eyaa.chronovault.restore;

import java.nio.file.Path;

/**
 * 恢复操作结果。
 *
 * @param snapshotId 恢复的快照 ID
 * @param restoredFiles 恢复的普通文件数量
 * @param restoredRegions 恢复的 region 文件数量
 * @param restoredChunks 恢复的 chunk 数量
 * @param targetWorldName 新创建的世界文件夹名
 * @param targetWorldPath 新创建的世界完整路径
 */
public record RestoreResult(
    String snapshotId,
    int restoredFiles,
    int restoredRegions,
    int restoredChunks,
    String targetWorldName,
    Path targetWorldPath
) {
    /**
     * 创建恢复结果（兼容旧签名，内部使用）。
     */
    public static RestoreResult of(
        String snapshotId,
        int restoredFiles,
        int restoredRegions,
        int restoredChunks,
        String targetWorldName,
        Path targetWorldPath
    ) {
        return new RestoreResult(
            snapshotId,
            restoredFiles,
            restoredRegions,
            restoredChunks,
            targetWorldName,
            targetWorldPath
        );
    }
}
