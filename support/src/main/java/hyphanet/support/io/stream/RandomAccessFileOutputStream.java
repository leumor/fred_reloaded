package hyphanet.support.io.stream;

import java.io.IOException;
import java.io.OutputStream;
import java.io.RandomAccessFile;

public class RandomAccessFileOutputStream extends OutputStream {

    public RandomAccessFileOutputStream(RandomAccessFile raf) {
        this.raf = raf;
    }

    @Override
    public void write(int arg0) throws IOException {
        raf.writeByte(arg0);
    }

    @Override
    public void write(byte[] buf) throws IOException {
        raf.write(buf);
    }

    @Override
    public void write(byte[] buf, int offset, int length) throws IOException {
        raf.write(buf, offset, length);
    }

    @Override
    public void close() throws IOException {
        raf.close();
    }
    final RandomAccessFile raf;

}
