package software.amazon.event.ruler;

import software.amazon.event.ruler.jmh.CityLots2State;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The citylots2 events flattened once with {@link Event#flatten(String)}, which keeps every field, for benchmarks
 * that match through {@link GenericMachine#rulesForEvent(List)}. Lives in this package because
 * {@code Event.flatten} is package-private.
 */
public final class FlattenedCityLots2 {

    private static final List<List<String>> EVENTS;

    static {
        List<String> raw = new ArrayList<>();
        Benchmarks.readCityLots2(raw);
        List<List<String>> flat = new ArrayList<>(raw.size());
        for (String json : raw) {
            flat.add(Collections.unmodifiableList(Event.flatten(json)));
        }
        if (flat.size() != CityLots2State.DATASET_SIZE) {
            throw new IllegalStateException("Expected dataset size: "
                    + CityLots2State.DATASET_SIZE + ", actual: " + flat.size());
        }
        EVENTS = Collections.unmodifiableList(flat);
    }

    private FlattenedCityLots2() { }

    public static List<List<String>> events() {
        return EVENTS;
    }
}
