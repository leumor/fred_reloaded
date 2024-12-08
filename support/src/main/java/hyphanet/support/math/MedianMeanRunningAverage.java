package hyphanet.support.math;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * <p>A {@link RunningAverage} implementation that tracks both the median and mean
 * of a series of values. This implementation maintains a complete history of all reported values to
 * calculate accurate statistics.</p>
 *
 * <p><strong>Warning:</strong> This class uses memory proportional to the number
 * of reports, making it suitable primarily for debugging purposes. The time complexity for
 * calculating the current value is O(N log N) where N is the number of reports.</p>
 *
 * <p>Thread-safety is ensured through synchronization and immutable state
 * management using a record-based approach.</p>
 *
 * @author Matthew Toseland (0xE43DA450)
 * @see RunningAverage
 * @see TrivialRunningAverage
 */
public final class MedianMeanRunningAverage implements RunningAverage {
    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * <p>Constructs a new empty MedianMeanRunningAverage instance.</p>
     * <p>Initializes with an empty report list and a new TrivialRunningAverage
     * for mean calculations.</p>
     */
    public MedianMeanRunningAverage() {
        this.state = new State(new ArrayList<>(), new TrivialRunningAverage());
    }

    /**
     * <p>Copy constructor that creates a new instance with the same state as the provided one.</p>
     *
     * @param other the MedianMeanRunningAverage instance to copy from
     */
    public MedianMeanRunningAverage(MedianMeanRunningAverage other) {
        this.state =
            new State(new ArrayList<>(other.state.reports()), other.state.mean().deepCopy());
    }

    /**
     * {@inheritDoc}
     * <p>Creates a thread-safe deep copy of this instance.</p>
     *
     * @return a new MedianMeanRunningAverage instance with the same state
     */
    @Override
    public MedianMeanRunningAverage deepCopy() {
        synchronized (this) {
            return new MedianMeanRunningAverage(this);
        }
    }

    /**
     * {@inheritDoc}
     * <p>Returns the total number of values reported to this instance.</p>
     *
     * @return the number of reports made to this instance
     */
    @Override
    public synchronized long countReports() {
        return state.reports().size();
    }

    /**
     * {@inheritDoc}
     * <p>Calculates and returns the current median value. If there are no reports,
     * returns 0.0. For an odd number of reports, returns the middle value. For an even number,
     * returns the lower of the two middle values.</p>
     *
     * @return the current median value, or 0.0 if no reports exist
     */
    @Override
    public synchronized double currentValue() {
        var reports = new ArrayList<>(state.reports());
        int size = reports.size();
        if (size == 0) {
            return 0.0;
        }
        Collections.sort(reports);
        return reports.get(size / 2);
    }

    /**
     * {@inheritDoc}
     * <p>Reports a new value to be included in both median and mean calculations.</p>
     *
     * @param d the value to report
     */
    @Override
    public synchronized void report(double d) {
        state = state.withNewReport(d);
    }

    /**
     * {@inheritDoc}
     * <p>Reports a long value by converting it to double.</p>
     *
     * @param d the long value to report
     */
    @Override
    public void report(long d) {
        report((double) d);
    }

    /**
     * {@inheritDoc}
     *
     * @throws UnsupportedOperationException always, as this operation is not supported
     */
    @Override
    public double valueIfReported(double r) {
        throw new UnsupportedOperationException();
    }

    /**
     * <p>Returns a string representation of both the median and mean values.</p>
     *
     * @return a string in the format "Median [median_value] mean [mean_value]"
     */
    @Override
    public synchronized String toString() {
        return "Median " + currentValue() + " mean " + meanValue();
    }

    /**
     * <p>Returns the current arithmetic mean of all reported values.</p>
     *
     * @return the current mean value
     */
    public synchronized double meanValue() {
        return state.mean().currentValue();
    }

    /**
     * <p>Immutable state record holding the list of reports and mean calculator.</p>
     *
     * @param reports the list of all reported values
     * @param mean    the TrivialRunningAverage instance for mean calculation
     */
    private record State(List<Double> reports, TrivialRunningAverage mean) implements Serializable {
        private State {
            reports = List.copyOf(reports);
        }

        /**
         * <p>Creates a new State instance with an additional reported value.</p>
         *
         * @param value the new value to add to the reports
         *
         * @return a new State instance including the new value
         */
        private State withNewReport(double value) {
            var newReports = new ArrayList<>(reports);
            newReports.add(value);
            var newMean = mean.deepCopy();
            newMean.report(value);
            return new State(newReports, newMean);
        }
    }

    private State state;

}
