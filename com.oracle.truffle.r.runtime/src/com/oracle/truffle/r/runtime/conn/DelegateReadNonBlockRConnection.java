package com.oracle.truffle.r.runtime.conn;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.r.runtime.RError;
import com.oracle.truffle.r.runtime.RInternalError;
import com.oracle.truffle.r.runtime.conn.ConnectionSupport.BaseRConnection;
import com.oracle.truffle.r.runtime.data.model.RAbstractStringVector;

public abstract class DelegateReadNonBlockRConnection extends DelegateRConnection {

    private final ByteBuffer tmp = ByteBuffer.allocate(1);

    protected DelegateReadNonBlockRConnection(BaseRConnection base) {
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
        tmp.clear();
        int nread = getChannel().read(tmp);
        tmp.rewind();
        return nread > 0 ? tmp.get() : -1;
    }

    @Override
    public String readChar(int nchars, boolean useBytes) throws IOException {
        return DelegateRConnection.readCharHelper(nchars, getChannel(), useBytes);
    }

    @Override
    public int readBin(ByteBuffer buffer) throws IOException {
        return getChannel().read(buffer);
    }

    @Override
    public byte[] readBinChars() throws IOException {
        return DelegateRConnection.readBinCharsHelper(getChannel());
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

    public abstract ReadableByteChannel getChannel();

    @Override
    @Deprecated
    public InputStream getInputStream() throws IOException {
        return Channels.newInputStream(getChannel());
    }

    @Override
    public void close() throws IOException {
        getChannel().close();
    }

    @Override
    public void closeAndDestroy() throws IOException {
        base.closed = true;
        close();
    }

}