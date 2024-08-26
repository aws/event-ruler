package software.amazon.event.ruler.jmh;

import org.openjdk.jmh.annotations.AuxCounters;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;

@State(Scope.Thread)
@AuxCounters(AuxCounters.Type.OPERATIONS)
public class EventsCounter {
    public long events;
}
