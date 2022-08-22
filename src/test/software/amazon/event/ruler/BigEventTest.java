package software.amazon.event.ruler;

import java.util.List;
import org.junit.Test;
import static org.junit.Assert.assertTrue;

public class BigEventTest {
    @Test
    public void WHEN_eventHasBigArrayAndLegacyFinderIsUsed_THEN_itWillCompleteSuccessfully_insteadOfCrashingOOM() throws Exception {
        // when there are large number of array elements either in the rule and/or event, it used to lead to explosion in
        // steps. A fix in Task.seenSteps collection takes a shortcut. This test is to check we won't regress
        // and uses a modified version of event and rule to avoid early optimization
        final Machine cut = new Machine();
        String rule = GenericMachineTest.readData("bigEventRule.json");
        cut.addRule("test", rule);
        String event = GenericMachineTest.readData("bigEvent.json");

        long start;
        long latency;
        List<String> list;

        // pre-warm the machine
        for(int i = 0; i < 5; i++) {
            cut.rulesForJSONEvent(event);
        }

        // Below is the example of the output:
        // matched rule name :[test]
        // use rulesForJSONEvent API, matching takes : 71 ms
        // matched rule name :[test]
        // use rulesForEvent API, matching takes : 1552 ms

        start = System.currentTimeMillis();
        list = cut.rulesForJSONEvent(event);
        System.out.println("matched rule name :" + list.toString());
        latency = System.currentTimeMillis() - start;
        System.out.println("use rulesForJSONEvent API, matching takes : " + latency + " ms");
        assertTrue(!list.isEmpty() && latency < 500);

        start = System.currentTimeMillis();
        list = cut.rulesForEvent(event);
        System.out.println("matched rule name :" + list.toString());
        latency = System.currentTimeMillis() - start;
        System.out.println("use rulesForEvent API, matching takes : " + latency + " ms");
        assertTrue(!list.isEmpty() && latency < 4000);
    }
}
