package software.amazon.event.ruler;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Array-consistent matching without the O(N^2) step queue growth of ACFinder.
 *
 * <p>Instead of flattening the entire event into a single field list and tracking
 * array membership (which causes quadratic blowup when large arrays match
 * multi-field rules), this class walks the JSON tree structurally:</p>
 *
 * <ul>
 *   <li>Scalar fields and primitive arrays are accumulated into a field context</li>
 *   <li>Object arrays trigger per-element recursion with inherited context</li>
 *   <li>At leaf level (no more object arrays), matches using {@link Finder}
 *       (non-AC, with seenSteps dedup)</li>
 * </ul>
 *
 * <p>Array consistency is correct by construction: each Finder call only sees
 * fields from one element per array level, so cross-element matching is
 * impossible.</p>
 *
 * <p>For events without object arrays (e.g., primitive arrays + scalars), falls
 * back to {@link ACFinder} which handles those cases efficiently.</p>
 */
class StructuredFinder {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private StructuredFinder() {}

    /**
     * Match rules against an event using structured tree walking.
     *
     * @param json the event as a JSON string
     * @param machine the compiled rule machine
     * @param gen sub-rule context generator
     * @return list of matched rule names (empty if none, never null)
     */
    static List<Object> matchRules(final String json, final GenericMachine<?> machine,
                                   final SubRuleContext.Generator gen) throws Exception {
        final JsonNode root = MAPPER.readTree(json);
        if (!root.isObject()) {
            return new ArrayList<>();
        }

        // If no object arrays anywhere, use Finder directly.
        // Without object arrays there is no array-consistency concern,
        // so the non-AC Finder (with seenSteps dedup) is both correct and fast.
        // If no object arrays anywhere, use ACFinder directly.
        // With the step-pruning fix, ACFinder is efficient for primitive arrays
        // because it skips irrelevant fields during JSON parsing (isFieldStepUsed)
        // and prunes steps for non-matching field names (getTransitionOn).
        if (!hasObjectArray(root)) {
            final Event event = new Event(json, machine);
            return ACFinder.matchRules(event, machine, gen);
        }

        final Set<Object> matched = new HashSet<>();
        walkObject(root, "", new ArrayList<>(), machine, gen, matched);
        return new ArrayList<>(matched);
    }

    private static boolean hasObjectArray(final JsonNode node) {
        final Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
        while (fields.hasNext()) {
            final JsonNode val = fields.next().getValue();
            if (val.isArray()) {
                for (final JsonNode elem : val) {
                    if (elem.isObject()) return true;
                }
            }
            if (val.isObject() && hasObjectArray(val)) return true;
        }
        return false;
    }

    /**
     * Walk a JSON object node. Scalars and primitive array values go into the
     * field context. Sub-objects are recursed into. Object arrays trigger
     * per-element recursion.
     */
    private static void walkObject(final JsonNode node, final String prefix,
                                   final List<String> inherited,
                                   final GenericMachine<?> machine,
                                   final SubRuleContext.Generator gen,
                                   final Set<Object> matched) throws Exception {
        final List<String> ctx = new ArrayList<>(inherited);
        final List<ObjArray> objArrays = new ArrayList<>();

        final Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
        while (fields.hasNext()) {
            final Map.Entry<String, JsonNode> field = fields.next();
            final String name = field.getKey();
            final JsonNode val = field.getValue();
            final String path = prefix.isEmpty() ? name : prefix + "." + name;

            if (!machine.isFieldStepUsed(name)) continue;

            if (val.isObject()) {
                walkObject(val, path, ctx, machine, gen, matched);
            } else if (val.isArray()) {
                classifyArray(val, path, ctx, objArrays);
            } else if (val.isTextual()) {
                ctx.add(path);
                ctx.add("\"" + val.asText() + "\"");
            } else if (!val.isNull()) {
                ctx.add(path);
                ctx.add(val.asText());
            }
        }

        // No object arrays at this level: match with accumulated context
        if (objArrays.isEmpty()) {
            if (!ctx.isEmpty()) {
                matched.addAll(Finder.rulesForEvent(sortPairs(ctx), machine, gen));
            }
            return;
        }

        // Per-element recursion for each object array
        for (final ObjArray arr : objArrays) {
            for (final JsonNode elem : arr.elements) {
                if (elem.isObject()) {
                    walkObject(elem, arr.path, ctx, machine, gen, matched);
                }
            }
        }
    }

    /**
     * Classify array elements as objects (for per-element recursion) or
     * primitives (added directly to context).
     */
    private static void classifyArray(final JsonNode arrayNode, final String path,
                                      final List<String> ctx,
                                      final List<ObjArray> objArrays) {
        final List<JsonNode> objElements = new ArrayList<>();
        for (final JsonNode elem : arrayNode) {
            if (elem.isObject()) {
                objElements.add(elem);
            } else if (elem.isTextual()) {
                ctx.add(path);
                ctx.add("\"" + elem.asText() + "\"");
            } else if (!elem.isNull()) {
                ctx.add(path);
                ctx.add(elem.asText());
            }
        }
        if (!objElements.isEmpty()) {
            objArrays.add(new ObjArray(path, objElements));
        }
    }

    /** Sort name/value pairs by name for Finder (which expects sorted input). */
    private static String[] sortPairs(final List<String> pairs) {
        if (pairs.size() <= 2) return pairs.toArray(new String[0]);
        final TreeMap<String, List<String>> sorted = new TreeMap<>();
        for (int i = 0; i < pairs.size(); i += 2) {
            sorted.computeIfAbsent(pairs.get(i), k -> new ArrayList<>()).add(pairs.get(i + 1));
        }
        final List<String> result = new ArrayList<>();
        for (final Map.Entry<String, List<String>> e : sorted.entrySet()) {
            for (final String v : e.getValue()) {
                result.add(e.getKey());
                result.add(v);
            }
        }
        return result.toArray(new String[0]);
    }

    private static class ObjArray {
        final String path;
        final List<JsonNode> elements;
        ObjArray(final String p, final List<JsonNode> e) { path = p; elements = e; }
    }
}
