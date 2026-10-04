package com.compression.container;

public final class ContainerFormat {
    private ContainerFormat() {}

    public static final int MAGIC = 0x43564531; // "CVE1"
    public static final int VERSION = 1;
    public static final int HEADER_SIZE = 32;
    public static final int BLOCK_HEADER_SIZE = 24;
}
