/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package hyphanet.support.math;

import java.io.Serializable;

/**
 * Represents a running average calculator that processes numerical reports and generates current
 * values.
 *
 * <p>This interface provides methods to maintain and calculate running averages of reported values.
 * All implementations must be thread-safe, including the {@code clone()} operation.
 *
 * <p>The running average is typically calculated based on all values reported since initialization,
 * though specific implementations may use different strategies (e.g., time-based decay, sliding
 * window, etc.).
 *
 * @see Serializable
 */
public interface RunningAverage extends Serializable {

  /**
   * Creates a deep copy of this RunningAverage instance.
   *
   * <p>The new instance will be independent of the original, containing a snapshot of the current
   * state. Any subsequent reports to either instance will not affect the other.
   *
   * @return a new RunningAverage instance with the same state as this one
   */
  RunningAverage deepCopy();

  /**
   * Returns the current calculated average value.
   *
   * <p>The exact calculation method depends on the implementation.
   *
   * @return the current average value
   */
  double currentValue();

  /**
   * Reports a new double value to be included in the running average.
   *
   * <p>This method updates the internal state to include the new value in future average
   * calculations.
   *
   * @param d the value to report
   */
  void report(double d);

  /**
   * Reports a new long value to be included in the running average.
   *
   * <p>This method converts the long to a double and includes it in the running average
   * calculation.
   *
   * @param d the long value to report
   */
  void report(long d);

  /**
   * Calculates what the average would be if a specific value were to be reported.
   *
   * <p>This method does not modify the internal state of the running average. It provides a way to
   * preview the effect of reporting a value.
   *
   * @param r the value to simulate reporting
   * @return the average that would result if the value were reported
   */
  double valueIfReported(double r);

  /**
   * Returns the total number of reports that have been made to this instance.
   *
   * <p>This count can be used for various purposes such as:
   *
   * <ul>
   *   <li>Calculating weighted averages
   *   <li>Determining confidence levels
   *   <li>Estimating statistical significance
   * </ul>
   *
   * @return the total number of reports made
   */
  long countReports();
}
