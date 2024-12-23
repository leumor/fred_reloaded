/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package hyphanet.support.io.stream;

import java.io.OutputStream;

public class NullOutputStream extends OutputStream {
    public NullOutputStream() {
    }

    @Override
    public void write(int b) {
    }

    @Override
    public void write(byte[] buf, int off, int len) {
    }
}

