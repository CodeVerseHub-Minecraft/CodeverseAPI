package net.codeverse.api.event;

import java.time.Instant;

/**
 * Something that happened on the network, worth telling other plugins about.
 *
 * Events exist so that consumers do not have to poll. A minigame plugin wanting
 * to know when someone's trust tier changes should be told, not asked to check
 * every tick, and a Discord bridge wanting to announce a link should not be
 * scraping the database for new rows.
 *
 * Events are informational and cannot be cancelled. The decision has already
 * been made and persisted by the time one is published; offering cancellation
 * would let a listener leave the network in a state where storage and
 * behaviour disagree.
 */
public interface CodeverseEvent {

    /** When the change happened, not when the listener received it. */
    Instant occurredAt();

    /**
     * Whether this event originated on another server and arrived by
     * propagation, rather than being caused here.
     *
     * Consumers that act on an event should usually check this. A plugin that
     * announces restrictions in chat wants to announce once per network, not
     * once per server that heard about it.
     */
    boolean remote();
}
