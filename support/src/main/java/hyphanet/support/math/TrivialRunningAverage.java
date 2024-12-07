package hyphanet.support.math;

import java.io.Serializable;

/**
 * A simple implementation of {@link RunningAverage} that maintains a running sum and count. This class
 * provides a thread-safe way to calculate running averages of reported values.
 * <p>
 * The average is calculated as the sum of all reported values divided by the number of reports. All
 * operations are synchronized to ensure thread safety.
 * </p>
 *
 * @see RunningAverage
 * @since 1.0
 */
public final class TrivialRunningAverage implements RunningAverage {

    private static final long serialVersionUID = 1L;

    /**
     * Creates a new TrivialRunningAverage with initial values set to zero. The initial state will have
     * no reports and a total of 0.0.
     */
    public TrivialRunningAverage() {
        state = new State(0.0, 0L);
    }

    /**
     * Creates a new TrivialRunningAverage by copying the state from another instance. This constructor
     * creates a deep copy of the provided instance.
     *
     * @param average the instance to copy from
     */
    public TrivialRunningAverage(TrivialRunningAverage average) {
        state = new State(average.state.total(), average.state.reports());
    }

    /**
     * {@inheritDoc}
     *
     * @return the number of values that have been reported
     */
    @Override
    public synchronized long countReports() {
        return state.reports();
    }

    /**
     * Returns the sum of all reported values. This method is synchronized to ensure thread safety.
     *
     * @return the total sum of all reported values
     */
    public synchronized double totalValue() {
        return state.total();
    }

    /**
     * {@inheritDoc}
     *
     * @return the current average value, or 0.0 if no reports exist
     */
    @Override
    public synchronized double currentValue() {
        return state.calculateAverage();
    }

    /**
     * {@inheritDoc}
     *
     * @param d the value to report
     *
     * @throws IllegalArgumentException if the value is NaN or infinite
     */
    @Override
    public synchronized void report(double d) {
        if (Double.isNaN(d) || Double.isInfinite(d)) {
            throw new IllegalArgumentException("Reported value must be a finite number");
        }
        state = new State(state.total() + d, state.reports() + 1);
    }

    /**
     * {@inheritDoc}
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
     * @param r the value to simulate reporting
     *
     * @return the average that would result from reporting the value
     *
     * @throws IllegalArgumentException if the value is NaN or infinite
     */
    @Override
    public synchronized double valueIfReported(double r) {
        if (Double.isNaN(r) || Double.isInfinite(r)) {
            throw new IllegalArgumentException("Value must be a finite number");
        }
        return new State(state.total() + r, state.reports() + 1).calculateAverage();
    }

    /**
     * Creates and returns a deep copy of this instance. The clone is thread-safe and independent of the
     * original instance.
     *
     * @return a new TrivialRunningAverage with the same state
     */
    @Override
    public TrivialRunningAverage deepCopy() {
        synchronized (this) {
            return new TrivialRunningAverage(this);
        }
    }

    /**
     * Immutable state record holding the running total and count of reports.
     *
     * @param total   the sum of all reported values
     * @param reports the count of all reports
     */
    private record State(double total, long reports) implements Serializable {
        /**
         * Creates a new State instance with validation.
         *
         * @throws IllegalArgumentException if reports is negative
         */
        State {
            if (reports < 0) {
                throw new IllegalArgumentException("Number of reports cannot be negative");
            }
        }

        /**
         * Calculates the current average from this state.
         *
         * @return the calculated average, or 0.0 if no reports exist
         */
        double calculateAverage() {
            if (reports == 0) {
                return 0.0;
            }
            return total / reports;
        }
    }

    private State state;
}
