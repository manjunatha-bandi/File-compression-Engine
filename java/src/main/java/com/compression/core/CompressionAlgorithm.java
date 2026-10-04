package com.compression.core;

import com.compression.api.CompressionResult;

public interface CompressionAlgorithm {
    CompressionResult compress(byte[] input);
    byte[] decompress(byte[] input, int expectedSize);
}
