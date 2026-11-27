package software.amazon.event.ruler.jmh;

import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import software.amazon.event.ruler.FlattenedCityLots2;

import java.util.List;

/** Loads and flattens the citylots2 events before the first iteration, so loading is not timed. */
@State(Scope.Benchmark)
public class FlattenedCityLots2State {

    private List<List<String>> events;

    @Setup(Level.Trial)
    public void load() {
        events = FlattenedCityLots2.events();
    }

    public List<List<String>> getEvents() {
        return events;
    }
}
