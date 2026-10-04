package com.compression.cli;

import com.compression.CompressionEngine;
import com.compression.api.CompressionOptions;
import com.compression.container.ContainerReader;

import java.io.InputStream;
import java.nio.file.*;

public final class Main {
    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            usage();
            return;
        }

        switch (args[0].toLowerCase()) {
            case "server" -> {
                int port = args.length > 1 ? Integer.parseInt(args[1]) : 8080;
                com.compression.web.WebServer.startServer(port);
            }
            case "compress" -> {
                if (args.length < 3) { usage(); return; }
                CompressionEngine engine = new CompressionEngine(CompressionOptions.defaults());
                engine.compress(Path.of(args[1]), Path.of(args[2]));
                System.out.println("Compressed: " + args[1] + " -> " + args[2]);
            }
            case "decompress" -> {
                if (args.length < 3) { usage(); return; }
                CompressionEngine engine = new CompressionEngine(CompressionOptions.defaults());
                engine.decompress(Path.of(args[1]), Path.of(args[2]));
                System.out.println("Decompressed: " + args[1] + " -> " + args[2]);
            }
            case "inspect" -> {
                if (args.length < 2) { usage(); return; }
                inspect(Path.of(args[1]));
            }
            default -> usage();
        }
    }

    private static void inspect(Path file) throws Exception {
        try (InputStream in = Files.newInputStream(file);
             ContainerReader reader = new ContainerReader(in)) {
            var h = reader.readHeader();
            System.out.println("Version: " + h.version());
            System.out.println("Original size: " + h.originalSize());
            System.out.println("Block size: " + h.blockSize());
            System.out.println("Block count: " + h.blockCount());

            for (int i = 0; i < h.blockCount(); i++) {
                var b = reader.readBlock();
                System.out.printf(
                        "Block %d: method=%s original=%d compressed=%d%n",
                        b.id(), b.method(), b.originalSize(), b.compressedSize());
            }
        }
    }

    private static void usage() {
        System.out.println("""
                Usage:
                  server     [port (default 8080)]
                  compress   <input> <output.cve>
                  decompress <input.cve> <output>
                  inspect    <input.cve>
                """);
    }
}
