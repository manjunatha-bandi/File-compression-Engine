package com.compression.container;

import com.compression.api.CompressionMethod;

import java.io.*;
import java.util.zip.CRC32;

public final class ContainerWriter implements Closeable {
    private final DataOutputStream out;

    public ContainerWriter(OutputStream output) {
        this.out = new DataOutputStream(new BufferedOutputStream(output));
    }

    public void writeHeader(long originalSize, int blockSize, int blockCount) throws IOException {
        out.writeInt(ContainerFormat.MAGIC);
        out.writeInt(ContainerFormat.VERSION);
        out.writeInt(0); // flags
        out.writeLong(originalSize);
        out.writeInt(blockSize);
        out.writeInt(blockCount);
        out.writeInt(0); // reserved
    }

    public void writeBlock(long blockId, int originalSize, byte[] compressed,
                           CompressionMethod method, byte[] original) throws IOException {
        CRC32 crc = new CRC32();
        crc.update(original);

        out.writeLong(blockId);
        out.writeInt(originalSize);
        out.writeInt(compressed.length);
        out.writeByte(method.id());
        out.writeByte(0); // block flags
        out.writeInt((int) crc.getValue());
        out.writeInt(0); // reserved
        out.write(compressed);
    }

    @Override
    public void close() throws IOException {
        out.flush();
        out.close();
    }
}
