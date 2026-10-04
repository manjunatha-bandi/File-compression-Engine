package com.compression.api;

public record CompressionResult(
        byte[] data,
        int originalSize,
        int compressedSize,
        CompressionMethod method
) {}
