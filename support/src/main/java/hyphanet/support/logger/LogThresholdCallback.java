/* This code is part of Freenet. It is distributed under the GNU General
 *  * Public License, version 2 (or at your option any later version). See
 *   * http://www.gnu.org/ for further details of the GPL. */
package hyphanet.support.logger;

/**
 * Interface for receiving notifications when logging thresholds change.
 * This callback mechanism allows components to dynamically adjust their
 * behavior based on current logging configuration.
 * <p>
 * Key characteristics:
 * <ul>
 * <li>Called immediately upon registration
 * <li>Called whenever thresholds are modified
 * <li>Supports both global and detailed threshold changes
 * <li>Thread-safe execution via CopyOnWriteArrayList
 * </ul>
 * <p>
 * Common use cases:
 * <ul>
 * <li>Updating static logging flags in classes
 * <li>Adjusting debug behavior based on threshold changes
 * <li>Maintaining cached logging state
 * <li>Synchronizing logging configuration across components
 * </ul>
 * <p>
 * Implementation considerations:
 * <ul>
 * <li>Implementations should be lightweight and fast
 * <li>Must handle concurrent execution
 * <li>Should not throw exceptions
 * <li>May be called frequently when thresholds change
 * </ul>
 * <p>
 * Example usage:
 * <pre>
 * LogThresholdCallback callback = new LogThresholdCallback() {
 *     public void shouldUpdate() {
 *         // Update logging flags or behavior
 *         MyClass.logDEBUG = Logger.shouldLog(LogLevel.DEBUG, MyClass.class);
 *     }
 * };
 * Logger.registerLogThresholdCallback(callback);
 * </pre>
 *
 * @see Logger#registerLogThresholdCallback(LogThresholdCallback)
 * @see Logger#unregisterLogThresholdCallback(LogThresholdCallback)
 * @see LoggerHook#instanceRegisterLogThresholdCallback(LogThresholdCallback)
 */
public class LogThresholdCallback {

    public LogThresholdCallback() {
    }

    /**
     * Called when logging thresholds have changed.
     * This method should update any cached logging states or flags.
     */
    public void shouldUpdate() {
    }
}
