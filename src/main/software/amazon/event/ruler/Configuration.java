package software.amazon.event.ruler;

/**
 * Configuration for a Machine.
 */
public class Configuration {

    /**
     * Normally, NameStates are re-used for a given key subsequence and pattern if this key subsequence and pattern have
     * been previously added, or if the current rule has already added a pattern for the given key subsequence. Hence,
     * by default, NameState re-use is opportunistic. But by setting this flag to true, NameState re-use will be forced
     * for a key subsequence. This means that the first pattern being added for a key subsequence for a rule will re-use
     * a NameState if that key subsequence has been added before. Meaning each key subsequence has a single NameState.
     * This improves memory utilization exponentially in some cases but does lead to more sub-rules being stored in
     * individual NameStates, which Ruler sometimes iterates over, which can cause a modest runtime performance
     * regression.
     */
    private final boolean additionalNameStateReuse;

    private Configuration(boolean additionalNameStateReuse) {
        this.additionalNameStateReuse = additionalNameStateReuse;
    }

    public boolean isAdditionalNameStateReuse() {
        return additionalNameStateReuse;
    }

    public static class Builder {

        private boolean additionalNameStateReuse = false;

        public Builder withAdditionalNameStateReuse(boolean additionalNameStateReuse) {
            this.additionalNameStateReuse = additionalNameStateReuse;
            return this;
        }

        public Configuration build() {
            return new Configuration(additionalNameStateReuse);
        }
    }
}

