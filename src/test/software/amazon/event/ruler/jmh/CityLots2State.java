package software.amazon.event.ruler.jmh;

import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import software.amazon.event.ruler.Benchmarks;

import java.util.ArrayList;
import java.util.List;

@State(Scope.Benchmark)
public class CityLots2State {

    final List<String> citylots2 = new ArrayList<>();

    @Setup
    public void setup() throws Exception {
        Benchmarks.readCityLots2(citylots2);
    }
}
