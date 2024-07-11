package software.amazon.event.ruler;

import org.junit.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

public class SubRuleContextTest {

    private static final String NAME = "name";

    @Test
    public void testGenerate() {
        SubRuleContext.Generator generatorA = new SubRuleContext.Generator();
        SubRuleContext contextA1 = generatorA.generate(NAME);
        SubRuleContext contextA2 = generatorA.generate(NAME);

        SubRuleContext.Generator generatorB = new SubRuleContext.Generator();
        SubRuleContext contextB1 = generatorB.generate(NAME);
        SubRuleContext contextB2 = generatorB.generate(NAME);
        SubRuleContext contextB3 = generatorB.generate(NAME);

        SubRuleContext contextA3 = generatorA.generate(NAME);

        double expected1 = -Double.MAX_VALUE;
        double expected2 = Math.nextUp(expected1);
        double expected3 = Math.nextUp(expected2);
        assertTrue(expected1 < expected2);
        assertTrue(expected2 < expected3);
        assertEquals(expected1, contextA1.getId(), 0.0);
        assertEquals(expected1, contextB1.getId(), 0.0);
        assertEquals(expected2, contextA2.getId(), 0.0);
        assertEquals(expected2, contextB2.getId(), 0.0);
        assertEquals(expected3, contextA3.getId(), 0.0);
        assertEquals(expected3, contextB3.getId(), 0.0);
    }

    @Test
    public void testGetters() {
        SubRuleContext.Generator generator = new SubRuleContext.Generator();
        SubRuleContext context = generator.generate(NAME);
        assertEquals(NAME, generator.getNameForGeneratedId(context.getId()));
        Set<Double> expectedIds = new HashSet<>();
        expectedIds.add(context.getId());
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