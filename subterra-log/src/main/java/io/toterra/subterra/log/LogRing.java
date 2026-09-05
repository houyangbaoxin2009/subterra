package io.toterra.subterra.log;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Bounded ring buffer of recent {@link LogRecord}s (capacity fixed at
 * construction, oldest entries overwritten first). O(1) append; {@link #snapshot()}
 * returns records in chronological order. Used as the crash-time source for the
 * "last N log lines" section of the diagnostic dump (Paper/NeoForge-style tail).
 */
public final class LogRing {

    private final LogRecord[] buf;
    private final ReentrantLock lock = new ReentrantLock();
    private int head;
    private int size;

    public LogRing(int capacity) {
        if (capacity < 1) {
            throw new IllegalArgumentException("ring capacity must be >= 1");
        }
        this.buf = new LogRecord[capacity];
    }

    public int capacity() {
        return buf.length;
    }

    public void append(LogRecord record) {
        lock.lock();
        try {
            if (size == buf.length) {
                buf[head] = record;
                head = (head + 1) % buf.length;
            } else {
                int idx = (head + size) % buf.length;
                buf[idx] = record;
                size++;
            }
        } finally {
            lock.unlock();
        }
    }

    /** Chronological snapshot of the buffered records (never the live array). */
    public List<LogRecord> snapshot() {
        lock.lock();
        try {
            List<LogRecord> out = new ArrayList<>(size);
            for (int i = 0; i < size; i++) {
                out.add(buf[(head + i) % buf.length]);
            }
            return out;
        } finally {
            lock.unlock();
        }
    }

    /** Number of buffered records. */
    public int size() {
        lock.lock();
        try {
            return size;
        } finally {
            lock.unlock();
        }
    }
}