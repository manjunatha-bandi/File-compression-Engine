package com.compression.api;

public record CompressionOptions(
        int blockSize,
        int windowSize,
        int minMatch,
        int maxMatch
) {
    public CompressionOptions {
        if (blockSize < 1024) throw new IllegalArgumentException("blockSize < 1024");
        if (windowSize < 256) throw new IllegalArgumentException("windowSize < 256");
        if (minMatch < 3) throw new IllegalArgumentException("minMatch < 3");
        if (maxMatch < minMatch) throw new IllegalArgumentException("maxMatch < minMatch");
        if (maxMatch > 255) throw new IllegalArgumentException("maxMatch > 255");
    }

    public static CompressionOptions defaults() {
        return new CompressionOptions(
                1024 * 1024,
                32 * 1024,
                3,
                255
        );
    }
}
