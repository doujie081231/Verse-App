/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2020  huangyuhui <huanghongxun2008@126.com> and contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package com.tungsten.fclcore.task;

import static com.tungsten.fclcore.util.DigestUtils.getDigest;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;

import static java.util.Objects.requireNonNull;

import com.tungsten.fclcore.util.Hex;
import com.tungsten.fclcore.util.Logging;
import com.tungsten.fclcore.util.io.ChecksumMismatchException;
import com.tungsten.fclcore.util.io.CompressingUtils;
import com.tungsten.fclcore.util.io.FileUtils;
import com.tungsten.fclcore.util.io.IOUtils;
import com.tungsten.fclcore.util.io.NetworkUtils;

/**
 * A task that can download a file online.
 */
public class FileDownloadTask extends FetchTask<Void> {

    public static class IntegrityCheck {
        private final String algorithm;
        private final String checksum;

        public IntegrityCheck(String algorithm, String checksum) {
            this.algorithm = requireNonNull(algorithm);
            this.checksum = requireNonNull(checksum);
        }

        public static IntegrityCheck of(String algorithm, String checksum) {
            if (checksum == null) return null;
            else return new IntegrityCheck(algorithm, checksum);
        }

        public String getAlgorithm() {
            return algorithm;
        }

        public String getChecksum() {
            return checksum;
        }

        public MessageDigest createDigest() {
            return getDigest(algorithm);
        }

        public void performCheck(MessageDigest digest) throws ChecksumMismatchException {
            String actualChecksum = Hex.encodeHex(digest.digest());
            if (!checksum.equalsIgnoreCase(actualChecksum)) {
                throw new ChecksumMismatchException(algorithm, checksum, actualChecksum);
            }
        }
    }

    private final File file;
    private final IntegrityCheck integrityCheck;
    private Path candidate;
    private final ArrayList<IntegrityCheckHandler> integrityCheckHandlers = new ArrayList<>();

    /**
     * @param url the URL of remote file.
     * @param file the location that download to.
     */
    public FileDownloadTask(URL url, File file) {
        this(url, file, null);
    }

    /**
     * @param url the URL of remote file.
     * @param file the location that download to.
     * @param integrityCheck the integrity check to perform, null if no integrity check is to be performed
     */
    public FileDownloadTask(URL url, File file, IntegrityCheck integrityCheck) {
        this(Collections.singletonList(url), file, integrityCheck);
    }

    /**
     * @param url the URL of remote file.
     * @param file the location that download to.
     * @param integrityCheck the integrity check to perform, null if no integrity check is to be performed
     * @param retry the times for retrying if downloading fails.
     */
    public FileDownloadTask(URL url, File file, IntegrityCheck integrityCheck, int retry) {
        this(Collections.singletonList(url), file, integrityCheck, retry);
    }

    /**
     * Constructor.
     * @param urls urls of remote file, will be attempted in order.
     * @param file the location that download to.
     */
    public FileDownloadTask(List<URL> urls, File file) {
        this(urls, file, null);
    }

    /**
     * Constructor.
     * @param urls urls of remote file, will be attempted in order.
     * @param file the location that download to.
     * @param integrityCheck the integrity check to perform, null if no integrity check is to be performed
     */
    public FileDownloadTask(List<URL> urls, File file, IntegrityCheck integrityCheck) {
        this(urls, file, integrityCheck, 3);
    }

    /**
     * Constructor.
     * @param urls urls of remote file, will be attempted in order.
     * @param file the location that download to.
     * @param integrityCheck the integrity check to perform, null if no integrity check is to be performed
     * @param retry the times for retrying if downloading fails.
     */
    public FileDownloadTask(List<URL> urls, File file, IntegrityCheck integrityCheck, int retry) {
        super(urls, retry);
        this.file = file;
        this.integrityCheck = integrityCheck;

        setName(file.getName());
    }

    public File getFile() {
        return file;
    }

    public FileDownloadTask setCandidate(Path candidate) {
        this.candidate = candidate;
        return this;
    }

    public void addIntegrityCheckHandler(IntegrityCheckHandler handler) {
        integrityCheckHandlers.add(Objects.requireNonNull(handler));
    }

    @Override
    protected EnumCheckETag shouldCheckETag() {
        // Check cache
        if (integrityCheck != null && caching) {
            Optional<Path> cache = repository.checkExistentFile(candidate, integrityCheck.getAlgorithm(), integrityCheck.getChecksum());
            if (cache.isPresent()) {
                try {
                    FileUtils.copyFile(cache.get().toFile(), file);
                    Logging.LOG.log(Level.FINER, "Successfully verified file " + file + " from " + urls.get(0));
                    return EnumCheckETag.CACHED;
                } catch (IOException e) {
                    Logging.LOG.log(Level.WARNING, "Failed to copy cache files", e);
                }
            }
            return EnumCheckETag.NOT_CHECK_E_TAG;
        } else {
            return EnumCheckETag.CHECK_E_TAG;
        }
    }

    @Override
    protected void beforeDownload(URL url) {
        Logging.LOG.log(Level.FINER, "Downloading " + url + " to " + file);
    }

    @Override
    protected void useCachedResult(Path cache) throws IOException {
        FileUtils.copyFile(cache.toFile(), file);
    }

    @Override
    protected Context getContext(URLConnection conn, boolean checkETag) throws IOException {
        Path temp = Files.createTempFile(null, null);
        RandomAccessFile rFile = new RandomAccessFile(temp.toFile(), "rw");
        MessageDigest digest = integrityCheck == null ? null : integrityCheck.createDigest();

        return new Context() {
            @Override
            public void write(byte[] buffer, int offset, int len) throws IOException {
                if (digest != null) {
                    digest.update(buffer, offset, len);
                }

                rFile.write(buffer, offset, len);
            }

            @Override
            public void close() throws IOException {
                try {
                    rFile.close();
                } catch (IOException e) {
                    Logging.LOG.log(Level.WARNING, "Failed to close file: " + rFile, e);
                }

                if (!isSuccess()) {
                    try {
                        Files.delete(temp);
                    } catch (IOException e) {
                        Logging.LOG.log(Level.WARNING, "Failed to delete file: " + rFile, e);
                    }
                    return;
                }

                finishDownload(temp, conn, checkETag, digest);
            }
        };
    }

    /**
     * 下载收尾：完整性检查 → 落盘 → 缓存。单连接与多连接分段下载共用。
     * @param digest 单连接流程在下载过程中增量算好的摘要；分段下载无法顺序算，传 null 由这里回读计算
     */
    private void finishDownload(Path temp, URLConnection conn, boolean checkETag, MessageDigest digest) throws IOException {
        for (IntegrityCheckHandler handler : integrityCheckHandlers) {
            handler.checkIntegrity(temp, file.toPath());
        }

        Files.deleteIfExists(file.toPath());
        if (!FileUtils.makeDirectory(file.getAbsoluteFile().getParentFile()))
            throw new IOException("Unable to make parent directory " + file);

        try {
            FileUtils.moveFile(temp.toFile(), file);
        } catch (Exception e) {
            throw new IOException("Unable to move temp file from " + temp + " to " + file, e);
        }

        // Integrity check
        if (integrityCheck != null) {
            MessageDigest actual = digest;
            if (actual == null) {
                // 分段下载是乱序写入，摘要只能落盘后顺序回读一次
                actual = integrityCheck.createDigest();
                try (InputStream in = Files.newInputStream(file.toPath())) {
                    byte[] buffer = new byte[IOUtils.DEFAULT_BUFFER_SIZE];
                    int len;
                    while ((len = in.read(buffer)) != -1) {
                        actual.update(buffer, 0, len);
                    }
                }
            }
            integrityCheck.performCheck(actual);
        }

        if (caching && integrityCheck != null) {
            try {
                repository.cacheFile(file.toPath(), integrityCheck.getAlgorithm(), integrityCheck.getChecksum());
            } catch (IOException e) {
                Logging.LOG.log(Level.WARNING, "Failed to cache file", e);
            }
        }

        if (checkETag) {
            repository.cacheRemoteFile(file.toPath(), conn);
        }
    }

    // ===== 多连接分段下载（参考 PCL 的多线程下载） =====

    /** 小于该体积不值得开多连接：连接与握手开销大于并行收益 */
    private static final long PARALLEL_MIN_SIZE = 1024L * 1024;
    /** 每段的最小体积，决定拆几段 */
    private static final long MIN_SEGMENT_SIZE = 512L * 1024;
    /** 单个文件最大并发段数（同时安装几十个文件，段数过多反而抢带宽） */
    private static final int MAX_SEGMENTS = 6;

    private static final ExecutorService SEGMENT_POOL = Executors.newFixedThreadPool(MAX_SEGMENTS, r -> {
        Thread t = new Thread(r, "fcl-download-segment");
        t.setDaemon(true);
        return t;
    });

    @Override
    protected int getSegmentCount(URLConnection conn, long contentLength) {
        if (!(conn instanceof HttpURLConnection)) return 1;
        if (contentLength < PARALLEL_MIN_SIZE) return 1;
        String acceptRanges = ((HttpURLConnection) conn).getHeaderField("Accept-Ranges");
        if (acceptRanges != null && acceptRanges.toLowerCase(Locale.ROOT).contains("none")) return 1;
        long bySize = contentLength / MIN_SEGMENT_SIZE;
        return (int) Math.max(1, Math.min(MAX_SEGMENTS, bySize));
    }

    @Override
    protected boolean downloadInSegments(URLConnection conn, long contentLength, int segments, boolean checkETag) throws IOException {
        URL url = conn.getURL();
        long segmentSize = contentLength / segments;
        Path temp = null;
        try {
            temp = Files.createTempFile(null, null);
            final Path tempFile = temp;
            final AtomicLong downloaded = new AtomicLong();
            final AtomicReference<Exception> failure = new AtomicReference<>();
            List<Future<?>> futures = new ArrayList<>(segments);
            for (int i = 0; i < segments; i++) {
                final long start = i * segmentSize;
                final long end = (i == segments - 1) ? contentLength - 1 : start + segmentSize - 1;
                futures.add(SEGMENT_POOL.submit(() -> {
                    try {
                        downloadSegment(url, start, end, tempFile, downloaded);
                    } catch (Exception e) {
                        failure.compareAndSet(null, e);
                    }
                }));
            }

            long lastCount = 0;
            long lastReport = System.currentTimeMillis();
            while (true) {
                boolean allDone = true;
                for (Future<?> f : futures) {
                    if (!f.isDone()) {
                        allDone = false;
                        break;
                    }
                }
                long current = downloaded.get();
                updateProgress(current, contentLength);
                long now = System.currentTimeMillis();
                if (now - lastReport >= 1000) {
                    updateDownloadSpeed((int) Math.min(Integer.MAX_VALUE, current - lastCount));
                    lastCount = current;
                    lastReport = now;
                }
                if (isCancelled()) {
                    for (Future<?> f : futures) f.cancel(true);
                    return false;
                }
                if (allDone) break;
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    for (Future<?> f : futures) f.cancel(true);
                    return false;
                }
            }

            if (failure.get() != null) {
                // 服务端忽略 Range（返回 200）或某段失败：整文件回退单连接
                Logging.LOG.log(Level.WARNING, "Segmented download failed, falling back to single connection: " + url, failure.get());
                return false;
            }

            finishDownload(temp, conn, checkETag, null);
            return true;
        } finally {
            // 成功时 finishDownload 已把临时文件移走；失败/回退时在这里清理
            if (temp != null) {
                try {
                    Files.deleteIfExists(temp);
                } catch (IOException e) {
                    Logging.LOG.log(Level.WARNING, "Failed to delete temp file: " + temp, e);
                }
            }
        }
    }

    /** 下载 [start, end] 区间并写入临时文件的对应偏移；服务端不返回 206 时抛异常触发回退 */
    private void downloadSegment(URL url, long start, long end, Path tempFile, AtomicLong counter) throws IOException {
        URLConnection connection = NetworkUtils.createConnection(url);
        if (!(connection instanceof HttpURLConnection)) throw new IOException("Not an HTTP connection: " + url);
        HttpURLConnection http = (HttpURLConnection) connection;
        try {
            http.setRequestProperty("Range", "bytes=" + start + "-" + end);
            int code = http.getResponseCode();
            if (code != 206) throw new IOException("Server ignored Range request, code=" + code);
            long expected = end - start + 1;
            long written = 0;
            try (InputStream in = http.getInputStream();
                 RandomAccessFile raf = new RandomAccessFile(tempFile.toFile(), "rw")) {
                raf.seek(start);
                byte[] buffer = new byte[IOUtils.DEFAULT_BUFFER_SIZE];
                int len;
                while ((len = in.read(buffer)) != -1) {
                    raf.write(buffer, 0, len);
                    written += len;
                    counter.addAndGet(len);
                }
            }
            if (written != expected) throw new IOException("Segment incomplete: " + written + "/" + expected);
        } finally {
            http.disconnect();
        }
    }

    public interface IntegrityCheckHandler {
        /**
         * Check whether the file is corrupted or not.
         * @param filePath the file locates in (maybe in temp directory)
         * @param destinationPath for real file name
         * @throws IOException if the file is corrupted
         */
        void checkIntegrity(Path filePath, Path destinationPath) throws IOException;
    }

    public static final IntegrityCheckHandler ZIP_INTEGRITY_CHECK_HANDLER = (filePath, destinationPath) -> {
        String ext = FileUtils.getExtension(destinationPath).toLowerCase(Locale.ROOT);
        if (ext.equals("zip") || ext.equals("jar")) {
            try (FileSystem ignored = CompressingUtils.createReadOnlyZipFileSystem(filePath)) {
                // test for zip format
            }
        }
    };
}
