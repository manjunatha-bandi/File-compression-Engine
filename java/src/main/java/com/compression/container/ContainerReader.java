package com.compression.container;

import com.compression.api.CompressionMethod;

import java.io.*;
import java.util.zip.CRC32;

public final class ContainerReader implements Closeable {
    private final DataInputStream in;

    public ContainerReader(InputStream input) {
        this.in = new DataInputStream(new BufferedInputStream(input));
    }

    public Header readHeader() throws IOException {
        int magic = in.readInt();
        if (magic != ContainerFormat.MAGIC) throw new IOException("Invalid container magic");

        int version = in.readInt();
        if (version != ContainerFormat.VERSION) {
            throw new IOException("Unsupported container version: " + version);
        }

        int flags = in.readInt();
        long originalSize = in.readLong();
        int blockSize = in.readInt();
        int blockCount = in.readInt();
        in.readInt(); // reserved

        return new Header(version, flags, originalSize, blockSize, blockCount);
    }

    public Block readBlock() throws IOException {
        long blockId = in.readLong();
        int originalSize = in.readInt();
        int compressedSize = in.readInt();
        CompressionMethod method = CompressionMethod.fromId(in.readUnsignedByte());
        in.readUnsignedByte(); // flags
        long checksum = Integer.toUnsignedLong(in.readInt());
        in.readInt(); // reserved

        if (originalSize < 0 || compressedSize < 0) {
            throw new IOException("Invalid block sizes");
        }

        byte[] data = in.readNBytes(compressedSize);
        if (data.length != compressedSize) throw new EOFException("Truncated block");

        return new Block(blockId, originalSize, compressedSize, method, checksum, data);
    }

    public static void verifyChecksum(byte[] original, long expected) throws IOException {
        CRC32 crc = new CRC32();
        crc.update(original);
        long actual = crc.getValue();

        if (actual != expected) {
            throw new IOException("CRC mismatch. expected=" + expected + ", actual=" + actual);
        }
    }

    @Override
    public void close() throws IOException {
        in.close();
    }

    public record Header(int version, int flags, long originalSize, int blockSize, int blockCount) {}

    public record Block(long id, int originalSize, int compressedSize,
                        CompressionMethod method, long checksum, byte[] data) {}
}
