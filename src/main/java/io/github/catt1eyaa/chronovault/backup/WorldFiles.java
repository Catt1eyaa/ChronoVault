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

import java.nio.file.Path;
import java.util.Map;
import java.util.Set;

/**
 * Represents the result of scanning a world: all files found in the world directory.
 * Files are categorized into regular files and region files.
 * 
 * @param regularFiles Regular file mapping: relative path -> absolute path
 * @param regionFiles Region file mapping: dimension name -> (region file name -> absolute path)
 */
public record WorldFiles(
    Map<String, Path> regularFiles,           // relative path -> absolute path
    Map<String, Map<String, Path>> regionFiles // dimension -> (file name -> absolute path)
) {
    /**
     * Compact constructor with null checks
     */
    public WorldFiles {
        if (regularFiles == null) {
            regularFiles = Map.of();
        }
        if (regionFiles == null) {
            regionFiles = Map.of();
        }
    }
    
    /**
     * Get the total number of all files (regular + region)
     * 
     * @return Total file count
     */
    public int getTotalFileCount() {
        return regularFiles.size() + getRegionFileCount();
    }
    
    /**
     * Get the total number of region files across all dimensions
     * 
     * @return Region file count
     */
    public int getRegionFileCount() {
        return regionFiles.values().stream()
            .mapToInt(Map::size)
            .sum();
    }
    
    /**
     * Get all dimension names that have region files
     * 
     * @return Set of dimension names
     */
    public Set<String> getDimensions() {
        return regionFiles.keySet();
    }
}
