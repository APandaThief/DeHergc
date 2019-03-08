package cn.chase;

import java.io.File;

public class Stream {
    private File file;
    private int bitBuffer;
    private int bitCount;

    public Stream() {

    }

    public Stream(File file, int bitBuffer, int bitCount) {
        this.file = file;
        this.bitBuffer = bitBuffer;
        this.bitCount = bitCount;
    }

    public File getFile() {
        return file;
    }

    public void setFile(File file) {
        this.file = file;
    }

    public int getBitBuffer() {
        return bitBuffer;
    }

    public void setBitBuffer(int bitBuffer) {
        this.bitBuffer = bitBuffer;
    }

    public int getBitCount() {
        return bitCount;
    }

    public void setBitCount(int bitCount) {
        this.bitCount = bitCount;
    }
}
