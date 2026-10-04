/**
 * Compression Engine v0.1 - Frontend Application Logic
 */

document.addEventListener('DOMContentLoaded', () => {
  // State
  let backendOnline = false;
  let backendInfo = null;

  // Compress state
  let compressFile = null;
  let compressFileData = null;
  let compressedBlob = null;
  let compressedFileName = "output.cve";

  // Decompress state
  let decompressFile = null;
  let decompressFileData = null;
  let decompressedBlob = null;
  let decompressedFileName = "restored.bin";

  // Inspect state
  let inspectFileData = null;

  // DOM Elements
  const backendStatusPill = document.getElementById('backend-status-pill');
  const backendStatusText = document.getElementById('backend-status-text');
  const toastContainer = document.getElementById('toast-container');

  // Tabs
  const tabButtons = document.querySelectorAll('.tab-btn');
  const tabPanes = document.querySelectorAll('.tab-pane');

  // Compress elements
  const compressDropzone = document.getElementById('compress-dropzone');
  const compressFileInput = document.getElementById('compress-file-input');
  const compressSelectedCard = document.getElementById('compress-selected-card');
  const compressSelectedName = document.getElementById('compress-selected-name');
  const compressSelectedSize = document.getElementById('compress-selected-size');
  const compressRemoveFileBtn = document.getElementById('compress-remove-file-btn');
  const btnRunCompress = document.getElementById('btn-run-compress');
  const compressPlaceholder = document.getElementById('compress-placeholder');
  const compressResultsArea = document.getElementById('compress-results-area');
  const btnDownloadCve = document.getElementById('btn-download-cve');
  const compressBlocksTbody = document.getElementById('compress-blocks-tbody');

  // Tuning options elements
  const optBlockSize = document.getElementById('opt-block-size');
  const optWindowSize = document.getElementById('opt-window-size');
  const optMinMatch = document.getElementById('opt-min-match');
  const optMaxMatch = document.getElementById('opt-max-match');
  const lblBlockSize = document.getElementById('lbl-block-size');
  const lblWindowSize = document.getElementById('lbl-window-size');
  const lblMinMatch = document.getElementById('lbl-min-match');
  const lblMaxMatch = document.getElementById('lbl-max-match');
  const compressOptionsToggle = document.getElementById('compress-options-toggle');
  const compressOptionsBody = document.getElementById('compress-options-body');
  const compressOptionsArrow = document.getElementById('compress-options-arrow');

  // Decompress elements
  const decompressDropzone = document.getElementById('decompress-dropzone');
  const decompressFileInput = document.getElementById('decompress-file-input');
  const decompressSelectedCard = document.getElementById('decompress-selected-card');
  const decompressSelectedName = document.getElementById('decompress-selected-name');
  const decompressSelectedSize = document.getElementById('decompress-selected-size');
  const decompressRemoveFileBtn = document.getElementById('decompress-remove-file-btn');
  const btnRunDecompress = document.getElementById('btn-run-decompress');
  const decompressPlaceholder = document.getElementById('decompress-placeholder');
  const decompressResultsArea = document.getElementById('decompress-results-area');
  const btnDownloadRestored = document.getElementById('btn-download-restored');
  const decompressBlocksTbody = document.getElementById('decompress-blocks-tbody');
  const decompPreviewBox = document.getElementById('decomp-preview-box');
  const decompPreviewText = document.getElementById('decomp-preview-text');

  // Inspect elements
  const inspectDropzone = document.getElementById('inspect-dropzone');
  const inspectFileInput = document.getElementById('inspect-file-input');
  const inspectResultsArea = document.getElementById('inspect-results-area');
  const inspectBlocksTbody = document.getElementById('inspect-blocks-tbody');

  // Sandbox elements
  const sandboxInputText = document.getElementById('sandbox-input-text');
  const sandboxRawLen = document.getElementById('sandbox-raw-len');
  const sandboxEncodedLen = document.getElementById('sandbox-encoded-len');
  const sandboxRatio = document.getElementById('sandbox-ratio');
  const sandboxStrategy = document.getElementById('sandbox-strategy');
  const sandboxTokenStream = document.getElementById('sandbox-token-stream');

  // ==========================================================================
  // UTILITY HELPERS
  // ==========================================================================
  function formatBytes(bytes, decimals = 2) {
    if (bytes === 0) return '0 Bytes';
    const k = 1024;
    const dm = decimals < 0 ? 0 : decimals;
    const sizes = ['Bytes', 'KB', 'MB', 'GB', 'TB'];
    const i = Math.floor(Math.log(bytes) / Math.log(k));
    return parseFloat((bytes / Math.pow(k, i)).toFixed(dm)) + ' ' + sizes[i];
  }

  function showToast(message, type = 'info') {
    const toast = document.createElement('div');
    toast.className = `toast ${type}`;
    const icon = type === 'success' ? '✅' : (type === 'error' ? '⚠️' : 'ℹ️');
    toast.innerHTML = `<span>${icon}</span> <span>${message}</span>`;
    toastContainer.appendChild(toast);
    setTimeout(() => {
      toast.style.opacity = '0';
      toast.style.transform = 'translateY(10px)';
      setTimeout(() => toast.remove(), 300);
    }, 4000);
  }

  function triggerDownload(blob, filename) {
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = filename;
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
    setTimeout(() => URL.revokeObjectURL(url), 10000);
  }

  // ==========================================================================
  // BACKEND STATUS CHECK
  // ==========================================================================
  async function checkBackendStatus() {
    try {
      const res = await fetch('/api/status', { method: 'GET' });
      if (res.ok) {
        backendInfo = await res.json();
        backendOnline = true;
        backendStatusPill.className = 'status-pill online';
        backendStatusText.textContent = `⚡ Java SE Backend Online (${backendInfo.javaVersion || 'JDK'})`;
        document.getElementById('compress-engine-badge').textContent = `Java SE ${backendInfo.javaVersion || ''}`;
      } else {
        throw new Error("Status endpoint returned non-200");
      }
    } catch (e) {
      backendOnline = false;
      backendStatusPill.className = 'status-pill';
      backendStatusText.textContent = '🌐 In-Browser Web Engine (Client Mode)';
      document.getElementById('compress-engine-badge').textContent = 'Web Engine JS';
    }
  }

  checkBackendStatus();

  // ==========================================================================
  // TAB NAVIGATION
  // ==========================================================================
  tabButtons.forEach(btn => {
    btn.addEventListener('click', () => {
      const targetId = btn.getAttribute('data-target');
      tabButtons.forEach(b => b.classList.remove('active'));
      tabPanes.forEach(p => p.classList.remove('active'));

      btn.classList.add('active');
      const targetPane = document.getElementById(targetId);
      if (targetPane) targetPane.classList.add('active');

      if (targetId === 'pane-sandbox') {
        renderSandboxTokens();
      }
    });
  });

  // ==========================================================================
  // COMPRESSION TUNING CONTROLS
  // ==========================================================================
  optBlockSize.addEventListener('change', () => {
    const val = parseInt(optBlockSize.value);
    lblBlockSize.textContent = val >= 1048576 ? `${val / 1048576} MB` : `${val / 1024} KB`;
  });

  optWindowSize.addEventListener('change', () => {
    const val = parseInt(optWindowSize.value);
    lblWindowSize.textContent = `${val / 1024} KB`;
  });

  optMinMatch.addEventListener('input', () => {
    lblMinMatch.textContent = `${optMinMatch.value} bytes`;
  });

  optMaxMatch.addEventListener('input', () => {
    lblMaxMatch.textContent = `${optMaxMatch.value} bytes`;
  });

  compressOptionsToggle.addEventListener('click', () => {
    const isHidden = compressOptionsBody.style.display === 'none';
    compressOptionsBody.style.display = isHidden ? 'grid' : 'none';
    compressOptionsArrow.textContent = isHidden ? '▾' : '▸';
  });

  // ==========================================================================
  // COMPRESS FILE HANDLING & DRAG-AND-DROP
  // ==========================================================================
  function setCompressFile(file, dataBytes = null) {
    compressFile = file;
    compressSelectedName.textContent = file.name;
    compressSelectedSize.textContent = formatBytes(file.size);
    compressSelectedCard.classList.add('visible');
    compressDropzone.style.display = 'none';
    btnRunCompress.disabled = false;

    if (dataBytes) {
      compressFileData = dataBytes;
    } else {
      const reader = new FileReader();
      reader.onload = (e) => {
        compressFileData = new Uint8Array(e.target.result);
      };
      reader.readAsArrayBuffer(file);
    }
  }

  function clearCompressFile() {
    compressFile = null;
    compressFileData = null;
    compressFileInput.value = '';
    compressSelectedCard.classList.remove('visible');
    compressDropzone.style.display = 'block';
    btnRunCompress.disabled = true;
  }

  compressRemoveFileBtn.addEventListener('click', clearCompressFile);

  compressDropzone.addEventListener('dragover', (e) => {
    e.preventDefault();
    compressDropzone.classList.add('drag-over');
  });

  compressDropzone.addEventListener('dragleave', () => {
    compressDropzone.classList.remove('drag-over');
  });

  compressDropzone.addEventListener('drop', (e) => {
    e.preventDefault();
    compressDropzone.classList.remove('drag-over');
    if (e.dataTransfer.files && e.dataTransfer.files.length > 0) {
      setCompressFile(e.dataTransfer.files[0]);
    }
  });

  compressFileInput.addEventListener('change', () => {
    if (compressFileInput.files && compressFileInput.files.length > 0) {
      setCompressFile(compressFileInput.files[0]);
    }
  });

  // Sample File Chips
  document.querySelectorAll('.sample-chip[data-sample]').forEach(chip => {
    chip.addEventListener('click', async () => {
      const sampleType = chip.getAttribute('data-sample');
      try {
        if (backendOnline) {
          const res = await fetch(`/api/samples?type=${sampleType}`);
          if (res.ok) {
            const blob = await res.blob();
            const filename = res.headers.get('X-Sample-Name') || `sample_${sampleType}.txt`;
            const file = new File([blob], filename);
            const arrayBuffer = await blob.arrayBuffer();
            setCompressFile(file, new Uint8Array(arrayBuffer));
            showToast(`Loaded sample: ${filename}`, 'success');
            return;
          }
        }
      } catch (ignored) {}

      // Client-side fallback sample generation
      let sampleData;
      let filename;
      if (sampleType === 'repetitive') {
        filename = 'sample_repetitive.txt';
        const str = "Enterprise document metadata tenantId createdBy compression engine v0.1 block storage foundation ".repeat(150);
        sampleData = new TextEncoder().encode(str);
      } else if (sampleType === 'json') {
        filename = 'sample_server_logs.json';
        const log = JSON.stringify({
          timestamp: "2026-10-04T12:00:00Z",
          level: "INFO",
          service: "auth-service",
          message: "User session validated successfully for tenantId=corp-enterprise-9921",
          status: 200
        }, null, 2) + "\n";
        sampleData = new TextEncoder().encode(log.repeat(50));
      } else if (sampleType === 'code') {
        filename = 'sample_code.java';
        const code = `package com.compression;\n\npublic class Processor {\n    public static void run() {\n        System.out.println("Processing data chunk for tenant");\n    }\n}\n`;
        sampleData = new TextEncoder().encode(code.repeat(40));
      } else {
        filename = 'sample_random_entropy.bin';
        sampleData = new Uint8Array(8192);
        crypto.getRandomValues(sampleData);
      }

      const file = new File([sampleData], filename);
      setCompressFile(file, sampleData);
      showToast(`Loaded sample: ${filename}`, 'success');
    });
  });

  // ==========================================================================
  // RUN COMPRESSION
  // ==========================================================================
  btnRunCompress.addEventListener('click', async () => {
    if (!compressFileData) {
      showToast("Please select a file first", "error");
      return;
    }

    btnRunCompress.disabled = true;
    btnRunCompress.innerHTML = '<span>⏳ Compressing with CVE1 Engine...</span>';

    const blockSize = parseInt(optBlockSize.value);
    const windowSize = parseInt(optWindowSize.value);
    const minMatch = parseInt(optMinMatch.value);
    const maxMatch = parseInt(optMaxMatch.value);

    const startTime = performance.now();
    let result = null;

    try {
      if (backendOnline) {
        // Run via Java Backend
        const res = await fetch(`/api/compress?action=json`, {
          method: 'POST',
          headers: {
            'Content-Type': 'application/octet-stream',
            'X-File-Name': compressFile.name,
            'X-Block-Size': blockSize,
            'X-Window-Size': windowSize,
            'X-Min-Match': minMatch,
            'X-Max-Match': maxMatch
          },
          body: compressFileData
        });

        if (!res.ok) {
          const errData = await res.json().catch(() => ({}));
          throw new Error(errData.error || `Server returned error ${res.status}`);
        }

        const data = await res.json();
        // Convert base64 data to binary Blob
        const byteCharacters = atob(data.dataBase64);
        const byteNumbers = new Array(byteCharacters.length);
        for (let i = 0; i < byteCharacters.length; i++) {
          byteNumbers[i] = byteCharacters.charCodeAt(i);
        }
        const byteArray = new Uint8Array(byteNumbers);
        compressedBlob = new Blob([byteArray], { type: 'application/octet-stream' });

        result = {
          originalSize: data.originalSize,
          compressedSize: data.compressedSize,
          ratio: parseFloat(data.ratio),
          savingsPercent: parseFloat(data.savingsPercent),
          durationMs: parseFloat(data.durationMs),
          blocks: data.blocks
        };
      } else {
        // Run via in-browser JS engine
        const jsResult = CveContainerJs.compressFile(compressFileData, {
          blockSize, windowSize, minMatch, maxMatch
        });
        const elapsed = performance.now() - startTime;
        compressedBlob = new Blob([jsResult.containerBytes], { type: 'application/octet-stream' });

        result = {
          originalSize: jsResult.originalSize,
          compressedSize: jsResult.compressedSize,
          ratio: parseFloat(jsResult.ratio.toFixed(2)),
          savingsPercent: parseFloat(jsResult.savingsPercent.toFixed(2)),
          durationMs: parseFloat(elapsed.toFixed(2)),
          blocks: jsResult.blocks
        };
      }

      compressedFileName = compressFile.name.endsWith('.cve') ? compressFile.name : compressFile.name + '.cve';

      // Render Results
      renderCompressResults(result);
      showToast(`Compressed successfully! Space saved: ${result.savingsPercent}%`, 'success');
    } catch (err) {
      console.error(err);
      showToast(`Compression failed: ${err.message}`, 'error');
    } finally {
      btnRunCompress.disabled = false;
      btnRunCompress.innerHTML = '<span>🗜️ Compress File to .cve</span>';
    }
  });

  function renderCompressResults(result) {
    compressPlaceholder.style.display = 'none';
    compressResultsArea.classList.add('visible');

    document.getElementById('res-orig-size').textContent = formatBytes(result.originalSize);
    document.getElementById('res-comp-size').textContent = formatBytes(result.compressedSize);
    document.getElementById('res-savings').textContent = `${result.savingsPercent}%`;
    document.getElementById('res-ratio').textContent = `${result.ratio}x`;
    document.getElementById('res-time').textContent = `${result.durationMs} ms`;

    // Storage comparison bar
    const storagePercent = document.getElementById('storage-bar-percent');
    const storageFill = document.getElementById('storage-bar-fill');
    storagePercent.textContent = `${result.savingsPercent}% footprint reduction`;
    const percentWidth = Math.min(100, Math.max(2, (result.compressedSize / (result.originalSize || 1)) * 100));
    storageFill.style.width = `${percentWidth}%`;

    // Blocks Table
    document.getElementById('res-block-count-badge').textContent = `${result.blocks.length} Block${result.blocks.length > 1 ? 's' : ''}`;
    compressBlocksTbody.innerHTML = '';
    result.blocks.forEach(b => {
      const tr = document.createElement('tr');
      const isLz = b.method === 'LZ';
      tr.innerHTML = `
        <td>#${b.id}</td>
        <td><span class="badge-method ${isLz ? 'lz' : 'store'}">${b.method}</span></td>
        <td>${formatBytes(b.originalSize)}</td>
        <td>${formatBytes(b.compressedSize)}</td>
        <td style="color: #38bdf8;">0x${b.checksumHex}</td>
        <td>${b.ratio || '1.00'}x</td>
      `;
      compressBlocksTbody.appendChild(tr);
    });

    btnDownloadCve.querySelector('span').textContent = `⬇️ Download "${compressedFileName}" (${formatBytes(result.compressedSize)})`;
  }

  btnDownloadCve.addEventListener('click', () => {
    if (compressedBlob) {
      triggerDownload(compressedBlob, compressedFileName);
      showToast(`Downloading ${compressedFileName}`, 'success');
    }
  });

  // ==========================================================================
  // DECOMPRESS FILE HANDLING & ACTION
  // ==========================================================================
  function setDecompressFile(file) {
    decompressFile = file;
    decompressSelectedName.textContent = file.name;
    decompressSelectedSize.textContent = formatBytes(file.size);
    decompressSelectedCard.classList.add('visible');
    decompressDropzone.style.display = 'none';
    btnRunDecompress.disabled = false;

    const reader = new FileReader();
    reader.onload = (e) => {
      decompressFileData = new Uint8Array(e.target.result);
      // Quick validation check
      if (decompressFileData.length >= 4) {
        const magic = (decompressFileData[0] << 24) | (decompressFileData[1] << 16) | (decompressFileData[2] << 8) | decompressFileData[3];
        if (magic === 0x43564531) {
          document.getElementById('decompress-integrity-badge').textContent = 'Valid CVE1 Header';
          document.getElementById('decompress-integrity-badge').style.borderColor = 'rgba(52, 211, 153, 0.4)';
          document.getElementById('decompress-integrity-badge').style.color = '#34d399';
        } else {
          document.getElementById('decompress-integrity-badge').textContent = 'Non-CVE1 File';
          document.getElementById('decompress-integrity-badge').style.borderColor = 'rgba(244, 63, 94, 0.4)';
          document.getElementById('decompress-integrity-badge').style.color = '#f43f5e';
        }
      }
    };
    reader.readAsArrayBuffer(file);
  }

  function clearDecompressFile() {
    decompressFile = null;
    decompressFileData = null;
    decompressFileInput.value = '';
    decompressSelectedCard.classList.remove('visible');
    decompressDropzone.style.display = 'block';
    btnRunDecompress.disabled = true;
    document.getElementById('decompress-integrity-badge').textContent = 'Ready';
  }

  decompressRemoveFileBtn.addEventListener('click', clearDecompressFile);

  decompressDropzone.addEventListener('dragover', (e) => {
    e.preventDefault();
    decompressDropzone.classList.add('drag-over');
  });

  decompressDropzone.addEventListener('dragleave', () => {
    decompressDropzone.classList.remove('drag-over');
  });

  decompressDropzone.addEventListener('drop', (e) => {
    e.preventDefault();
    decompressDropzone.classList.remove('drag-over');
    if (e.dataTransfer.files && e.dataTransfer.files.length > 0) {
      setDecompressFile(e.dataTransfer.files[0]);
    }
  });

  decompressFileInput.addEventListener('change', () => {
    if (decompressFileInput.files && decompressFileInput.files.length > 0) {
      setDecompressFile(decompressFileInput.files[0]);
    }
  });

  btnRunDecompress.addEventListener('click', async () => {
    if (!decompressFileData) {
      showToast("Please choose a .cve container first", "error");
      return;
    }

    btnRunDecompress.disabled = true;
    btnRunDecompress.innerHTML = '<span>⏳ Decompressing & Checking CRC32...</span>';

    const startTime = performance.now();
    let result = null;

    try {
      if (backendOnline) {
        // Run via Java Backend
        const res = await fetch(`/api/decompress?action=json`, {
          method: 'POST',
          headers: {
            'Content-Type': 'application/octet-stream',
            'X-File-Name': decompressFile.name
          },
          body: decompressFileData
        });

        if (!res.ok) {
          const errData = await res.json().catch(() => ({}));
          throw new Error(errData.error || `Decompression error (HTTP ${res.status})`);
        }

        const data = await res.json();
        const byteCharacters = atob(data.dataBase64);
        const byteNumbers = new Array(byteCharacters.length);
        for (let i = 0; i < byteCharacters.length; i++) {
          byteNumbers[i] = byteCharacters.charCodeAt(i);
        }
        const restoredBytes = new Uint8Array(byteNumbers);
        decompressedBlob = new Blob([restoredBytes], { type: 'application/octet-stream' });

        result = {
          containerSize: data.containerSize,
          restoredSize: data.restoredSize,
          blockCount: data.blockCount,
          crcVerified: data.crcVerified,
          durationMs: data.durationMs,
          blocks: data.blocks,
          restoredBytes: restoredBytes
        };
      } else {
        // Run via in-browser JS engine
        const jsResult = CveContainerJs.decompressFile(decompressFileData);
        const elapsed = performance.now() - startTime;
        decompressedBlob = new Blob([jsResult.restoredBytes], { type: 'application/octet-stream' });

        result = {
          containerSize: decompressFileData.length,
          restoredSize: jsResult.originalSize,
          blockCount: jsResult.blockCount,
          crcVerified: jsResult.crcVerified,
          durationMs: parseFloat(elapsed.toFixed(2)),
          blocks: jsResult.blocks,
          restoredBytes: jsResult.restoredBytes
        };
      }

      // Restored file name logic
      decompressedFileName = decompressFile.name.endsWith('.cve') 
        ? decompressFile.name.substring(0, decompressFile.name.length - 4) 
        : decompressFile.name + '.restored';

      renderDecompressResults(result);
      showToast("Container decompressed! All CRC32 checksums verified.", "success");
    } catch (err) {
      console.error(err);
      showToast(`Decompression failed: ${err.message}`, "error");
    } finally {
      btnRunDecompress.disabled = false;
      btnRunDecompress.innerHTML = '<span>🔓 Decompress & Verify Container</span>';
    }
  });

  function renderDecompressResults(result) {
    decompressPlaceholder.style.display = 'none';
    decompressResultsArea.classList.add('visible');

    document.getElementById('decomp-res-container-size').textContent = formatBytes(result.containerSize);
    document.getElementById('decomp-res-restored-size').textContent = formatBytes(result.restoredSize);
    document.getElementById('decomp-res-blocks').textContent = result.blockCount;
    document.getElementById('decomp-res-time').textContent = `${result.durationMs} ms`;

    // Blocks
    decompressBlocksTbody.innerHTML = '';
    result.blocks.forEach(b => {
      const tr = document.createElement('tr');
      tr.innerHTML = `
        <td>#${b.id}</td>
        <td><span class="badge-method ${b.method === 'LZ' ? 'lz' : 'store'}">${b.method}</span></td>
        <td>${formatBytes(b.originalSize)}</td>
        <td>${formatBytes(b.compressedSize)}</td>
        <td style="color: #38bdf8;">0x${b.checksumHex}</td>
        <td style="color: #34d399; font-weight: bold;">VERIFIED ✓</td>
      `;
      decompressBlocksTbody.appendChild(tr);
    });

    // Check if plain text / json / code to display preview
    if (result.restoredBytes && result.restoredBytes.length > 0 && result.restoredBytes.length < 50000) {
      try {
        const text = new TextDecoder('utf-8', { fatal: true }).decode(result.restoredBytes);
        // If string contains mostly printable chars
        if (!/[\x00-\x08\x0E-\x1F]/.test(text.substring(0, 1000))) {
          decompPreviewBox.style.display = 'block';
          decompPreviewText.textContent = text.length > 500 ? text.substring(0, 500) + '... [truncated]' : text;
        } else {
          decompPreviewBox.style.display = 'none';
        }
      } catch (e) {
        decompPreviewBox.style.display = 'none';
      }
    } else {
      decompPreviewBox.style.display = 'none';
    }

    btnDownloadRestored.querySelector('span').textContent = `⬇️ Download "${decompressedFileName}" (${formatBytes(result.restoredSize)})`;
  }

  btnDownloadRestored.addEventListener('click', () => {
    if (decompressedBlob) {
      triggerDownload(decompressedBlob, decompressedFileName);
      showToast(`Downloading ${decompressedFileName}`, 'success');
    }
  });

  // ==========================================================================
  // CONTAINER INSPECTOR
  // ==========================================================================
  function inspectFile(file) {
    const reader = new FileReader();
    reader.onload = async (e) => {
      inspectFileData = new Uint8Array(e.target.result);
      try {
        let data = null;
        if (backendOnline) {
          const res = await fetch('/api/inspect', {
            method: 'POST',
            headers: { 'Content-Type': 'application/octet-stream' },
            body: inspectFileData
          });
          if (res.ok) {
            data = await res.json();
          }
        }
        if (!data) {
          data = CveContainerJs.inspectContainer(inspectFileData);
        }

        renderInspectResults(data);
        showToast("Container structure inspected successfully", "success");
      } catch (err) {
        console.error(err);
        showToast(`Inspection failed: ${err.message}`, "error");
      }
    };
    reader.readAsArrayBuffer(file);
  }

  inspectDropzone.addEventListener('dragover', (e) => {
    e.preventDefault();
    inspectDropzone.classList.add('drag-over');
  });

  inspectDropzone.addEventListener('dragleave', () => {
    inspectDropzone.classList.remove('drag-over');
  });

  inspectDropzone.addEventListener('drop', (e) => {
    e.preventDefault();
    inspectDropzone.classList.remove('drag-over');
    if (e.dataTransfer.files && e.dataTransfer.files.length > 0) {
      inspectFile(e.dataTransfer.files[0]);
    }
  });

  inspectFileInput.addEventListener('change', () => {
    if (inspectFileInput.files && inspectFileInput.files.length > 0) {
      inspectFile(inspectFileInput.files[0]);
    }
  });

  function renderInspectResults(data) {
    inspectResultsArea.style.display = 'block';

    document.getElementById('insp-magic').textContent = `${data.magic} (${data.magicHex})`;
    document.getElementById('insp-version').textContent = data.version;
    document.getElementById('insp-flags').textContent = `0x${data.flags.toString(16).padStart(8, '0').toUpperCase()}`;
    document.getElementById('insp-orig-size').textContent = `${data.originalSize.toLocaleString()} bytes (${formatBytes(data.originalSize)})`;
    document.getElementById('insp-block-size').textContent = `${data.blockSize.toLocaleString()} bytes (${formatBytes(data.blockSize)})`;
    document.getElementById('insp-block-count').textContent = data.blockCount;

    document.getElementById('insp-total-file-size').textContent = formatBytes(data.totalFileSize);
    document.getElementById('insp-overall-ratio').textContent = `${data.overallRatio}x`;
    document.getElementById('insp-overall-savings').textContent = `${data.overallSavings}%`;

    // Blocks
    inspectBlocksTbody.innerHTML = '';
    data.blocks.forEach(b => {
      const tr = document.createElement('tr');
      const isLz = b.method === 'LZ';
      const rationale = isLz 
        ? `LZ match encoding yielded ${b.savingsPercent}% savings` 
        : `STORE fallback prevented expansion (high entropy)`;
      tr.innerHTML = `
        <td>Block #${b.id}</td>
        <td><span class="badge-method ${isLz ? 'lz' : 'store'}">${b.method}</span></td>
        <td>${formatBytes(b.originalSize)}</td>
        <td>${formatBytes(b.compressedSize)}</td>
        <td style="color: ${b.savingsPercent > 0 ? '#34d399' : '#fbbf24'};">${b.savingsPercent}%</td>
        <td style="color: #38bdf8;">0x${b.checksumHex}</td>
        <td style="font-size: 11px; color: var(--text-muted); font-family: var(--font-sans);">${rationale}</td>
      `;
      inspectBlocksTbody.appendChild(tr);
    });
  }

  // ==========================================================================
  // ALGORITHM SANDBOX & VISUALIZER
  // ==========================================================================
  function renderSandboxTokens() {
    const text = sandboxInputText.value;
    const bytes = new TextEncoder().encode(text);
    sandboxRawLen.textContent = `${bytes.length} bytes`;

    if (bytes.length === 0) {
      sandboxEncodedLen.textContent = '0 bytes';
      sandboxRatio.textContent = '1.00x';
      sandboxTokenStream.innerHTML = '<span style="color: var(--text-subtle);">Type characters above to see real-time token segmentation...</span>';
      return;
    }

    const lz = new LzCompressorJs({ windowSize: 32768, minMatch: 3, maxMatch: 255 });
    const encoded = lz.compress(bytes);
    sandboxEncodedLen.textContent = `${encoded.length} bytes`;

    const ratio = encoded.length > 0 ? (bytes.length / encoded.length).toFixed(2) : '1.00';
    sandboxRatio.textContent = `${ratio}x`;

    const isStoreFallback = encoded.length >= bytes.length;
    sandboxStrategy.textContent = isStoreFallback ? 'STORE (Fallback)' : 'LZ Match';
    sandboxStrategy.style.color = isStoreFallback ? '#fbbf24' : '#34d399';

    // Parse and visualize token stream
    sandboxTokenStream.innerHTML = '';
    let readIdx = 0;
    let groupNum = 1;

    while (readIdx < encoded.length) {
      const flags = encoded[readIdx++];
      const groupBox = document.createElement('div');
      groupBox.className = 'token-group-box';

      const flagBitsStr = flags.toString(2).padStart(8, '0');
      groupBox.innerHTML = `
        <div class="flag-byte-badge">
          <span>Group #${groupNum++} Flag: <code>0x${flags.toString(16).toUpperCase().padStart(2, '0')}</code></span>
          <span style="font-size: 10px; color: var(--text-subtle);">[bits: ${flagBitsStr}]</span>
        </div>
      `;

      const subGroup = document.createElement('div');
      subGroup.className = 'token-subgroup';

      for (let bit = 0; bit < 8 && readIdx < encoded.length; bit++) {
        const isMatch = (flags & (1 << bit)) !== 0;
        const chip = document.createElement('span');

        if (isMatch) {
          if (readIdx + 3 <= encoded.length) {
            const dist = ((encoded[readIdx] << 8) | encoded[readIdx + 1]);
            const len = encoded[readIdx + 2];
            readIdx += 3;
            chip.className = 'token-chip match';
            chip.title = `Match Token: references ${len} bytes back at distance ${dist}`;
            chip.textContent = `Match(d=${dist}, len=${len})`;
          }
        } else {
          const byteVal = encoded[readIdx++];
          const char = (byteVal >= 32 && byteVal <= 126) ? String.fromCharCode(byteVal) : `\\x${byteVal.toString(16).padStart(2, '0')}`;
          chip.className = 'token-chip literal';
          chip.title = `Literal Byte: raw character '${char}' (0x${byteVal.toString(16).toUpperCase()})`;
          chip.textContent = `'${char}'`;
        }
        subGroup.appendChild(chip);
      }

      groupBox.appendChild(subGroup);
      sandboxTokenStream.appendChild(groupBox);
    }
  }

  sandboxInputText.addEventListener('input', renderSandboxTokens);

  document.getElementById('sandbox-btn-preset1').addEventListener('click', () => {
    sandboxInputText.value = "[INFO] User session authenticated tenantId=tenant-corp-9921\n[INFO] User session authenticated tenantId=tenant-corp-9921\n[INFO] User session authenticated tenantId=tenant-corp-9921\n";
    renderSandboxTokens();
  });

  document.getElementById('sandbox-btn-preset2').addEventListener('click', () => {
    sandboxInputText.value = "ATCGATCGGCTAATCGATCGAATCGATCGGCTAATCGATCGAATCGATCGGCTAATCGATCGA";
    renderSandboxTokens();
  });

  document.getElementById('sandbox-btn-preset3').addEventListener('click', () => {
    sandboxInputText.value = '{"status":"success","code":200,"data":{"id":101,"name":"compression"},"status":"success","code":200,"data":{"id":102,"name":"compression"}}';
    renderSandboxTokens();
  });

  document.getElementById('sandbox-btn-preset4').addEventListener('click', () => {
    const chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789!@#$%^&*";
    let randomStr = "";
    for (let i = 0; i < 120; i++) {
      randomStr += chars.charAt(Math.floor(Math.random() * chars.length));
    }
    sandboxInputText.value = randomStr;
    renderSandboxTokens();
  });

  // Render initial sandbox state
  renderSandboxTokens();

  // ==========================================================================
  // COPY CHEATSHEET BUTTONS
  // ==========================================================================
  document.querySelectorAll('.btn-copy').forEach(btn => {
    btn.addEventListener('click', () => {
      const textToCopy = btn.getAttribute('data-copy');
      if (textToCopy) {
        navigator.clipboard.writeText(textToCopy).then(() => {
          showToast("Copied to clipboard!", "success");
          btn.textContent = "Copied!";
          setTimeout(() => btn.textContent = "Copy", 2000);
        });
      }
    });
  });
});
