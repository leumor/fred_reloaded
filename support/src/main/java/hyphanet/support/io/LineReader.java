/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package hyphanet.support.io;

import org.jspecify.annotations.Nullable;

import java.io.IOException;

public interface LineReader {

    /**
     * Read a \n or \r\n terminated line of UTF-8 or ISO-8859-1.
     */
    @Nullable
    String readLine(int maxLength, int bufferSize, boolean utf) throws IOException;

}
