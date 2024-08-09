package software.amazon.event.ruler;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * This class stores context regarding a sub-rule.
 *
 * A sub-rule refers to name/value pairs, usually represented by Map of String to List of Patterns, that compose a rule.
 * In the case of $or, one rule will have multiple name/value pairs, and this is why we use the "sub-rule" terminology.
 */
public final class SubRuleContext {

    private final long id;
    private final Object ruleName;

    SubRuleContext(long id, Object ruleName) {
        this.id = id;
        this.ruleName = ruleName;
    }

    public Object getRuleName() {
        return ruleName;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj instanceof SubRuleContext) {
            return id == ((SubRuleContext) obj).id;
        }
        return false;
    }

    @Override
    public int hashCode() {
        return Long.hashCode(id);
    }

    /**
     * Generator for SubRuleContexts.
     */
    static final class Generator {

        private final Map<Object, Set<SubRuleContext>> nameToContext = new ConcurrentHashMap<>();
        private long nextId;

        public SubRuleContext generate(Object ruleName) {
            SubRuleContext subRuleContext = new SubRuleContext(nextId++, ruleName);
            nameToContext.computeIfAbsent(ruleName, k -> new HashSet<>()).add(subRuleContext);
            return subRuleContext;
        }

        public Set<SubRuleContext> getIdsGeneratedForName(Object ruleName) {
            return nameToContext.get(ruleName);
        }
    }
}
