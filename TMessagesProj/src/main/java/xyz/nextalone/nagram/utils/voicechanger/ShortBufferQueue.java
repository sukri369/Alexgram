package xyz.nextalone.nagram.utils.voicechanger;

import java.util.Arrays;

public class ShortBufferQueue {
    private final short[] buffer;
    private int head = 0;
    private int tail = 0;
    private int size = 0;

    public ShortBufferQueue(int capacity) {
        this.buffer = new short[capacity];
    }

    public synchronized void write(short[] data, int length) {
        for (int i = 0; i < length; i++) {
            if (size < buffer.length) {
                buffer[tail] = data[i];
                tail = (tail + 1) % buffer.length;
                size++;
            }
        }
    }

    public synchronized int read(short[] dest, int offset, int length) {
        if (size == 0) {
            // If queue is empty, fill dest with silence (0) to prevent the vocoder from stalling
            Arrays.fill(dest, offset, offset + length, (short) 0);
            return length;
        }
        int readCount = Math.min(length, size);
        for (int i = 0; i < readCount; i++) {
            dest[offset + i] = buffer[head];
            head = (head + 1) % buffer.length;
            size--;
        }
        // If we read less than requested, fill the rest with silence (0)
        if (readCount < length) {
            Arrays.fill(dest, offset + readCount, offset + length, (short) 0);
        }
        return length;
    }

    public synchronized int size() {
        return size;
    }

    public synchronized void clear() {
        head = 0;
        tail = 0;
        size = 0;
        Arrays.fill(buffer, (short) 0);
    }
}
