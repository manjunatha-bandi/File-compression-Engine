package com.compression.core;

import com.compression.api.CompressionMethod;
import com.compression.api.CompressionOptions;
import com.compression.api.CompressionResult;

import java.io.ByteArrayOutputStream;

public final class LzCompressor implements CompressionAlgorithm {
    private final CompressionOptions options;

    public LzCompressor(CompressionOptions options) {
        this.options = options;
    }

    @Override
    public CompressionResult compress(byte[] input) {
        ByteArrayOutputStream out = new ByteArrayOutputStream(input.length);
        int i = 0;

        while (i < input.length) {
            int flagPos = out.size();
            out.write(0); // one bit per next token; 1=match, 0=literal
            int flags = 0;
            int bit = 0;

            while (bit < 8 && i < input.length) {
                Match match = findBestMatch(input, i);

                if (match.length >= options.minMatch()) {
                    flags |= (1 << bit);
                    writeMatch(out, match.distance, match.length);
                    i += match.length;
                } else {
                    out.write(input[i++] & 0xFF);
                }
                bit++;
            }

            byte[] bytes = out.toByteArray();
            bytes[flagPos] = (byte) flags;
            out.reset();
            out.writeBytes(bytes);
        }

        byte[] encoded = out.toByteArray();
        return new CompressionResult(encoded, input.length, encoded.length, CompressionMethod.LZ);
    }

    private Match findBestMatch(byte[] input, int pos) {
        int start = Math.max(0, pos - options.windowSize());
        int bestLength = 0;
        int bestDistance = 0;

        for (int candidate = pos - 1; candidate >= start; candidate--) {
            if (input[candidate] != input[pos]) continue;

            int max = Math.min(options.maxMatch(), input.length - pos);
            int length = 1;

            while (length < max) {
                int source = candidate + length;
                int target = pos + length;

                // Allow overlapping matches: source can move into the sequence
                // currently being reproduced by the decoder.
                byte sourceByte = source < pos ? input[source] : input[pos + (length % Math.max(1, pos - candidate))];

                if (sourceByte != input[target]) break;
                length++;
            }

            if (length > bestLength) {
                bestLength = length;
                bestDistance = pos - candidate;
                if (length == max) break;
            }
        }

        return new Match(bestDistance, bestLength);
    }

    private void writeMatch(ByteArrayOutputStream out, int distance, int length) {
        out.write((distance >>> 8) & 0xFF);
        out.write(distance & 0xFF);
        out.write(length & 0xFF);
    }

    @Override
    public byte[] decompress(byte[] input, int expectedSize) {
        ByteArrayOutputStream out = new ByteArrayOutputStream(expectedSize);
        int i = 0;

        while (i < input.length && out.size() < expectedSize) {
            int flags = input[i++] & 0xFF;

            for (int bit = 0; bit < 8 && i < input.length && out.size() < expectedSize; bit++) {
                boolean match = (flags & (1 << bit)) != 0;

                if (!match) {
                    out.write(input[i++] & 0xFF);
                    continue;
                }

                if (i + 3 > input.length) {
                    throw new IllegalArgumentException("Truncated LZ match token");
                }

                int distance = ((input[i++] & 0xFF) << 8) | (input[i++] & 0xFF);
                int length = input[i++] & 0xFF;

                if (distance <= 0 || distance > out.size()) {
                    throw new IllegalArgumentException("Invalid LZ distance: " + distance);
                }
                if (length < options.minMatch()) {
                    throw new IllegalArgumentException("Invalid LZ length: " + length);
                }

                for (int j = 0; j < length; j++) {
                    byte[] current = out.toByteArray();
                    out.write(current[out.size() - distance] & 0xFF);
                }
            }
        }

        byte[] result = out.toByteArray();
        if (result.length != expectedSize) {
            throw new IllegalArgumentException(
                    "Decompressed size mismatch. expected=" + expectedSize + ", actual=" + result.length);
        }
        return result;
    }

    private record Match(int distance, int length) {}
}
