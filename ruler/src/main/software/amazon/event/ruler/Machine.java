package software.amazon.event.ruler;

/**
 *  Represents a state machine used to match name/value patterns to rules.
 *  The machine is thread safe. The concurrency strategy is:
 *  Multi-thread access assumed, single-thread update enforced by synchronized on
 *  addRule/deleteRule.
 *  ConcurrentHashMap and ConcurrentSkipListSet are used so that writer and readers can be in tables
 *  simultaneously. So all changes the writer made could be synced to and viable by all readers (in other threads).
 *  Though it may  generate a half-built rule to rulesForEvent() e.g. when a long rule is adding and
 *  in the middle of adding, some event is coming to query machine, it won't generate side impact with rulesForEvent
 *  because each step of routing will check next State and transition map before moving forward.
 */
public class Machine extends GenericMachine<String> {

    public Machine() {
    }
}