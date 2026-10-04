package com.compression.api;

public enum CompressionMethod {
    STORE(0),
    LZ(1);

    private final int id;

    CompressionMethod(int id) {
        this.id = id;
    }

    public int id() {
        return id;
    }

    public static CompressionMethod fromId(int id) {
        for (CompressionMethod method : values()) {
            if (method.id == id) return method;
        }
        throw new IllegalArgumentException("Unknown compression method: " + id);
    }
}
