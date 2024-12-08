/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package hyphanet.support.math;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serial;
import java.io.Serializable;
import java.util.Arrays;

/**
 * A thread-safe implementation of {@link RunningAverage} that maintains a simple linear mean of the last
 * N reported values using a circular buffer.
 *
 * <p>This implementation provides O(1) time complexity for both reporting new values
 * and calculating the current average. The internal state is maintained as an immutable record to ensure
 * thread-safety and consistency.</p>
 *
 * <h2>Features:</h2>
 * <ul>
 *   <li>Fixed-size circular buffer for storing values</li>
 *   <li>Thread-safe operations</li>
 *   <li>Constant-time average calculation</li>
 *   <li>Support for deep copying</li>
 * </ul>
 *
 * <h2>Usage Example:</h2>
 * <pre>{@code
 * RunningAverage avg = new SimpleRunningAverage(5, 0.0);
 * avg.report(1.0);
 * avg.report(2.0);
 * avg.report(3.0);
 * double mean = avg.currentValue(); // Returns (1.0 + 2.0 + 3.0) / 3
 * }</pre>
 *
 * @author amphibian
 */
public final class SimpleRunningAverage implements RunningAverage {
    /**
     * Serial version UID for serialization support
     */
    @Serial
    private static final long serialVersionUID = -1;

    /**
     * Logger instance for debugging purposes
     */
    private static final Logger logger = LoggerFactory.getLogger(SimpleRunningAverage.class);

    /**
     * Constructs a new running average calculator with the specified buffer size and initial value.
     *
     * <p>The initial value is used as the result of {@link #currentValue()} until the first
     * value is reported.</p>
     *
     * @param length    the number of values to maintain in the running average
     * @param initValue the initial value to use when no values have been reported
     *
     * @throws IllegalArgumentException if {@code length} is less than or equal to zero
     */
    public SimpleRunningAverage(int length, double initValue) {
        if (length <= 0) {
            throw new IllegalArgumentException("Length must be positive");
        }
        state = new State(new double[length], initValue, 0, 0, 0.0, 0);
    }

    /**
     * Creates a deep copy of an existing SimpleRunningAverage instance.
     *
     * <p>The new instance will have identical state but will operate independently
     * of the original instance.</p>
     *
     * @param a the SimpleRunningAverage instance to copy
     */
    public SimpleRunningAverage(SimpleRunningAverage a) {
        var oldState = a.state;
        state = new State(Arrays.copyOf(oldState.refs, oldState.refs.length), oldState.initValue,
                          oldState.nextSlotPtr, oldState.curLen, oldState.total, oldState.totalReports);
    }

    /**
     * {@inheritDoc}
     *
     * <p>This implementation creates a completely independent copy of the running
     * average calculator, including its internal buffer and state.</p>
     *
     * @return a new independent copy of this running average calculator
     *
     * @see #SimpleRunningAverage(SimpleRunningAverage)
     */
    @Override
    public SimpleRunningAverage deepCopy() {
        synchronized (this) {
            return new SimpleRunningAverage(this);
        }
    }

    /**
     * Resets this running average calculator to its initial state.
     *
     * <p>After calling this method:
     * <ul>
     *   <li>All recorded values are cleared</li>
     *   <li>{@link #currentValue()} will return the initial value</li>
     *   <li>{@link #countReports()} will return 0</li>
     * </ul></p>
     */
    public synchronized void clear() {
        state = new State(new double[state.refs.length], state.initValue, 0, 0, 0.0, 0);
    }

    /**
     * {@inheritDoc}
     *
     * <p>If no values have been reported, returns the initial value specified
     * in the constructor.</p>
     *
     * @return the current average value, or the initial value if no values have been reported
     */
    @Override
    public synchronized double currentValue() {
        return state.curLen == 0 ? state.initValue : state.total / state.curLen;
    }

    /**
     * {@inheritDoc}
     *
     * <p>This method calculates the hypothetical average without modifying the
     * internal state. If the buffer is not full, the value is added to the existing sum. If the buffer
     * is full, the oldest value is subtracted before adding the new value.</p>
     *
     * @param r the hypothetical value to report
     *
     * @return the average that would result from reporting the value
     */
    @Override
    public synchronized double valueIfReported(double r) {
        if (state.curLen < state.refs.length) {
            return (state.total + r) / (state.curLen + 1);
        }
        return (state.total + r - state.refs[state.nextSlotPtr]) / state.curLen;
    }

    /**
     * {@inheritDoc}
     *
     * <p>This implementation maintains a circular buffer of the most recent values.
     * When the buffer is full, new values replace the oldest ones.</p>
     *
     * @param d the value to include in the running average
     */
    @Override
    public synchronized void report(double d) {
        if (logger.isDebugEnabled()) {
            logger.debug("report({}) on {}", d, this);
        }
        state = state.withNewReport(d);
    }

    /**
     * {@inheritDoc}
     *
     * <p>This implementation converts the long value to a double and delegates
     * to {@link #report(double)}.</p>
     *
     * @param d the long value to report
     *
     * @see #report(double)
     */
    @Override
    public void report(long d) {
        report((double) d);
    }

    /**
     * {@inheritDoc}
     *
     * @return the total number of reports made since initialization or last {@link #clear()}
     */
    @Override
    public synchronized long countReports() {
        return state.totalReports;
    }

    @Override
    public synchronized String toString() {
        return "%s: curLen=%d, ptr=%d, total=%f, average=%f".formatted(super.toString(), state.curLen,
                                                                       state.nextSlotPtr, state.total,
                                                                       state.curLen == 0 ? 0 :
                                                                           state.total / state.curLen);
    }


    private record State(double[] refs, double initValue, int nextSlotPtr, int curLen, double total,
                         long totalReports) implements Serializable {
        private State {
            refs = refs.clone(); // Defensive copy
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null || getClass() != obj.getClass()) {
                return false;
            }
            State other = (State) obj;
            return Double.compare(other.initValue, initValue) == 0 && other.nextSlotPtr == nextSlotPtr &&
                   other.curLen == curLen && Double.compare(other.total, total) == 0 &&
                   other.totalReports == totalReports && Arrays.equals(refs, other.refs);
        }

        @Override
        public int hashCode() {
            int result = Arrays.hashCode(refs);
            result = 31 * result + Double.hashCode(initValue);
            result = 31 * result + nextSlotPtr;
            result = 31 * result + curLen;
            result = 31 * result + Double.hashCode(total);
            result = 31 * result + Long.hashCode(totalReports);
            return result;
        }

        @Override
        public String toString() {
            return "State[refs=" + Arrays.toString(refs) + ", initValue=" + initValue +
                   ", nextSlotPtr=" + nextSlotPtr + ", curLen=" + curLen + ", total=" + total +
                   ", totalReports=" + totalReports + "]";
        }

        /**
         * Creates a new State instance with an updated running average after reporting a new value.
         *
         * <p>This method implements the circular buffer logic for maintaining the running average:
         * <ul>
         *   <li>If the buffer is not full, the value is added and length is incremented</li>
         *   <li>If the buffer is full, the oldest value is removed before adding the new one</li>
         * </ul></p>
         *
         * <p>The method maintains these invariants:
         * <ul>
         *   <li>The running sum is always accurate for the current values</li>
         *   <li>The circular buffer pointer always points to the next write position</li>
         *   <li>The current length never exceeds the buffer size</li>
         * </ul></p>
         *
         * @param value the new value to add to the running average
         *
         * @return a new State instance reflecting the addition of the new value
         */
        private State withNewReport(double value) {
            double[] newRefs = refs.clone();
            int newPtr = nextSlotPtr;
            int newLen = curLen;
            double newTotal = total;

            if (curLen < refs.length) {
                newLen++; // Increment length if buffer not full
            } else {
                newTotal -= newRefs[newPtr]; // Remove oldest value from sum if buffer full
            }

            newRefs[newPtr] = value;
            newPtr = (newPtr + 1) % refs.length; // Advance pointer with wraparound
            newTotal += value;

            return new State(newRefs, initValue, newPtr, newLen, newTotal, totalReports + 1);
        }
    }

    private State state;
}
