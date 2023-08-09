package software.amazon.event.ruler;

/**
 * This class stores context regarding a sub-rule.
 *
 * A sub-rule refers to name/value pairs, usually represented by Map of String to List of Patterns, that compose a rule.
 * In the case of $or, one rule will have multiple name/value pairs, and this is why we use the "sub-rule" terminology.
 */
public class SubRuleContext {

    private final double id;

    private SubRuleContext(double id) {
        this.id = id;
    }

    public double getId() {
        return id;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || !(o instanceof SubRuleContext)) {
            return false;
        }
        SubRuleContext otherContext = (SubRuleContext) o;
        return id == otherContext.id;
    }

    @Override
    public int hashCode() {
        return Double.hashCode(id);
    }

    /**
     * Generator for SubRuleContexts.
     */
    static final class Generator {

        private double nextId = -Double.MAX_VALUE;

        public SubRuleContext generate() {
            assert nextId < Double.MAX_VALUE : "SubRuleContext.Generator's nextId reached Double.MAX_VALUE - " +
                    "this required the equivalent of calling generate() at 6 billion TPS for 100 years";

            SubRuleContext subRuleContext = new SubRuleContext(nextId);
            nextId = Math.nextUp(nextId);
            return subRuleContext;
        }
    }
}
