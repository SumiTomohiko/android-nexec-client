package jp.gr.java_conf.neko_daisuki.android.nexec.client.util;

import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.util.LinkedList;
import java.util.List;

import jp.gr.java_conf.neko_daisuki.fsyscall.Logging;
import jp.gr.java_conf.neko_daisuki.fsyscall.slave.Alarm;
import jp.gr.java_conf.neko_daisuki.fsyscall.util.ByteUtil;

/**
 * This code is based on jp.gr.java_conf.neko_daisuki.fsyscall.slave.Pipe.
 */
public class AlarmPipe {

    private static class Buffer {

        private static class Chunk {

            private int mFrom;
            private int mTo;
            private byte[] mBuffer = new byte[8192];

            public int write(byte[] buffer, int offset) {
                int len = Math.min(buffer.length - offset,
                                   mBuffer.length - mTo);
                System.arraycopy(buffer, offset, mBuffer, mTo, len);
                mTo += len;
                return len;
            }

            public int read(byte[] buffer, int offset, int len) {
                int size = Math.min(len, available());
                System.arraycopy(mBuffer, mFrom, buffer, offset, size);
                mFrom += size;
                return size;
            }

            public int read() {
                if (mTo <= mFrom) {
                    return -1;
                }
                byte b = mBuffer[mFrom];
                mFrom++;
                return 0 <= b ? b : 256 + b;
            }

            public boolean isDead() {
                return mBuffer.length <= mFrom;
            }

            public int available() {
                return mTo - mFrom;
            }

            public String toString() {
                String fmt = "Chunk(from=%d, to=%d, buffer=%s)";
                String s = mTo - mFrom < 64 ? ByteUtil.toString(mBuffer, mFrom,
                                                                mTo)
                                            : "(...snip...)";
                return String.format(fmt, mFrom, mTo, s);
            }
        }

        private static final class DummyChunk extends Chunk {

            @Override
            public int write(byte[] buffer, int offset) {
                return 0;
            }

            @Override
            public int read(byte[] buffer, int offset, int len) {
                return 0;
            }
        }

        private static final Chunk DUMMY_CHUNK = new DummyChunk();

        private List<Chunk> mChunks = new LinkedList<Chunk>();

        public void write(byte[] buffer) {
            synchronized (this) {
                int nWrote = getLastChunk().write(buffer, 0);
                int len = buffer.length;
                while (nWrote < len) {
                    Chunk chunk = new Chunk();
                    nWrote += chunk.write(buffer, nWrote);
                    mChunks.add(chunk);
                }
                notifyAll();

                /*
                mLogger.debug("Buffer.write: buffer=%s, this=%s",
                              ByteUtil.toString(buffer), this);
                              */
            }
        }

        public int read(byte[] buffer, int offset, int len) throws InterruptedException {
            int nRead = 0;
            synchronized (this) {
                while (!isReadable()) {
                    wait();
                }

                while ((nRead < len) && isReadable()) {
                    int rest = len - nRead;
                    nRead += mChunks.get(0).read(buffer, offset + nRead, rest);
                    removeFirstChunkIfDead();
                }

                /*
                mLogger.debug("Buffer.read(buffer (length=%d), offset=%d, len=%d): nRead=%d, buffer=%s, this=%s",
                              buffer.length, offset, len, nRead,
                              ByteUtil.toString(buffer, offset,
                                                offset + nRead),
                              this);
                              */
            }
            return nRead;
        }

        public int read() throws InterruptedException {
            int b;
            synchronized (this) {
                while (!isReadable()) {
                    wait();
                }

                b = mChunks.get(0).read();
                removeFirstChunkIfDead();

                /*
                mLogger.debug("Buffer.read(): b=%s, this=%s",
                              ByteUtil.toString((byte)b), this);
                              */
            }
            return b;
        }

        public int available() {
            int nBytes = 0;
            synchronized (this) {
                for (Chunk chunk: mChunks) {
                    nBytes += chunk.available();
                }
            }
            return nBytes;
        }

        public String toString() {
            StringBuffer buf = new StringBuffer();
            String sep = "";
            synchronized (this) {
                int size = mChunks.size();
                for (int i = 0; i < size; i++) {
                    String fmt = "chunks[%d]=%s";
                    String s = String.format(fmt, i, mChunks.get(i).toString());
                    buf.append(s);
                    buf.append(sep);
                    sep = ", ";
                }
            }
            return String.format("Buffer(%s)", buf.toString());
        }

        private void removeFirstChunkIfDead() {
            if (mChunks.get(0).isDead()) {
                mChunks.remove(0);
            }
        }

        private boolean isReadable() {
            return (0 < mChunks.size()) && (0 < mChunks.get(0).available());
        }

        private Chunk getLastChunk() {
            int size = mChunks.size();
            return 0 < size ? mChunks.get(size - 1) : DUMMY_CHUNK;
        }
    }

    private class PipeOutputStream extends OutputStream {

        private Alarm mAlarm;

        public PipeOutputStream(Alarm alarm) {
            mAlarm = alarm;
        }

        @Override
        public void close() throws IOException {
            mClosed = true;
        }

        @Override
        public void write(int b) throws IOException {
            mBuffer.write(new byte[] { (byte)b });
            mAlarm.alarm();
        }
    }

    private class PipeInputStream extends InputStream {

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            try {
                return mBuffer.read(b, off, len);
            }
            catch (InterruptedException unused) {
                throw new InterruptedIOException();
            }
        }

        @Override
        public int read() throws IOException {
            try {
                return mBuffer.read();
            }
            catch (InterruptedException unused) {
                throw new InterruptedIOException();
            }
        }

        /**
         * Returns a number of available bytes. This method throws an
         * IOException when the OutputStream is closed.
         */
        @Override
        public int available() throws IOException {
            int nbytes = mBuffer.available();
            if ((nbytes == 0) && mClosed) {
                throw new IOException("closed pipe");
            }
            return nbytes;
        }
    }

    private static Logging.Logger mLogger;

    private Buffer mBuffer = new Buffer();
    private boolean mClosed;
    private InputStream mIn;
    private OutputStream mOut;

    public AlarmPipe(Alarm alarm) throws IOException {
        mIn = new PipeInputStream();
        mOut = new PipeOutputStream(alarm);
        mClosed = false;
    }

    public InputStream getInputStream() {
        return mIn;
    }

    public OutputStream getOutputStream() {
        return mOut;
    }

    static {
        mLogger = new Logging.Logger("AlarmPipe");
    }
}

// vim: tabstop=4 shiftwidth=4 expandtab softtabstop=4
