package com.oracle.truffle.r.runtime.conn;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.ByteBuffer;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.r.runtime.RError;
import com.oracle.truffle.r.runtime.RInternalError;
import com.oracle.truffle.r.runtime.conn.ConnectionSupport.BaseRConnection;
import com.oracle.truffle.r.runtime.data.model.RAbstractStringVector;

/**
 * A blocking connection for reading.
 */
public abstract class DelegateReadRConnection extends DelegateRConnection {

    protected DelegateReadRConnection(BaseRConnection base) {
        super(base);
    }

    @Override
    public void writeLines(RAbstractStringVector lines, String sep, boolean useBytes) throws IOException {
        throw new IOException(RError.Message.CANNOT_WRITE_CONNECTION.message);
    }

    @Override
    public void writeChar(String s, int pad, String eos, boolean useBytes) throws IOException {
        throw new IOException(RError.Message.CANNOT_WRITE_CONNECTION.message);
    }

    @Override
    public void writeString(String s, boolean nl) throws IOException {
        throw new IOException(RError.Message.CANNOT_WRITE_CONNECTION.message);
    }

    @Override
    public void writeBin(ByteBuffer buffer) throws IOException {
        throw new IOException(RError.Message.CANNOT_WRITE_CONNECTION.message);
    }

    @Override
    public int getc() throws IOException {
        return getInputStream().read();
    }

    @Override
    public String readChar(int nchars, boolean useBytes) throws IOException {
        if (useBytes) {
            return DelegateRConnection.readCharHelper(nchars, getInputStream());
        } else {
            final InputStreamReader isr = new InputStreamReader(getInputStream(), base.getEncoding());
            return DelegateRConnection.readCharHelper(nchars, isr);
        }
    }

    @Override
    public int readBin(ByteBuffer buffer) throws IOException {
        return getInputStream().read(buffer.array());
    }

    @Override
    public byte[] readBinChars() throws IOException {
        return DelegateRConnection.readBinCharsHelper(getInputStream());
    }

    @TruffleBoundary
    @Override
    public String[] readLinesInternal(int n, boolean warn, boolean skipNul) throws IOException {
        return readLinesHelper(n, warn, skipNul);
    }

    @Override
    public void flush() {
        // nothing to do when reading
    }

    @Override
    public OutputStream getOutputStream() {
        throw RInternalError.shouldNotReachHere();
    }

    @Override
    public boolean canRead() {
        return true;
    }

    @Override
    public boolean canWrite() {
        return false;
    }

    @Override
    public abstract InputStream getInputStream();

    @Override
    public void close() throws IOException {
        getInputStream().close();
    }

    @Override
    public void closeAndDestroy() throws IOException {
        base.closed = true;
        close();
    }

}