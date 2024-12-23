/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package hyphanet.support.io.bucket;

import java.io.IOException;

public class ArrayFactory implements Factory {

    @Override
    public RandomAccess makeBucket(long size) throws IOException {
        return new Array();
    }

    public void freeBucket(Bucket b) throws IOException {
        b.free();
    }

}
