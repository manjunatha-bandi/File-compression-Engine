package com.compression.web;

import com.compression.CompressionEngine;
import com.compression.api.CompressionMethod;
import com.compression.api.CompressionOptions;
import com.compression.container.ContainerFormat;
import com.compression.container.ContainerReader;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public final class WebServer {
    private static final int DEFAULT_PORT = 8080;
    private static final Path WEB_DIR = resolveWebDir();

    private static Path resolveWebDir() {
        Path[] candidates = {
            Path.of("web"),
            Path.of("../web"),
            Path.of("../../web"),
            Path.of("frontend"),
            Path.of("../frontend")
        };
        for (Path p : candidates) {
            if (Files.isDirectory(p) && Files.exists(p.resolve("index.html"))) {
                return p.toAbsolutePath().normalize();
            }
        }
        return Path.of("web").toAbsolutePath().normalize();
    }

    public static void main(String[] args) throws IOException {
        int port = DEFAULT_PORT;
        if (args.length > 0) {
            try {
                port = Integer.parseInt(args[0]);
            } catch (NumberFormatException ignored) {}
        }
        startServer(port);
    }

    public static HttpServer startServer(int preferredPort) throws IOException {
        int port = preferredPort;
        HttpServer server = null;
        for (int i = 0; i < 10; i++) {
            try {
                server = HttpServer.create(new InetSocketAddress("localhost", port), 0);
                break;
            } catch (IOException e) {
                port++;
            }
        }
        if (server == null) {
            throw new IOException("Could not bind HTTP server on port " + preferredPort + " to " + port);
        }

        server.createContext("/api/status", new StatusHandler());
        server.createContext("/api/compress", new CompressHandler());
        server.createContext("/api/decompress", new DecompressHandler());
        server.createContext("/api/inspect", new InspectHandler());
        server.createContext("/api/samples", new SamplesHandler());
        server.createContext("/", new StaticFileHandler());

        server.setExecutor(java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor());
        server.start();

        System.out.println("==================================================================");
        System.out.println("  COMPRESSION ENGINE v0.1 - WEB DASHBOARD");
        System.out.println("  URL: http://localhost:" + port);
        System.out.println("  Web Root: " + WEB_DIR);
        System.out.println("  API Endpoints:");
        System.out.println("    - GET  /api/status");
        System.out.println("    - POST /api/compress");
        System.out.println("    - POST /api/decompress");
        System.out.println("    - POST /api/inspect");
        System.out.println("    - GET  /api/samples");
        System.out.println("==================================================================");

        return server;
    }

    private static void applyCors(HttpExchange exchange) {
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type, Accept, X-File-Name, X-Block-Size, X-Window-Size, X-Min-Match, X-Max-Match, X-Action");
    }

    private static boolean handleOptions(HttpExchange exchange) throws IOException {
        if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
            applyCors(exchange);
            exchange.sendResponseHeaders(204, -1);
            exchange.close();
            return true;
        }
        return false;
    }

    private static byte[] readRequestBody(HttpExchange exchange) throws IOException {
        try (InputStream in = exchange.getRequestBody()) {
            return in.readAllBytes();
        }
    }

    private static Map<String, String> parseQueryParams(String query) {
        Map<String, String> params = new HashMap<>();
        if (query == null || query.isBlank()) return params;
        for (String pair : query.split("&")) {
            int idx = pair.indexOf('=');
            if (idx > 0) {
                String key = URLDecoder.decode(pair.substring(0, idx), StandardCharsets.UTF_8);
                String val = URLDecoder.decode(pair.substring(idx + 1), StandardCharsets.UTF_8);
                params.put(key, val);
            } else if (!pair.isBlank()) {
                params.put(URLDecoder.decode(pair, StandardCharsets.UTF_8), "");
            }
        }
        return params;
    }

    private static CompressionOptions parseOptions(HttpExchange exchange, Map<String, String> query) {
        int blockSize = 1024 * 1024;
        int windowSize = 32 * 1024;
        int minMatch = 3;
        int maxMatch = 255;

        String bs = exchange.getRequestHeaders().getFirst("X-Block-Size");
        if (bs == null) bs = query.get("blockSize");
        if (bs != null) {
            try { blockSize = Math.max(1024, Integer.parseInt(bs)); } catch (Exception ignored) {}
        }

        String ws = exchange.getRequestHeaders().getFirst("X-Window-Size");
        if (ws == null) ws = query.get("windowSize");
        if (ws != null) {
            try { windowSize = Math.max(256, Integer.parseInt(ws)); } catch (Exception ignored) {}
        }

        String minM = exchange.getRequestHeaders().getFirst("X-Min-Match");
        if (minM == null) minM = query.get("minMatch");
        if (minM != null) {
            try { minMatch = Math.max(3, Integer.parseInt(minM)); } catch (Exception ignored) {}
        }

        String maxM = exchange.getRequestHeaders().getFirst("X-Max-Match");
        if (maxM == null) maxM = query.get("maxMatch");
        if (maxM != null) {
            try { maxMatch = Math.min(255, Math.max(minMatch, Integer.parseInt(maxM))); } catch (Exception ignored) {}
        }

        return new CompressionOptions(blockSize, windowSize, minMatch, maxMatch);
    }

    static class StatusHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            applyCors(exchange);
            if (handleOptions(exchange)) return;

            String json = """
                    {
                      "status": "online",
                      "engine": "Compression Engine v0.1",
                      "javaVersion": "%s",
                      "container": {
                        "magic": "CVE1",
                        "magicHex": "0x43564531",
                        "version": 1,
                        "headerSize": 32,
                        "blockHeaderSize": 24
                      },
                      "defaultOptions": {
                        "blockSize": 1048576,
                        "windowSize": 32768,
                        "minMatch": 3,
                        "maxMatch": 255
                      },
                      "methods": ["STORE", "LZ"]
                    }
                    """.formatted(System.getProperty("java.version"));

            byte[] resp = json.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
            exchange.sendResponseHeaders(200, resp.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(resp);
            }
        }
    }

    static class CompressHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            applyCors(exchange);
            if (handleOptions(exchange)) return;

            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(405, -1);
                return;
            }

            Map<String, String> query = parseQueryParams(exchange.getRequestURI().getQuery());
            CompressionOptions options = parseOptions(exchange, query);

            String fileName = exchange.getRequestHeaders().getFirst("X-File-Name");
            if (fileName == null) fileName = query.getOrDefault("filename", "data.bin");

            byte[] inputBytes = readRequestBody(exchange);
            if (inputBytes.length == 0) {
                sendError(exchange, 400, "Empty payload for compression");
                return;
            }

            long startTime = System.nanoTime();
            Path tempInput = Files.createTempFile("comp-in-", ".tmp");
            Path tempOutput = Files.createTempFile("comp-out-", ".cve");

            try {
                Files.write(tempInput, inputBytes);

                CompressionEngine engine = new CompressionEngine(options);
                engine.compress(tempInput, tempOutput);

                long durationNs = System.nanoTime() - startTime;
                double durationMs = durationNs / 1_000_000.0;

                byte[] compressedBytes = Files.readAllBytes(tempOutput);
                long originalSize = inputBytes.length;
                long compressedSize = compressedBytes.length;
                double ratio = compressedSize > 0 ? (double) originalSize / compressedSize : 1.0;
                double savingsPercent = originalSize > 0 ? Math.max(0, (1.0 - ((double) compressedSize / originalSize)) * 100.0) : 0.0;

                List<Map<String, Object>> blockStats = new ArrayList<>();
                try (InputStream cis = Files.newInputStream(tempOutput);
                     ContainerReader reader = new ContainerReader(cis)) {
                    var h = reader.readHeader();
                    for (int i = 0; i < h.blockCount(); i++) {
                        var b = reader.readBlock();
                        Map<String, Object> bm = new LinkedHashMap<>();
                        bm.put("id", b.id());
                        bm.put("method", b.method().name());
                        bm.put("methodId", b.method().id());
                        bm.put("originalSize", b.originalSize());
                        bm.put("compressedSize", b.compressedSize());
                        bm.put("checksum", b.checksum());
                        bm.put("checksumHex", String.format("%08X", b.checksum()));
                        double bRatio = b.compressedSize() > 0 ? (double) b.originalSize() / b.compressedSize() : 1.0;
                        bm.put("ratio", Math.round(bRatio * 100.0) / 100.0);
                        blockStats.add(bm);
                    }
                }

                String action = exchange.getRequestHeaders().getFirst("X-Action");
                if (action == null) action = query.getOrDefault("action", "json");

                if ("download".equalsIgnoreCase(action)) {
                    String outName = fileName.endsWith(".cve") ? fileName : fileName + ".cve";
                    exchange.getResponseHeaders().set("Content-Type", "application/octet-stream");
                    exchange.getResponseHeaders().set("Content-Disposition", "attachment; filename=\"" + outName + "\"");
                    exchange.getResponseHeaders().set("X-Original-Size", String.valueOf(originalSize));
                    exchange.getResponseHeaders().set("X-Compressed-Size", String.valueOf(compressedSize));
                    exchange.getResponseHeaders().set("X-Ratio", String.format(Locale.ROOT, "%.2f", ratio));
                    exchange.getResponseHeaders().set("X-Savings", String.format(Locale.ROOT, "%.2f", savingsPercent));
                    exchange.getResponseHeaders().set("X-Elapsed-Ms", String.format(Locale.ROOT, "%.2f", durationMs));
                    exchange.sendResponseHeaders(200, compressedBytes.length);
                    try (OutputStream os = exchange.getResponseBody()) {
                        os.write(compressedBytes);
                    }
                } else {
                    StringBuilder sb = new StringBuilder();
                    sb.append("{\n");
                    sb.append("  \"success\": true,\n");
                    sb.append("  \"originalName\": ").append(quote(fileName)).append(",\n");
                    sb.append("  \"originalSize\": ").append(originalSize).append(",\n");
                    sb.append("  \"compressedSize\": ").append(compressedSize).append(",\n");
                    sb.append("  \"ratio\": ").append(String.format(Locale.ROOT, "%.2f", ratio)).append(",\n");
                    sb.append("  \"savingsPercent\": ").append(String.format(Locale.ROOT, "%.2f", savingsPercent)).append(",\n");
                    sb.append("  \"durationMs\": ").append(String.format(Locale.ROOT, "%.2f", durationMs)).append(",\n");
                    sb.append("  \"blockCount\": ").append(blockStats.size()).append(",\n");
                    sb.append("  \"blockSize\": ").append(options.blockSize()).append(",\n");
                    sb.append("  \"windowSize\": ").append(options.windowSize()).append(",\n");
                    sb.append("  \"blocks\": [");
                    for (int i = 0; i < blockStats.size(); i++) {
                        Map<String, Object> bm = blockStats.get(i);
                        if (i > 0) sb.append(",");
                        sb.append(String.format(Locale.ROOT,
                                "{\"id\":%s,\"method\":%s,\"methodId\":%s,\"originalSize\":%s,\"compressedSize\":%s,\"checksum\":%s,\"checksumHex\":%s,\"ratio\":%s}",
                                bm.get("id"), quote(bm.get("method").toString()), bm.get("methodId"),
                                bm.get("originalSize"), bm.get("compressedSize"), bm.get("checksum"),
                                quote(bm.get("checksumHex").toString()), bm.get("ratio")));
                    }
                    sb.append("],\n");
                    sb.append("  \"dataBase64\": ").append(quote(Base64.getEncoder().encodeToString(compressedBytes))).append("\n");
                    sb.append("}");

                    byte[] resp = sb.toString().getBytes(StandardCharsets.UTF_8);
                    exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
                    exchange.sendResponseHeaders(200, resp.length);
                    try (OutputStream os = exchange.getResponseBody()) {
                        os.write(resp);
                    }
                }
            } catch (Exception e) {
                sendError(exchange, 500, "Compression error: " + e.getMessage());
            } finally {
                Files.deleteIfExists(tempInput);
                Files.deleteIfExists(tempOutput);
            }
        }
    }

    static class DecompressHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            applyCors(exchange);
            if (handleOptions(exchange)) return;

            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(405, -1);
                return;
            }

            Map<String, String> query = parseQueryParams(exchange.getRequestURI().getQuery());
            CompressionOptions options = parseOptions(exchange, query);

            String fileName = exchange.getRequestHeaders().getFirst("X-File-Name");
            if (fileName == null) fileName = query.getOrDefault("filename", "restored.bin");
            if (fileName.endsWith(".cve")) {
                fileName = fileName.substring(0, fileName.length() - 4);
            }

            byte[] inputBytes = readRequestBody(exchange);
            if (inputBytes.length < ContainerFormat.HEADER_SIZE) {
                sendError(exchange, 400, "File too small to be a valid CVE container");
                return;
            }

            long startTime = System.nanoTime();
            Path tempInput = Files.createTempFile("decomp-in-", ".cve");
            Path tempOutput = Files.createTempFile("decomp-out-", ".bin");

            try {
                Files.write(tempInput, inputBytes);

                List<Map<String, Object>> blockStats = new ArrayList<>();
                long origSizeFromHeader;
                int blockCount;
                int blockSize;
                try (InputStream cis = Files.newInputStream(tempInput);
                     ContainerReader reader = new ContainerReader(cis)) {
                    var h = reader.readHeader();
                    origSizeFromHeader = h.originalSize();
                    blockCount = h.blockCount();
                    blockSize = h.blockSize();

                    for (int i = 0; i < blockCount; i++) {
                        var b = reader.readBlock();
                        Map<String, Object> bm = new LinkedHashMap<>();
                        bm.put("id", b.id());
                        bm.put("method", b.method().name());
                        bm.put("originalSize", b.originalSize());
                        bm.put("compressedSize", b.compressedSize());
                        bm.put("checksumHex", String.format("%08X", b.checksum()));
                        blockStats.add(bm);
                    }
                }

                CompressionEngine engine = new CompressionEngine(options);
                engine.decompress(tempInput, tempOutput);

                long durationNs = System.nanoTime() - startTime;
                double durationMs = durationNs / 1_000_000.0;

                byte[] decompressedBytes = Files.readAllBytes(tempOutput);
                long restoredSize = decompressedBytes.length;

                String action = exchange.getRequestHeaders().getFirst("X-Action");
                if (action == null) action = query.getOrDefault("action", "json");

                if ("download".equalsIgnoreCase(action)) {
                    exchange.getResponseHeaders().set("Content-Type", "application/octet-stream");
                    exchange.getResponseHeaders().set("Content-Disposition", "attachment; filename=\"" + fileName + "\"");
                    exchange.getResponseHeaders().set("X-Restored-Size", String.valueOf(restoredSize));
                    exchange.getResponseHeaders().set("X-Elapsed-Ms", String.format(Locale.ROOT, "%.2f", durationMs));
                    exchange.sendResponseHeaders(200, decompressedBytes.length);
                    try (OutputStream os = exchange.getResponseBody()) {
                        os.write(decompressedBytes);
                    }
                } else {
                    StringBuilder sb = new StringBuilder();
                    sb.append("{\n");
                    sb.append("  \"success\": true,\n");
                    sb.append("  \"restoredName\": ").append(quote(fileName)).append(",\n");
                    sb.append("  \"containerSize\": ").append(inputBytes.length).append(",\n");
                    sb.append("  \"restoredSize\": ").append(restoredSize).append(",\n");
                    sb.append("  \"headerOriginalSize\": ").append(origSizeFromHeader).append(",\n");
                    sb.append("  \"blockCount\": ").append(blockCount).append(",\n");
                    sb.append("  \"blockSize\": ").append(blockSize).append(",\n");
                    sb.append("  \"crcVerified\": true,\n");
                    sb.append("  \"durationMs\": ").append(String.format(Locale.ROOT, "%.2f", durationMs)).append(",\n");
                    sb.append("  \"blocks\": [");
                    for (int i = 0; i < blockStats.size(); i++) {
                        Map<String, Object> bm = blockStats.get(i);
                        if (i > 0) sb.append(",");
                        sb.append(String.format(Locale.ROOT,
                                "{\"id\":%s,\"method\":%s,\"originalSize\":%s,\"compressedSize\":%s,\"checksumHex\":%s}",
                                bm.get("id"), quote(bm.get("method").toString()),
                                bm.get("originalSize"), bm.get("compressedSize"),
                                quote(bm.get("checksumHex").toString())));
                    }
                    sb.append("],\n");
                    sb.append("  \"dataBase64\": ").append(quote(Base64.getEncoder().encodeToString(decompressedBytes))).append("\n");
                    sb.append("}");

                    byte[] resp = sb.toString().getBytes(StandardCharsets.UTF_8);
                    exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
                    exchange.sendResponseHeaders(200, resp.length);
                    try (OutputStream os = exchange.getResponseBody()) {
                        os.write(resp);
                    }
                }
            } catch (Exception e) {
                sendError(exchange, 400, "Decompression failed: " + e.getMessage());
            } finally {
                Files.deleteIfExists(tempInput);
                Files.deleteIfExists(tempOutput);
            }
        }
    }

    static class InspectHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            applyCors(exchange);
            if (handleOptions(exchange)) return;

            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(405, -1);
                return;
            }

            byte[] inputBytes = readRequestBody(exchange);
            if (inputBytes.length < ContainerFormat.HEADER_SIZE) {
                sendError(exchange, 400, "Payload smaller than container header size (32 bytes)");
                return;
            }

            try (InputStream in = new ByteArrayInputStream(inputBytes);
                 ContainerReader reader = new ContainerReader(in)) {

                var h = reader.readHeader();
                long totalCompressed = ContainerFormat.HEADER_SIZE;
                long totalOriginal = h.originalSize();

                List<Map<String, Object>> blocks = new ArrayList<>();
                for (int i = 0; i < h.blockCount(); i++) {
                    var b = reader.readBlock();
                    totalCompressed += ContainerFormat.BLOCK_HEADER_SIZE + b.compressedSize();

                    Map<String, Object> bm = new LinkedHashMap<>();
                    bm.put("id", b.id());
                    bm.put("method", b.method().name());
                    bm.put("methodId", b.method().id());
                    bm.put("originalSize", b.originalSize());
                    bm.put("compressedSize", b.compressedSize());
                    bm.put("checksum", b.checksum());
                    bm.put("checksumHex", String.format("%08X", b.checksum()));
                    double ratio = b.compressedSize() > 0 ? (double) b.originalSize() / b.compressedSize() : 1.0;
                    double savings = b.originalSize() > 0 ? Math.max(0, (1.0 - ((double) b.compressedSize() / b.originalSize())) * 100.0) : 0;
                    bm.put("ratio", Math.round(ratio * 100.0) / 100.0);
                    bm.put("savingsPercent", Math.round(savings * 10.0) / 10.0);
                    blocks.add(bm);
                }

                double overallRatio = totalCompressed > 0 ? (double) totalOriginal / totalCompressed : 1.0;
                double overallSavings = totalOriginal > 0 ? Math.max(0, (1.0 - ((double) totalCompressed / totalOriginal)) * 100.0) : 0;

                StringBuilder sb = new StringBuilder();
                sb.append("{\n");
                sb.append("  \"valid\": true,\n");
                sb.append("  \"magic\": \"CVE1\",\n");
                sb.append("  \"magicHex\": \"0x43564531\",\n");
                sb.append("  \"version\": ").append(h.version()).append(",\n");
                sb.append("  \"flags\": ").append(h.flags()).append(",\n");
                sb.append("  \"originalSize\": ").append(h.originalSize()).append(",\n");
                sb.append("  \"blockSize\": ").append(h.blockSize()).append(",\n");
                sb.append("  \"blockCount\": ").append(h.blockCount()).append(",\n");
                sb.append("  \"totalFileSize\": ").append(inputBytes.length).append(",\n");
                sb.append("  \"calculatedCompressedSize\": ").append(totalCompressed).append(",\n");
                sb.append("  \"overallRatio\": ").append(String.format(Locale.ROOT, "%.2f", overallRatio)).append(",\n");
                sb.append("  \"overallSavings\": ").append(String.format(Locale.ROOT, "%.2f", overallSavings)).append(",\n");
                sb.append("  \"blocks\": [");
                for (int i = 0; i < blocks.size(); i++) {
                    Map<String, Object> b = blocks.get(i);
                    if (i > 0) sb.append(",");
                    sb.append(String.format(Locale.ROOT,
                            "{\"id\":%s,\"method\":%s,\"methodId\":%s,\"originalSize\":%s,\"compressedSize\":%s,\"checksum\":%s,\"checksumHex\":%s,\"ratio\":%s,\"savingsPercent\":%s}",
                            b.get("id"), quote(b.get("method").toString()), b.get("methodId"),
                            b.get("originalSize"), b.get("compressedSize"), b.get("checksum"),
                            quote(b.get("checksumHex").toString()), b.get("ratio"), b.get("savingsPercent")));
                }
                sb.append("]\n");
                sb.append("}");

                byte[] resp = sb.toString().getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
                exchange.sendResponseHeaders(200, resp.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(resp);
                }
            } catch (Exception e) {
                sendError(exchange, 400, "Failed to parse CVE container: " + e.getMessage());
            }
        }
    }

    static class SamplesHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            applyCors(exchange);
            if (handleOptions(exchange)) return;

            Map<String, String> query = parseQueryParams(exchange.getRequestURI().getQuery());
            String type = query.getOrDefault("type", "repetitive");

            String sampleContent;
            String fileName;
            String mime = "text/plain; charset=utf-8";

            switch (type.toLowerCase()) {
                case "json" -> {
                    fileName = "sample_server_logs.json";
                    sampleContent = """
                            [
                              {"timestamp": "2026-10-04T12:00:00Z", "level": "INFO", "service": "auth-service", "message": "User session validated successfully for tenantId=corp-enterprise-9921", "status": 200},
                              {"timestamp": "2026-10-04T12:00:01Z", "level": "INFO", "service": "auth-service", "message": "User session validated successfully for tenantId=corp-enterprise-9921", "status": 200},
                              {"timestamp": "2026-10-04T12:00:02Z", "level": "INFO", "service": "doc-engine", "message": "Enterprise document metadata indexed tenantId=corp-enterprise-9921 createdBy=system", "status": 200},
                              {"timestamp": "2026-10-04T12:00:03Z", "level": "INFO", "service": "doc-engine", "message": "Enterprise document metadata indexed tenantId=corp-enterprise-9921 createdBy=system", "status": 200},
                              {"timestamp": "2026-10-04T12:00:04Z", "level": "WARN", "service": "cache-manager", "message": "Cache cluster node rebalancing initiated for tenantId=corp-enterprise-9921", "status": 202},
                              {"timestamp": "2026-10-04T12:00:05Z", "level": "INFO", "service": "doc-engine", "message": "Enterprise document metadata indexed tenantId=corp-enterprise-9921 createdBy=system", "status": 200}
                            ]
                            """.repeat(15);
                }
                case "random" -> {
                    fileName = "sample_random_entropy.bin";
                    byte[] randomBytes = new byte[8192];
                    new Random(1337).nextBytes(randomBytes);
                    exchange.getResponseHeaders().set("Content-Type", "application/octet-stream");
                    exchange.getResponseHeaders().set("Content-Disposition", "attachment; filename=\"" + fileName + "\"");
                    exchange.sendResponseHeaders(200, randomBytes.length);
                    try (OutputStream os = exchange.getResponseBody()) {
                        os.write(randomBytes);
                    }
                    return;
                }
                case "code" -> {
                    fileName = "sample_code.java";
                    sampleContent = """
                            package com.compression.demo;
                            
                            // High repetition code pattern
                            public class DataProcessor {
                                private final String tenantId = "tenant-prod-alpha-001";
                                private final String serviceName = "compression-service-v1";
                                
                                public void processRecord(int id) {
                                    System.out.println("Processing record id=" + id + " for tenant=" + tenantId);
                                    System.out.println("Validating record id=" + id + " for service=" + serviceName);
                                    System.out.println("Completed record id=" + id + " for tenant=" + tenantId);
                                }
                            }
                            """.repeat(20);
                }
                default -> {
                    fileName = "sample_repetitive.txt";
                    sampleContent = "Enterprise document metadata tenantId createdBy compression engine v0.1 block storage foundation ".repeat(150);
                }
            }

            byte[] resp = sampleContent.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", mime);
            exchange.getResponseHeaders().set("X-Sample-Name", fileName);
            exchange.sendResponseHeaders(200, resp.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(resp);
            }
        }
    }

    static class StaticFileHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            applyCors(exchange);
            if (handleOptions(exchange)) return;

            String path = exchange.getRequestURI().getPath();
            if (path.equals("/")) {
                path = "/index.html";
            }

            Path filePath = WEB_DIR.resolve(path.substring(1)).normalize();

            // Prevent path traversal
            if (!filePath.startsWith(WEB_DIR) || !Files.exists(filePath) || Files.isDirectory(filePath)) {
                sendError(exchange, 404, "File not found: " + path);
                return;
            }

            String contentType = getMimeType(filePath.getFileName().toString());
            byte[] bytes = Files.readAllBytes(filePath);

            exchange.getResponseHeaders().set("Content-Type", contentType);
            exchange.getResponseHeaders().set("Cache-Control", "no-cache, no-store, must-revalidate");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        }
    }

    private static String getMimeType(String name) {
        if (name.endsWith(".html")) return "text/html; charset=utf-8";
        if (name.endsWith(".css")) return "text/css; charset=utf-8";
        if (name.endsWith(".js")) return "application/javascript; charset=utf-8";
        if (name.endsWith(".json")) return "application/json; charset=utf-8";
        if (name.endsWith(".svg")) return "image/svg+xml";
        if (name.endsWith(".png")) return "image/png";
        if (name.endsWith(".ico")) return "image/x-icon";
        return "application/octet-stream";
    }

    private static void sendError(HttpExchange exchange, int status, String message) throws IOException {
        String json = "{\"error\": " + quote(message) + "}";
        byte[] resp = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(status, resp.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(resp);
        }
    }

    private static String quote(String s) {
        if (s == null) return "null";
        StringBuilder sb = new StringBuilder("\"");
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < ' ') {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        sb.append("\"");
        return sb.toString();
    }
}
