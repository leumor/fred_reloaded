/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package hyphanet.support.test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.fail;

public abstract class Asserts {

    private Asserts() {
    }

    public static void assertArrayEquals(byte[] expecteds, byte[] actuals) {
        if (!Arrays.equals(expecteds, actuals)) {
            fail("expected:<" + Arrays.toString(expecteds) + "> but was:<" + Arrays.toString(actuals) +
                 ">");
        }
    }

}
