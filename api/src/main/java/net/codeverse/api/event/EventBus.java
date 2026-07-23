package net.codeverse.api.event;

import java.util.function.Consumer;

/**
 * Subscription to network events.
 *
 * Deliberately minimal. A platform already has an event system, and consumers
 * are better served by a small typed bus they can bridge into their own than by
 * a second general purpose framework competing with the first.
 *
 * <h2>Threading</h2>
 * Listeners are invoked on the thread that published the event, which is rarely
 * a server thread and never guaranteed to be one. A listener that touches the
 * game must hand off to its platform scheduler.
 *
 * A listener that throws is logged and removed from further delivery, so one
 * broken consumer cannot suppress events for every other.
 */
public interface EventBus {

    /**
     * Registers a listener.
     *
     * @param plugin    the registering plugin instance, used to unregister in bulk
     * @return a handle that can be closed to stop receiving
     */
    <T extends CodeverseEvent> Subscription subscribe(Object plugin, Class<T> type, Consumer<? super T> listener);

    /** Removes every listener registered by a plugin. */
    void unsubscribeAll(Object plugin);

    /** A single registration. Closing it stops delivery. */
    interface Subscription extends AutoCloseable {
        boolean isActive();

        @Override
        void close();
    }
}
