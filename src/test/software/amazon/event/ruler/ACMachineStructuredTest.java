package software.amazon.event.ruler;

/**
 * Runs the full ACMachineTest suite with structured matching enabled.
 *
 * This validates that rulesForJSONEvent() produces identical results
 * when backed by StructuredFinder instead of ACFinder. All test methods
 * are inherited from ACMachineTest; the only difference is that Machine
 * instances are created with structured matching on.
 */
public class ACMachineStructuredTest extends ACMachineTest {

    @Override
    protected Machine createMachine() {
        return Machine.builder()
                .withStructuredMatching(true)
                .build();
    }
}
