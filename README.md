# Compression Engine v0.1

> A from-scratch, zero-dependency Java 21/25 compression engine with a custom binary container format, LZ sliding-window encoding, per-block CRC32 integrity validation, and a modern interactive web dashboard.

---

## Table of Contents

1. [Project Purpose](#1-project-purpose)
2. [Project Structure (Organized by Language)](#2-project-structure-organized-by-language)
3. [Architecture Deep Dive](#3-architecture-deep-dive)
   - [CVE1 Binary Container Format](#a-cve1-binary-container-format)
   - [Block-Based Streaming Architecture](#b-block-based-streaming-architecture)
   - [LZ Sliding-Window Algorithm](#c-lz-sliding-window-algorithm)
   - [STORE Fallback Strategy](#d-store-fallback-strategy)
   - [CRC32 Integrity Validation](#e-crc32-integrity-validation)
4. [File-by-File Explanation](#4-file-by-file-explanation)
   - [Java Engine](#java-engine)
   - [Web Frontend](#web-frontend)
   - [Launch Scripts](#launch-scripts)
5. [How to Run](#5-how-to-run)
   - [Web Dashboard](#option-a-web-dashboard-recommended)
   - [Command Line Interface](#option-b-command-line-interface-cli)
   - [Running Tests with Maven](#option-c-running-tests-with-maven)
6. [Capabilities](#6-capabilities)
7. [Limitations](#7-limitations)
8. [Roadmap — What Comes Next](#8-roadmap--what-comes-next)
9. [Pushing to GitHub](#9-pushing-to-github)

---

## 1. Project Purpose

This project is an **engineering foundation** for a custom data compression system built entirely from first principles — no external compression libraries (no ZLIB, no LZ4, no Snappy). The goal is to understand and implement each layer of a compression pipeline:

| Layer                  | What it does                                                                           |
| ---------------------- | -------------------------------------------------------------------------------------- |
| **Container Format**   | Defines the on-disk binary structure for compressed archives                           |
| **Block Segmentation** | Splits large files into independent, streamable chunks                                 |
| **LZ Algorithm**       | Finds and encodes repeated byte sequences in a sliding window                          |
| **Integrity Checking** | Validates each block with a CRC32 checksum upon decompression                          |
| **Adaptive Strategy**  | Automatically falls back to raw storage when compression expands data                  |
| **Web Interface**      | Lets users compress, decompress, inspect, and visualize all of the above interactively |

This is **not** a production-ready replacement for `gzip`, `zstd`, or `brotli`. It is a transparent, educational, and extensible starting point for building a serious compression engine from scratch.

---

## 2. Project Structure (Organized by Language)

```
compression-engine-v0.1/
│
├── java/                               ← All Java source code and build artifacts
│   ├── pom.xml                         ← Maven build configuration
│   ├── target/
│   │   └── classes/                    ← Compiled .class bytecode files
│   └── src/
│       ├── main/java/com/compression/
│       │   ├── CompressionEngine.java  ← Core engine orchestrator
│       │   ├── api/
│       │   │   ├── CompressionMethod.java   ← STORE / LZ enum
│       │   │   ├── CompressionOptions.java  ← Tuning parameters record
│       │   │   └── CompressionResult.java   ← Algorithm output record
│       │   ├── cli/
│       │   │   └── Main.java           ← Terminal CLI entry point
│       │   ├── container/
│       │   │   ├── ContainerFormat.java     ← Magic bytes & size constants
│       │   │   ├── ContainerReader.java     ← Binary container parser
│       │   │   └── ContainerWriter.java     ← Binary container serializer
│       │   ├── core/
│       │   │   ├── CompressionAlgorithm.java ← Interface (compress / decompress)
│       │   │   └── LzCompressor.java        ← Sliding-window LZ implementation
│       │   └── web/
│       │       └── WebServer.java      ← Built-in HTTP REST API server
│       └── test/java/com/compression/
│           └── CompressionEngineTest.java   ← JUnit 5 round-trip tests
│
├── web/                                ← Web Frontend (HTML, CSS, JavaScript)
│   ├── index.html                      ← Single-page application shell & UI
│   ├── styles.css                      ← Dark glassmorphism design system
│   ├── app.js                          ← UI logic, drag-and-drop, API calls
│   └── cve-engine.js                   ← Client-side JS port of CVE1 + LZ engine
│
├── start-web.bat                       ← 1-click Windows CMD launcher
├── start-web.ps1                       ← 1-click PowerShell launcher
└── README.md                           ← This file
```

---

## 3. Architecture Deep Dive

### A. CVE1 Binary Container Format

Every compressed file produced by this engine uses the **CVE1** container format — a fully custom binary specification with a fixed **32-byte container header** followed by a sequence of independent blocks.

#### Container Header Layout (32 bytes total)

| Offset | Bytes | Field                  | Description                                                                                                      |
| ------ | ----- | ---------------------- | ---------------------------------------------------------------------------------------------------------------- |
| `0x00` | 4     | **Magic Signature**    | `0x43564531` — the ASCII encoding of `"CVE1"`. Used to identify and validate the file format.                    |
| `0x04` | 4     | **Container Version**  | Currently `1`. Enables future format evolution without breaking backwards compatibility.                         |
| `0x08` | 4     | **Flags**              | Reserved bitfield for future container-level features (e.g. encryption, multi-stream). Currently `0`.            |
| `0x0C` | 8     | **Original File Size** | The exact number of bytes in the uncompressed source file. Required to pre-allocate output during decompression. |
| `0x14` | 4     | **Block Size**         | The chunk size used to segment the input (default: `1,048,576` bytes = 1 MB).                                    |
| `0x18` | 4     | **Block Count**        | Total number of blocks stored in the container.                                                                  |
| `0x1C` | 4     | **Reserved**           | Padding to 32 bytes for future use. Currently `0`.                                                               |

#### Block Header Layout (24 bytes per block)

After the container header, each block immediately follows in sequence, each prefixed by its own 24-byte header:

| Offset | Bytes | Field               | Description                                                                                           |
| ------ | ----- | ------------------- | ----------------------------------------------------------------------------------------------------- |
| `0x00` | 8     | **Block ID**        | Zero-indexed block sequence number (e.g. `0`, `1`, `2` …). Allows future random-access block seeking. |
| `0x08` | 4     | **Original Size**   | Number of uncompressed bytes in this block.                                                           |
| `0x0C` | 4     | **Compressed Size** | Number of bytes stored on disk for this block's payload.                                              |
| `0x10` | 1     | **Method**          | Compression method: `0 = STORE` (raw copy), `1 = LZ` (sliding-window encoded).                        |
| `0x11` | 1     | **Block Flags**     | Reserved for per-block feature flags (e.g. per-block encryption). Currently `0`.                      |
| `0x12` | 4     | **CRC32 Checksum**  | IEEE 802.3 CRC32 of the **original uncompressed** block data. Verified upon decompression.            |
| `0x16` | 4     | **Reserved**        | Padding to 24 bytes. Currently `0`.                                                                   |

After the 24-byte header, exactly `compressedSize` payload bytes follow immediately.

---

### B. Block-Based Streaming Architecture

The engine reads and compresses the input **one block at a time** without loading the entire file into memory. This enables:

- **Streaming large files**: Files larger than available RAM can be processed.
- **Independent block decompression**: Future versions can decompress only a specific block (random access).
- **Parallel processing potential**: Each block is completely self-contained and can be processed concurrently.

The block size can be tuned via `CompressionOptions`. Default: **1 MB**. Valid range: **1 KB – any size**.

---

### C. LZ Sliding-Window Algorithm

The core compression algorithm is implemented in [`LzCompressor.java`](java/src/main/java/com/compression/core/LzCompressor.java).

#### How it works — step by step:

**Step 1 — Maintain a sliding window.**  
The encoder keeps a look-back window of previously seen bytes (default: **32 KB = 32,768 bytes**). This is the "dictionary" from which matches are found.

**Step 2 — At each position, search for the longest match.**  
The encoder scans backwards through the window starting from the current byte position. When a match is found:

- Minimum match length: **3 bytes** (encoding 1 byte as a 3-byte match token would expand data, so shorter matches are never used).
- Maximum match length: **255 bytes** (constrained by the 1-byte `length` field in the token).

**Step 3 — Overlapping matches (Run-Length Encoding support).**  
The encoder supports matches that extend past the start of the current copy position — i.e., the decoder reads bytes it is currently writing. This enables efficient encoding of repeating runs like `AAAAAAA...` using a single match token.

**Step 4 — Build 8-token groups with a 1-byte flag mask.**  
Tokens are batched in groups of exactly 8. Before each group, one **flag byte** is written:

- Bit `k = 0` → Token `k` is a **literal**: the raw uncompressed byte is stored (1 byte).
- Bit `k = 1` → Token `k` is a **match**: a 3-byte back-reference `(distance: 2 bytes, length: 1 byte)` is stored.

This flag-mask grouping is similar to the approach used in DEFLATE but is entirely custom — the bit numbering, token layout, and encoding choices differ.

**Step 5 — Decoder mirrors encoder.**  
On decompression, each flag byte is read, and for each of its 8 bits:

- If `0`: read 1 literal byte, emit it.
- If `1`: read `distance` and `length`, copy `length` bytes from `output[current - distance]` into the output stream.

---

### D. STORE Fallback Strategy

After compressing a block with LZ, the engine compares the compressed size to the original size:

```
if (compressedSize >= originalSize) → STORE fallback
```

In STORE mode, the raw bytes are written directly with `method = 0`. This guarantees **zero negative expansion** — the output file is **never larger** than it would need to be. Files that cannot be compressed (random data, encrypted content, JPEG/PNG images, ZIP archives) will be stored verbatim.

---

### E. CRC32 Integrity Validation

When writing each block, the engine computes an **IEEE 802.3 CRC32** checksum over the **original uncompressed block bytes** using Java's built-in `java.util.zip.CRC32`. This checksum is stored in the block header.

On decompression:

1. The block payload is decoded.
2. CRC32 is recomputed over the decoded bytes.
3. The recomputed value is compared to the stored checksum.
4. If they differ → an `IOException` is thrown: `"CRC mismatch. expected=X, actual=Y"`.

This catches both data corruption in transit and bugs in the compression/decompression logic.

---

## 4. File-by-File Explanation

### Java Engine

#### `CompressionEngine.java`

The **top-level orchestrator**. Provides two public methods:

- `compress(Path input, Path output)` — reads input in blocks, LZ-compresses each, writes the CVE1 container.
- `decompress(Path input, Path output)` — reads each block from a CVE1 container, decodes it (LZ or STORE), verifies CRC32, and writes the restored bytes.

It does not implement any algorithm itself — it delegates to `LzCompressor` and the container layer.

---

#### `api/CompressionMethod.java`

A simple Java `enum` with two values:

- `STORE(0)` — raw uncompressed storage.
- `LZ(1)` — custom sliding-window LZ compression.

Each has an integer ID used as the single method byte in the block header. The `fromId(int)` method maps a header byte back to the enum constant on read.

---

#### `api/CompressionOptions.java`

A Java `record` holding all tunable parameters with validation:

- `blockSize` — chunk size in bytes. Min: `1024`. Default: `1,048,576` (1 MB).
- `windowSize` — LZ look-back window size in bytes. Min: `256`. Default: `32,768` (32 KB).
- `minMatch` — minimum match length in bytes. Min: `3`. Default: `3`.
- `maxMatch` — maximum match length in bytes. Max: `255`. Default: `255`.

Invalid combinations (e.g. `maxMatch < minMatch`) throw `IllegalArgumentException` at construction time.

---

#### `api/CompressionResult.java`

An immutable Java `record` returned by `LzCompressor.compress()`:

- `data` — the encoded byte array.
- `originalSize` — byte count before encoding.
- `compressedSize` — byte count after encoding.
- `method` — the `CompressionMethod` that was used.

---

#### `container/ContainerFormat.java`

A constants-only utility class:

- `MAGIC = 0x43564531` — the `"CVE1"` file signature.
- `VERSION = 1` — current container version.
- `HEADER_SIZE = 32` — container header byte count.
- `BLOCK_HEADER_SIZE = 24` — per-block header byte count.

---

#### `container/ContainerWriter.java`

Wraps an `OutputStream` in a `DataOutputStream` (buffered) and provides:

- `writeHeader(originalSize, blockSize, blockCount)` — serializes the 32-byte container header.
- `writeBlock(blockId, originalSize, compressed, method, original)` — computes CRC32 of the original bytes, then serializes the 24-byte block header followed by the compressed payload.

---

#### `container/ContainerReader.java`

Wraps an `InputStream` in a `DataInputStream` (buffered) and provides:

- `readHeader()` → `Header` record — validates magic signature and version, then reads all header fields.
- `readBlock()` → `Block` record — reads a block header and reads exactly `compressedSize` payload bytes.
- `static verifyChecksum(byte[] original, long expected)` — recomputes CRC32 and throws `IOException` on mismatch.

---

#### `core/CompressionAlgorithm.java`

A Java `interface` defining the contract for any compression algorithm:

```java
CompressionResult compress(byte[] input);
byte[] decompress(byte[] input, int expectedSize);
```

`LzCompressor` implements this interface. Future algorithms (Huffman, LZMA, etc.) would also implement it, making the engine pluggable.

---

#### `core/LzCompressor.java`

The full LZ sliding-window compress/decompress implementation. See [Section 3C](#c-lz-sliding-window-algorithm) for the detailed algorithm walkthrough.

---

#### `cli/Main.java`

The command-line entry point. Dispatches on the first argument:

- `server [port]` — starts the built-in HTTP web server (default port: `8080`).
- `compress <input> <output.cve>` — compresses a file using default options.
- `decompress <input.cve> <output>` — decompresses a container.
- `inspect <input.cve>` — prints the container header and per-block metadata to stdout.

---

#### `web/WebServer.java`

A zero-dependency HTTP server using Java's built-in `com.sun.net.httpserver.HttpServer`. Exposes:

| Method | Endpoint             | Description                                                                             |
| ------ | -------------------- | --------------------------------------------------------------------------------------- |
| `GET`  | `/api/status`        | Returns engine version, Java version, container spec, default options                   |
| `POST` | `/api/compress`      | Accepts raw binary body, returns compressed data + analytics as JSON or binary download |
| `POST` | `/api/decompress`    | Accepts a `.cve` container body, returns decompressed data + block stats                |
| `POST` | `/api/inspect`       | Accepts a `.cve` body, returns full header + block breakdown without decompressing      |
| `GET`  | `/api/samples?type=` | Returns a pre-built sample file (`repetitive`, `json`, `code`, `random`)                |
| `GET`  | `/*`                 | Serves static files from the `web/` directory                                           |

Supports configurable `X-Block-Size`, `X-Window-Size`, `X-Min-Match`, `X-Max-Match` HTTP request headers for per-request option tuning. Uses Java 21 virtual threads (`Executors.newVirtualThreadPerTaskExecutor()`) for concurrency.

---

#### `CompressionEngineTest.java`

Two JUnit 5 round-trip tests:

- `roundTripRepetitiveData()` — compresses a highly repetitive ~450 KB string and verifies the restored output is byte-for-byte identical **and** that the compressed file is strictly smaller than the input.
- `roundTripRandomData()` — compresses 100,000 random bytes (which will STORE-fallback) and verifies the restored output is byte-for-byte identical.

---

### Web Frontend

#### `web/index.html`

The single-page application shell. Contains five tab panels:

1. **Compress File** — drag-and-drop upload, engine option controls (block size, window size, min/max match), compression analytics display (size, ratio, savings %, time), per-block CRC32 table, and `.cve` download button.
2. **Decompress File** — drag-and-drop `.cve` upload, per-block CRC32 verification table, restored text preview, and download button.
3. **Inspect Container** — reads and displays the container header and all block headers without modifying the file.
4. **Algorithm Sandbox** — real-time interactive visualizer: type or paste text to see the LZ engine segment it into flag bytes + literal/match token groups instantly.
5. **Architecture & CLI** — full documentation panel with copyable CLI commands.

---

#### `web/styles.css`

A complete design system using CSS custom properties, Google Fonts (Plus Jakarta Sans, JetBrains Mono), glassmorphism card effects, animated status indicators, micro-animation transitions, dark theme, responsive grid layouts, and a custom table component. No CSS frameworks used.

---

#### `web/app.js`

All frontend JavaScript in a single `DOMContentLoaded` handler. Responsibilities:

- **Backend status probe** — `GET /api/status` on load. Sets status pill to "Java SE Backend Online" or falls back gracefully to "In-Browser Engine".
- **Tab navigation** — activates/deactivates tab panels.
- **Drag-and-drop file handling** — for compress, decompress, and inspect panels.
- **Sample file loader** — fetches from `/api/samples` or generates locally (fallback).
- **Compress action** — sends file bytes to `/api/compress`, renders metrics + block table + storage bar, stores blob for download.
- **Decompress action** — sends `.cve` bytes to `/api/decompress`, renders verification table + text preview, stores blob for download.
- **Inspect action** — sends `.cve` bytes to `/api/inspect`, renders the full header and block descriptor table.
- **Sandbox visualizer** — encodes the textarea content live using the client-side JS engine and renders each token group with color-coded literal vs match chips.
- **Copy-to-clipboard** — for all CLI command code blocks.
- **Toast notifications** — slide-in success/error notifications.

---

#### `web/cve-engine.js`

A **complete client-side JavaScript port** of the Java compression engine. Three classes:

- **`Crc32`** — builds the IEEE 802.3 CRC32 lookup table once and provides a `compute(Uint8Array)` static method.
- **`LzCompressorJs`** — full JavaScript port of `LzCompressor.java`: `compress(Uint8Array)` and `decompress(Uint8Array, expectedSize)` with identical algorithm semantics including overlapping match support.
- **`CveContainerJs`** — JavaScript port of the container layer:
  - `compressFile(inputBytes, options)` — builds a full CVE1 binary container in a `Uint8Array`.
  - `decompressFile(containerBytes, options)` — parses and decompresses a CVE1 container, verifying CRC32 per block.
  - `inspectContainer(containerBytes)` — reads header and block descriptors without decompressing data.

This enables the web dashboard to work **completely offline** without any backend. When the Java server is not running, all operations are handled in-browser.

---

### Launch Scripts

#### `start-web.bat`

Windows Command Prompt one-click launcher:

1. Creates `java/target/classes` directory if it doesn't exist.
2. Compiles all Java sources in `java/src/main`.
3. Opens `http://localhost:8080` in the default browser.
4. Starts the Java web server.

#### `start-web.ps1`

PowerShell equivalent of `start-web.bat` with colored output (Cyan/Green/Yellow).

---

## 5. How to Run

### Prerequisites

- **Java 21 or later** (Java 25 is confirmed to work)
- **Maven 3.x** (only needed for `mvn test`)
- A modern web browser (Chrome, Firefox, Edge)

Verify your Java installation:

```powershell
java -version
```

---

### Option A: Web Dashboard (Recommended)

**Step 1**: Open a terminal in the project root (`compression-engine-v0.1/`).

**Step 2**: Launch the server:

```powershell
# PowerShell
.\start-web.ps1

# OR Command Prompt
start-web.bat

# OR directly
java -cp java/target/classes com.compression.cli.Main server 8080
```

**Step 3**: Open **http://localhost:8080** in your browser.

The dashboard will automatically detect whether the Java backend is running and show it in the status indicator. If the backend is not running, it silently falls back to the in-browser JavaScript engine.

---

### Option B: Command Line Interface (CLI)

**Step 1** — Compile sources (first time or after code changes):

```powershell
javac -d java/target/classes (Get-ChildItem -Recurse -Filter *.java java/src/main).FullName
```

**Step 2** — Compress a file:

```powershell
java -cp java/target/classes com.compression.cli.Main compress input.txt output.cve
```

**Step 3** — Decompress a container:

```powershell
java -cp java/target/classes com.compression.cli.Main decompress output.cve restored.txt
```

**Step 4** — Inspect a container's structure (no decompression):

```powershell
java -cp java/target/classes com.compression.cli.Main inspect output.cve
```

**Example inspect output:**

```
Version: 1
Original size: 4718592
Block size: 1048576
Block count: 5
Block 0: method=LZ  original=1048576 compressed=312044
Block 1: method=LZ  original=1048576 compressed=309881
Block 2: method=LZ  original=1048576 compressed=315002
Block 3: method=LZ  original=1048576 compressed=318774
Block 4: method=STORE original=524288 compressed=524288
```

---

### Option C: Running Tests with Maven

```bash
cd java
mvn test
```

This runs both JUnit 5 round-trip tests with temporary files created and deleted automatically.

---

## 6. Capabilities

| Capability                       | Detail                                                                       |
| -------------------------------- | ---------------------------------------------------------------------------- |
| ✅ **Compress any file type**    | Text, JSON, logs, source code, binary data                                   |
| ✅ **Lossless compression**      | Byte-for-byte identical restoration guaranteed                               |
| ✅ **Per-block CRC32 integrity** | Every block checksum is recalculated on decompression                        |
| ✅ **Automatic STORE fallback**  | High-entropy blocks are never expanded                                       |
| ✅ **Streaming / large files**   | Processes input one block at a time, constant memory overhead                |
| ✅ **Configurable parameters**   | Block size, window size, min/max match all adjustable                        |
| ✅ **Forward versioning**        | Container version field allows future format changes                         |
| ✅ **Dual-engine web UI**        | Java REST API backend + JavaScript browser fallback                          |
| ✅ **No external dependencies**  | Only Java standard library (`java.util.zip.CRC32`, `com.sun.net.httpserver`) |
| ✅ **Interactive LZ visualizer** | Real-time token-stream visualization in the sandbox tab                      |
| ✅ **Works fully offline**       | JavaScript engine handles all operations client-side                         |
| ✅ **Overlapping match support** | Efficient run-length-style repetition encoding (e.g. `AAAAA…`)               |

**Best compression results on:**

- Plain text files with long repeated phrases (logs, templates, config dumps)
- Source code files (high structural repetition)
- JSON data with repeated field names and values
- CSV and TSV files with repeated column patterns

---

## 7. Limitations

Understanding what this engine **does not yet do** is as important as knowing what it does. This is v0.1 — a foundation, not a finished product.

### Algorithmic Limitations

| Limitation                                      | Why It Matters                                                                                                                                                        | Future Fix                                                                                     |
| ----------------------------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ---------------------------------------------------------------------------------------------- |
| **Naïve O(n × w) match search**                 | For each byte, the encoder scans up to `windowSize` bytes backward linearly. This is slow for large windows or large files.                                           | Replace with hash chains (like DEFLATE) or suffix arrays for O(1) average match lookup.        |
| **No entropy coding (Huffman / ANS)**           | After LZ tokenization, literal bytes and match lengths are stored as-is. A Huffman or arithmetic coder applied on top would yield much better ratios on typical text. | Add a second-pass entropy coder (Huffman, Asymmetric Numeral Systems).                         |
| **Fixed 2-byte distance + 1-byte length token** | Every match token costs exactly 3 bytes regardless of distance/length magnitude. Shorter distances could be encoded in 1 byte; longer lengths in 2.                   | Implement variable-length token encoding.                                                      |
| **Single-threaded compression**                 | Blocks are processed sequentially.                                                                                                                                    | Parallel block compression using Java virtual threads (already available).                     |
| **No adaptive strategy selection**              | The engine always tries LZ first and only falls back to STORE. It does not detect early that data is incompressible to skip LZ entirely.                              | Add a quick entropy estimator (byte frequency sampling) to skip LZ on random/encrypted blocks. |
| **No match-length run statistics**              | The encoder does not track which match lengths are most common to bias encoding decisions.                                                                            | Feed into a future entropy coder for better symbol probability modeling.                       |

### Format Limitations

| Limitation                                 | Detail                                                                                                                                                            |
| ------------------------------------------ | ----------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **No random block access**                 | Although block IDs are stored, there is no block index or offset table at the start of the file. Seeking to block N requires reading all preceding block headers. |
| **No multi-file archive support**          | The format encodes exactly one source file. No directory structure, file metadata, or multiple-stream support.                                                    |
| **No streaming decompression API**         | Decompression is block-oriented and writes to a `Path`. A streaming `InputStream` wrapper does not yet exist.                                                     |
| **No encryption or compression combining** | No authenticated encryption, no password protection.                                                                                                              |
| **No split archive support**               | Containers cannot be split across multiple volumes.                                                                                                               |

### Operational Limitations

| Limitation                       | Detail                                                                                                              |
| -------------------------------- | ------------------------------------------------------------------------------------------------------------------- |
| **No Maven installed**           | `mvn test` requires Maven in PATH. Tests can be compiled and run manually with `javac` if Maven is unavailable.     |
| **No CLI progress indicator**    | Compressing a large file prints nothing until completion.                                                           |
| **Web server is HTTP only**      | The built-in server does not support HTTPS/TLS. Not suitable for public internet exposure.                          |
| **No persistent upload history** | The web dashboard does not store previous compression results between page reloads.                                 |
| **Windows path separator**       | The PowerShell compile command uses `Get-ChildItem`. On Linux/Mac, use `find java/src/main -name "*.java"` instead. |

### Performance Expectations

| Input Type                          | Expected Ratio | Notes                             |
| ----------------------------------- | -------------- | --------------------------------- |
| Highly repetitive text (logs)       | 5–20x          | Very high savings with LZ         |
| Average English prose / source code | 1.5–4x         | Moderate savings                  |
| Already compressed (ZIP, JPEG, MP4) | ~1.0x          | STORE fallback, near-zero savings |
| Random/encrypted binary data        | ~1.0x          | STORE fallback, no savings        |

These ratios will improve significantly once entropy coding is added in a future version.

---

## 8. Roadmap — What Comes Next

| Version  | Planned Feature                                                                           |
| -------- | ----------------------------------------------------------------------------------------- |
| **v0.2** | Hash-chain match finder — O(1) average lookup replacing O(n) linear scan                  |
| **v0.3** | Huffman entropy coding as a second pass over LZ tokens                                    |
| **v0.4** | Block index table for O(1) random block access in the container                           |
| **v0.5** | Parallel block compression using Java virtual threads                                     |
| **v0.6** | Adaptive strategy selection (entropy-based pre-scan)                                      |
| **v0.7** | Fuzzing harness + property-based round-trip testing                                       |
| **v0.8** | Benchmarking suite vs gzip, LZ4, Zstandard                                                |
| **v1.0** | Production-quality release with documented API, stable format, and performance guarantees |

---

## 9. Pushing to GitHub

Follow these steps to push the project to a new or existing GitHub repository.

### Step 1 — Initialize Git (if not already done)

```powershell
cd C:\Users\Admin\Downloads\compression-engine-v0.1
git init
```

### Step 2 — Create a `.gitignore`

```powershell
@"
# Compiled Java bytecode
java/target/

# OS artifacts
.DS_Store
Thumbs.db

# IDE files
.idea/
*.iml
.vscode/

# Temp files created during compression tests
*.cve
"@ | Out-File -Encoding utf8 .gitignore
```

### Step 3 — Stage all files

```powershell
git add .
git status
```

### Step 4 — First commit

```powershell
git commit -m "feat: compression engine v0.1 — CVE1 container, LZ sliding window, CRC32, web dashboard"
```

### Step 5 — Add GitHub remote and push

```powershell
# Replace with your actual repository URL
git remote add origin https://github.com/YOUR_USERNAME/compression-engine.git
git branch -M main
git push -u origin main
```

### Step 6 — Verify on GitHub

Visit your repository URL. You should see the full folder structure:

```
java/         ← Java Engine
web/          ← Web Frontend
README.md     ← This file
start-web.bat
start-web.ps1
```

> **Tip**: Set a GitHub repository description like: _"From-scratch Java 21 compression engine: CVE1 binary container, LZ sliding window encoding, CRC32 integrity, interactive web dashboard."_

---

_Compression Engine v0.1 · Built from first principles · No external runtime dependencies_
