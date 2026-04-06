package io.github.catt1eyaa.chronovault.region;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AnvilReaderTest {

    @Test
    void testReadHeaderFromZeroByteRegionReturnsEmptyLocations(@TempDir Path tempDir) throws IOException {
        Path regionFile = tempDir.resolve("r.0.-1.mca");
        Files.write(regionFile, new byte[0]);

        List<ChunkLocation> locations = AnvilReader.readHeader(regionFile);
        assertEquals(1024, locations.size());
        assertEquals(1024, locations.stream().filter(ChunkLocation::isEmpty).count());
    }
}
