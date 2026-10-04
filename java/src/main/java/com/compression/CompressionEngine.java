package com.compression;

import com.compression.api.*;
import com.compression.container.*;
import com.compression.core.LzCompressor;

import java.io.*;
import java.nio.file.*;

public final class CompressionEngine {
    private final CompressionOptions options;

    public CompressionEngine(CompressionOptions options) {
        this.options = options;
    }

    public void compress(Path input, Path output) throws IOException {
        long originalSize = Files.size(input);
        int blockCount = (int) ((originalSize + options.blockSize() - 1) / options.blockSize());

        try (InputStream in = Files.newInputStream(input);
             OutputStream out = Files.newOutputStream(output);
             ContainerWriter writer = new ContainerWriter(out)) {

            writer.writeHeader(originalSize, options.blockSize(), blockCount);

            byte[] buffer = new byte[options.blockSize()];
            long blockId = 0;

            while (true) {
                int size = readBlock(in, buffer);
                if (size == 0) break;

                byte[] original = java.util.Arrays.copyOf(buffer, size);
                LzCompressor compressor = new LzCompressor(options);
                CompressionResult result = compressor.compress(original);

                if (result.compressedSize() >= size) {
                    writer.writeBlock(blockId++, size, original, CompressionMethod.STORE, original);
                } else {
                    writer.writeBlock(blockId++, size, result.data(), result.method(), original);
                }
            }
        }
    }

    public void decompress(Path input, Path output) throws IOException {
        try (InputStream in = Files.newInputStream(input);
             OutputStream out = Files.newOutputStream(output);
             ContainerReader reader = new ContainerReader(in)) {

            ContainerReader.Header header = reader.readHeader();

            for (int i = 0; i < header.blockCount(); i++) {
                ContainerReader.Block block = reader.readBlock();

                byte[] decoded;
                if (block.method() == CompressionMethod.STORE) {
                    decoded = block.data();
                    if (decoded.length != block.originalSize()) {
                        throw new IOException("STORE block size mismatch");
                    }
                } else if (block.method() == CompressionMethod.LZ) {
                    decoded = new LzCompressor(options)
                            .decompress(block.data(), block.originalSize());
                } else {
                    throw new IOException("Unsupported method: " + block.method());
                }

                ContainerReader.verifyChecksum(decoded, block.checksum());
                out.write(decoded);
            }
        }
    }

    private static int readBlock(InputStream in, byte[] buffer) throws IOException {
        int total = 0;
        while (total < buffer.length) {
            int n = in.read(buffer, total, buffer.length - total);
            if (n < 0) break;
            if (n == 0) continue;
            total += n;
        }
        return total;
    }
}
