package software.amazon.event.ruler.jmh;

import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import software.amazon.event.ruler.Benchmarks;

import java.util.ArrayList;
import java.util.List;

@State(Scope.Benchmark)
public class CityLots2State {

    public static final int DATASET_SIZE = 213068;

    private static final List<String> citylots2 = new ArrayList<>();

    static {
        Benchmarks.readCityLots2(citylots2);

        if (citylots2.size() != DATASET_SIZE) {
            throw new RuntimeException("Expected dataset size: " + DATASET_SIZE + ", actual: " + citylots2.size());
        }
    }

    public Iterable<String> getCityLots2() {
        return citylots2;
    }
}
