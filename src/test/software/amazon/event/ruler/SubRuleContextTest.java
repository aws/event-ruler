package software.amazon.event.ruler;

import org.junit.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

public class SubRuleContextTest {

    private static final String NAME = "name";

    @Test
    public void testPerformance() {
        long start = System.nanoTime();

        for (int i = 0; i < 10_000_000; i++) {
            SubRuleContext.Generator generator = new SubRuleContext.Generator();
            SubRuleContext c = generator.generate(NAME);
//            assertEquals(NAME, generator.getNameForGeneratedId(c.getId()));
            assertEquals(NAME, c.getRuleName());
        }
        System.out.println( (System.nanoTime() - start) / 1000_000 + "ms");
    }

    @Test
    public void testGetters() {
        SubRuleContext.Generator generator = new SubRuleContext.Generator();
        SubRuleContext context = generator.generate(NAME);
        assertEquals(NAME, context.getRuleName());
        Set<SubRuleContext> expectedIds = new HashSet<>();
        expectedIds.add(context);
        assertEquals(expectedIds, generator.getIdsGeneratedForName(NAME));
    }

    @Test
    public void testEquals() {
        SubRuleContext.Generator generatorA = new SubRuleContext.Generator();
        SubRuleContext contextA1 = generatorA.generate(NAME);
        SubRuleContext contextA2 = generatorA.generate(NAME);

        SubRuleContext.Generator generatorB = new SubRuleContext.Generator();
        SubRuleContext contextB1 = generatorB.generate(NAME);

        assertEquals(contextA1, contextB1);
        assertNotEquals(contextA2, contextB1);
    }

    @Test
    public void testHashCode() {
        SubRuleContext.Generator generatorA = new SubRuleContext.Generator();
        SubRuleContext contextA1 = generatorA.generate(NAME);
        SubRuleContext contextA2 = generatorA.generate(NAME);

        SubRuleContext.Generator generatorB = new SubRuleContext.Generator();
        SubRuleContext contextB1 = generatorB.generate(NAME);

        assertEquals(contextA1.hashCode(), contextB1.hashCode());
        assertNotEquals(contextA2.hashCode(), contextB1.hashCode());
    }
}