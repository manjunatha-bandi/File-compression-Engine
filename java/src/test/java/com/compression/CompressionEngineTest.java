package com.compression;

import com.compression.api.CompressionOptions;
import org.junit.jupiter.api.Test;

import java.nio.file.*;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

public class CompressionEngineTest {

    @Test
    void roundTripRepetitiveData() throws Exception {
        Path dir = Files.createTempDirectory("compression-test");
        Path input = dir.resolve("input.bin");
        Path compressed = dir.resolve("data.cve");
        Path output = dir.resolve("output.bin");

        String pattern = "Enterprise document metadata tenantId createdBy ";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 10000; i++) sb.append(pattern);

        Files.writeString(input, sb.toString());

        CompressionEngine engine =
                new CompressionEngine(CompressionOptions.defaults());

        engine.compress(input, compressed);
        engine.decompress(compressed, output);

        assertArrayEquals(Files.readAllBytes(input), Files.readAllBytes(output));
        assertTrue(Files.size(compressed) < Files.size(input));
    }

    @Test
    void roundTripRandomData() throws Exception {
        Path dir = Files.createTempDirectory("compression-test");
        Path input = dir.resolve("input.bin");
        Path compressed = dir.resolve("data.cve");
        Path output = dir.resolve("output.bin");

        byte[] data = new byte[100_000];
        new Random(42).nextBytes(data);
        Files.write(input, data);

        CompressionEngine engine =
                new CompressionEngine(CompressionOptions.defaults());

        engine.compress(input, compressed);
        engine.decompress(compressed, output);

        assertArrayEquals(data, Files.readAllBytes(output));
        assertEquals(data.length, Files.size(output));
    }
}
