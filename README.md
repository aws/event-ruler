# Event Ruler

Event Ruler (called Ruler in rest of the doc for brevity) is a Java library 
that allows matching **Rules** to **Events**. An event is a list of fields, which 
may be given as name/value pairs or as a JSON object.  A rule associates event 
field names with lists of possible values.  There are two reasons to use Ruler:

1. It's fast; the time it takes to match Events doesn't depend on the number of Rules.
2. Customers like the JSON "query language" for expressing rules.

Contents:

1. [Ruler by Example](#ruler-by-example)
2. [And and Or With Ruler](#and-and-or-relationship-among-fields-with-ruler)
3. [How to Use Ruler](#how-to-use-ruler)
4. [JSON Text Matching](#json-text-matching)
5. [JSON Array Matching](#json-array-matching)
6. [Compiling and Checking Rules](#compiling-and-checking-rules)
7. [Performance](#performance)

It's easiest to explain by example.

##  Ruler by Example

An Event is a JSON object.  Here's an example:

```javascript
{
  "version": "0",
  "id": "ddddd4-aaaa-7777-4444-345dd43cc333",
  "detail-type": "EC2 Instance State-change Notification",
  "source": "aws.ec2",
  "account": "012345679012",
  "time": "2017-10-02T16:24:49Z",
  "region": "us-east-1",
  "resources": [
    "arn:aws:ec2:us-east-1:123456789012:instance/i-000000aaaaaa00000"
  ],
  "detail": {
    "c-count": 5,
    "d-count": 3,
    "x-limit": 301.8,
    "source-ip": "10.0.0.33",
    "instance-id": "i-000000aaaaaa00000",
    "state": "running"
  }
}
```

You can also see this as a set of name/value pairs. For brevity, we present
only a sampling.  Ruler has APIs for providing events both in JSON form and
as name/value pairs:

```
    +--------------+------------------------------------------+
    | name         | value                                    |
    |--------------|------------------------------------------|
    | source       | "aws.ec2"                                |
    | detail-type  | "EC2 Instance State-change Notification" |
    | detail.state | "running"                                |
    +--------------+------------------------------------------+
```

Events in the JSON form may be provided in the form of a raw JSON String,
or a parsed [Jackson JsonNode](https://fasterxml.github.io/jackson-databind/javadoc/2.12/com/fasterxml/jackson/databind/JsonNode.html).

### Simple matching

The rules in this section all match the sample event above:

```javascript
{
  "detail-type": [ "EC2 Instance State-change Notification" ],
  "resources": [ "arn:aws:ec2:us-east-1:123456789012:instance/i-000000aaaaaa00000" ],
  "detail": {
    "state": [ "initializing", "running" ]
  }
}
```
This will match any event with the provided values for the `resource`,
`detail-type`, and `detail.state` values, ignoring any other fields in the
event. It would also match if the value of `detail.state` had been
`"initializing"`.

Values in rules are always provided as arrays, and match if the value in the
event is one of the values provided in the array.  The reference to `resources`
shows that if the value in the event is also an array, the rule matches if the
intersection between the event array and rule-array is non-empty.

### Prefix matching

```javascript
{
  "time": [ { "prefix": "2017-10-02" } ]
}
```
Prefix matches only work on string-valued fields.

### Suffix matching

```javascript
{
  "source": [ { "suffix": "ec2" } ]
}
```
Suffix matches only work on string-valued fields.

### Equals-ignore-case matching

```javascript
{
  "source": [ { "equals-ignore-case": "EC2" } ]
}
```
Equals-ignore-case matches only work on string-valued fields.

### Wildcard matching

```javascript
{
  "source": [ { "wildcard": "Simple*Service" } ]
}
```
Wildcard matches only work on string-valued fields. A single value can contain zero to many wildcard characters, but
consecutive wildcard characters are not allowed. To match the asterisk character specifically, a wildcard character can
be escaped with a backslash. Two consecutive backslashes (i.e. a backslash escaped with a backslash) represents the
actual backslash character. A backslash escaping any character other than asterisk or backslash is not allowed.

### Anything-but matching

Anything-but matching does what the name says: matches anything *except* what's provided in the rule.

Anything-but works with single string and numeric values or lists, which have to contain entirely strings or
entirely numerics.  It also may be applied to a prefix match.

Single anything-but (string, then numeric):
```javascript
{
  "detail": {
    "state": [ { "anything-but": "initializing" } ]
  }
}

{
  "detail": {
    "x-limit": [ { "anything-but": 123 } ]
  }
}
```
Anything-but list (strings):
```javascript
{
  "detail": {
    "state": [ { "anything-but": [ "stopped", "overloaded" ] } ]
  }
}
```

Anything-but list (numbers):
```javascript
{
  "detail": {
    "x-limit": [ { "anything-but": [ 100, 200, 300 ] } ]
  }
}
```

Anything-but prefix:
```javascript
{
  "detail": {
    "state": [ { "anything-but": { "prefix": "init" } } ]
  }
}
```
### Numeric matching
```javascript
{
  "detail": {
    "c-count": [ { "numeric": [ ">", 0, "<=", 5 ] } ],
    "d-count": [ { "numeric": [ "<", 10 ] } ],
    "x-limit": [ { "numeric": [ "=", 3.018e2 ] } ]
  }
}  
```

Above, the references to `c-count`, `d-count`, and `x-limit` illustrate numeric matching,
and only
work with values that are JSON numbers.  Numeric matching is limited to value between
-5.0e9 and +5.0e9 inclusive, with 15 digits of precision, that is to say 6 digits
to the right of the decimal point.

### IP Address Matching
```javascript
{
  "detail": {
    "source-ip": [ { "cidr": "10.0.0.0/24" } ]
  }
}
```

This also works with IPv6 addresses.

### Exists matching

Exists matching works on the presence or absence of a field in the JSON event.

The rule below will match any event which has a detail.c-count field present.

```javascript
{
  "detail": {
    "c-count": [ { "exists": true  } ]
  }
}  
```

The rule below will match any event which has no detail.c-count field.

```javascript
{
  "detail": {
    "c-count": [ { "exists": false  } ]
  }
}  
```

**Note** ```Exists``` match **only works on the leaf nodes.** It does not work on intermediate nodes.

As an example, the above example for ```exists : false ``` would match the event below:

```javascript
{
  "detail-type": [ "EC2 Instance State-change Notification" ],
  "resources": [ "arn:aws:ec2:us-east-1:123456789012:instance/i-000000aaaaaa00000" ],
  "detail": {
    "state": [ "initializing", "running" ]
  }
}
```

but would also match the event below because ```c-count``` is not a leaf node:

```javascript
{
  "detail-type": [ "EC2 Instance State-change Notification" ],
  "resources": [ "arn:aws:ec2:us-east-1:123456789012:instance/i-000000aaaaaa00000" ],
  "detail": {
    "state": [ "initializing", "running" ]
    "c-count" : {
       "c1" : 100
    }
  }
}
```


### Complex example

```javascript
{
  "time": [ { "prefix": "2017-10-02" } ],
  "detail": {
    "state": [ { "anything-but": "initializing" } ],
    "c-count": [ { "numeric": [ ">", 0, "<=", 5 ] } ],
    "d-count": [ { "numeric": [ "<", 10 ] } ],
    "x-limit": [ { "anything-but": [ 100, 200, 300 ] } ],
    "source-ip": [ { "cidr": "10.0.0.0/8" } ]
  }
}
```

And and Or Relationship among fields with Ruler
=====================
### Default "And" relationship
As the examples above show, Ruler considers a rule to match if **all** of the fields
named in the rule match, and it considers a field to match if **any** of the provided
field values match, __that is to say Ruler has applied "And" logic to all fields by
default without "And" primitive is required__.

### "Or" relationship
There are two ways to reach the "Or" effects:
* Add multiple rules with the same rule name and each individual rule will be treated as one of "Or" condition by Ruler.
  Refer to below under **addRule()** section on how to achieve an "Or" effect in that way.
* Use the "$or" primitive to express the "Or" relationship within the rule.

#### The "$or" Primitive
The "$or" primitive to allow the customer directly describe the "Or" relationship among fields in the rule.

Ruler recognizes "Or" relationship **only** when the rule has met **all** below conditions:
* There is "$or" on field attribute in the rule followed with an array – e.g. "$or": []
* There are 2+ objects in the "$or" array at least : "$or": [{}, {}]
* There has no filed name using Ruler keywords in Object of "$or" array, refer to RESERVED_FIELD_NAMES_IN_OR_RELATIONSHIP in `/src/main/software/amazon/event/ruler/Constants.java#L38`
  for example, below rule will be not parsed as "Or" relationship because "numeric" and "prefix" are Ruler reserved keywords.
  ```
  { 
     "$or": [ {"numeric" : 123}, {"prefix": "abc"} ] 
  } 
  ```
Otherwise, Ruler just treats the "$or" as normal filed name the same as other string in the rule.

#### Rule examples with "$or" Primitive
Normal "Or":
```javascript
// Effect of "source" && ("metricName" || "namespace")
{
  "source": [ "aws.cloudwatch" ], 
  "$or": [
    { "metricName": [ "CPUUtilization", "ReadLatency" ] },
    { "namespace": [ "AWS/EC2", "AWS/ES" ] }
  ] 
}
```
Parallel "Or":
```javascript
// Effect of ("metricName" || "namespace") && ("detail.source" || "detail.detail-type")
{
  "$or": [
    { "metricName": [ "CPUUtilization", "ReadLatency" ] },
    { "namespace": [ "AWS/EC2", "AWS/ES" ] }
  ], 
  "detail" : {
    "$or": [
      { "source": [ "aws.cloudwatch" ] },
      { "detail-type": [ "CloudWatch Alarm State Change"] }
    ]
  }
}
```
"Or" has an "And" inside
```javascript
// Effect of ("source" && ("metricName" || ("metricType && "namespace") || "scope"))
{
  "source": [ "aws.cloudwatch" ],
  "$or": [
    { "metricName": [ "CPUUtilization", "ReadLatency" ] },
    {
      "metricType": [ "MetricType" ] ,
      "namespace": [ "AWS/EC2", "AWS/ES" ]
    },
    { "scope": [ "Service" ] }
  ]
}
```
Nested "Or" and "And"
```javascript
// Effect of ("source" && ("metricName" || ("metricType && "namespace" && ("metricId" || "spaceId")) || "scope"))
{
  "source": [ "aws.cloudwatch" ],
  "$or": [
    { "metricName": [ "CPUUtilization", "ReadLatency" ] },
    {
      "metricType": [ "MetricType" ] ,
      "namespace": [ "AWS/EC2", "AWS/ES" ],
      "$or" : [
        { "metricId": [ 1234 ] },
        { "spaceId": [ 1000 ] }
      ]
    },
    { "scope": [ "Service" ] }
  ]
}
```

#### The backward compatibility of using "$or" as filed name in the rule
"$or" is possibly already used as a normal key in some applications (though its likely rare). For these cases, 
Ruler tries its best to maintain the backward compatibility. Only when the 3 conditions mentioned above, will 
ruler change behaviour because it assumes your rule really wanted an OR and was mis-configured until today. For example, 
the rule below will keep working as normal rule with treating "$or" as normal field name in the rule and event:
```javascript
{
    "source": [ "aws.cloudwatch" ],
    "$or": {
        "metricType": [ "MetricType" ] , 
        "namespace": [ "AWS/EC2", "AWS/ES" ]
    }
}
```
Refer to `/src/test/data/normalRulesWithOrWording.json` for more examples that "$or" is parsed as normal field name by Ruler.

#### Caveat
The keyword "$or" as "Or" relationship primitive should not be designed as normal field in both Events and Rules.
Ruler supports the legacy rules where "$or" is parsed as normal field name to keep backward
compatibility and give time for team to migrate their legacy "$or" usage away from their events and rules as normal filed name. 
Mix usage of "$or" as "Or" primitive, and "$or" as normal field name is not supported
intentionally by Ruler to avoid the super awkward ambiguities on "$or" from occurring.

How to use Ruler
================

There are two ways to use Ruler.  You can compile multiple rules
into a "Machine", and then use either of its `rulesForEvent()` method
or `rulesForJSONEvent()` methods to check  which of the rules match any Event.
The difference between these two methods is discussed below.  This discussion
will use `rulesForEvent()` generically except where the difference matters.

Alternatively, you can use a single static boolean method to determine
whether an individual event matches a particular rule.

## Static Rule Matching

There is a single static boolean method `Ruler.matchesRule(event, rule)` -
both arguments are provided as JSON strings. 

NOTE: There is another deprecated method called `Ruler.matches(event, rule)`which 
should not be used as its results are inconsistent with `rulesForJSONEvent()` and 
`rulesForEvent()`

## Matching with a Machine

The matching time does not depend on the number of rules.  This is the best choice
if you have multiple possible rules you want to select from, and especially
if you have a way to store the compiled Machine.

The matching time is impacted by the degree of non-determinism introduced by wildcard rules. Performance deteriorates as
an increasing number of the wildcard rule prefixes match a theoretical worst-case event. To avoid this, wildcard rules
pertaining to the same event field should avoid common prefixes leading up to their first wildcard character. If a
common prefix is required, then use the minimum number of wildcard characters and limit repeating character sequences
that occur following a wildcard character. MachineComplexityEvaluator can be used to evaluate a machine and determine
the degree of non-determinism, or "complexity" (i.e. how many wildcard rule prefixes match a theoretical worst-case
event). Here are some data points showing a typical decrease in performance for increasing complexity scores.

- Complexity = 1, Events per Second = 140,000
- Complexity = 17, Events per Second = 12,500
- Complexity = 34, Events per Second = 3500
- Complexity = 50, Events per Second = 2500
- Complexity = 100, Events per Second = 1250
- Complexity = 275, Events per Second = 100 (extrapolated data point)
- Complexity = 650, Events per Second = 10 (extrapolated data point)

The main class you'll interact with implements state-machine based rule
matching.  The interesting methods are:

* `addRule()` - adds a new rule to the machine
* `deleteRule()` - deletes a rule from the machine
* `rulesForEvent()`/`rulesForJSONEvent()` - finds the rules in the machine that match an event

There are two flavors: `Machine` and `GenericMachine<T>`.  Machine is simply `GenericMachine<String>`.  The
API refers to the generic type as "name", which reflects history: The String version was built first and
the strings it stored and returned were thought of as rule names.

For safety, the type used to "name" rules should be immutable. If you change the content of an object while
it's being used as a rule name, this may break the operation of Ruler.

### addRule()

All forms of this method have the same first argument, a String which provides
the name of the Rule and is returned by `rulesForEvent()`.  The rest of the
arguments provide the name/value pairs.  They may be provided in JSON as in
the examples above (via a String, a Reader, an InputStream, or `byte[]`), or as
a `Map<String, List<String>>`, where the keys are the field names and the
values are the list of possible matches; using the example above, there would
be a key named `detail.state` whose value would be the list containing
`"initializing"` and `"running"`.

Note: This method (and also `deleteRule()`) is synchronized, so only one thread
may be updating the machine at any point in time.

#### Rules and rule names

You can call `addRule()` multiple times with the same name but multiple different
name/value patterns, thus  achieving an "or" relationship;
`rulesForEvent()` will return that name if any of the patterns match.

For example, suppose you call `addRule()` with rule name as "R1" and add
the following pattern:
```javascript
{
  "detail": {
    "c-count": [ { "numeric": [ ">", 0, "<=", 5 ] } ]
  }
}
```
Then you call it again with the same name but a different pattern:

```javascript
{
  "detail": {
    "x-limit": [ { "numeric": [ "=", 3.018e2 ] } ]
  }
}
```
After this, `rulesForEvent()` will return "R1" for **either** a `c-count` value of 2
**or** an `x-limit` value of 301.8.

### deleteRule()

This is a mirror-image of `addRule()`; in each case the first argument is the rule
name, given as a String.  Subsequent arguments provide the names and values,
and may be given in any of the same ways as with `addRule()`.

Note: This method (and also `addRule()`) is synchronized, so only one thread may
be updating the machine at any point in time.

The operation of this API can be subtle.  The Machine compiles the mapping
of name/value patterns to Rule names into a finite automaton, but does not
remember what patterns are mapped to a given Rule name. Thus, there is no
requirement that the pattern in a `deleteRule()` exactly match that in the
corresponding `addRule()`.  Ruler will look for matches to the name/value patterns
and see if they give a match to a rule with the provided name, and if so
remove them. Bear in mind that while performing `deleteRule()` calls that do not exactly
match the corresponding `addRule()` calls will not fail and will not leave the
machine in an inconsistent state, they may cause "garbage" to build up in the
Machine.

A specific consequence is that if you have called `addRule()` multiple times with
the same name but different patterns, as illustrated above in the *Rules and rule
names* section, you would have to call `deleteRule()` the same number of times,
with the same associated patterns, to remove all references to that rule name
from the machine.

### rulesForEvent() / rulesForJSONEvent()

This method returns a `List<String>` for Machine (and `List<T>` for GenericMachine) which contains
the names of the rules that match the provided event.  The event may be provided to either method
as a single `String` representing its JSON form.

The event may also be provided to `rulesForEvent()` as a collection of strings which alternate field
names and values, and must be sorted lexically by field-name.  This may be a `List<String>` or `String[]`.

Providing the event in JSON is the recommended approach and has several advantages. First of all,
populating the String list or array with alternating name/value quantities, in an order sorted by name,
is tricky, and Ruler doesn't help, just fails to work correctly if the list is improperly structured.  Adding
to the difficulty, the representation of field values, provided as strings, must follow JSON-syntax
rules - see below under *JSON text matching*.

Finally, the list/array version of an event makes it impossible for Ruler to recognize array
structures and provide array-consistent matching, described below in this document. The
`rulesForEvent(String eventJSON)` API is deprecated in favor of `rulesForJSONEvent()`
specifically because it does not support array-consistent matching.

`rulesForJSONEvent()` also has the advantage that the code which turns the JSON form
of the event into a sorted list has been extensively profiled and optimized.

The performance of `rulesForEvent()` and `rulesForJSONEvent()` do not depend on the number of rules added
with `addRule()`.  `rulesForJSONEvent()` is generally faster because of the optimized
event processing. If you do your own event processing and call `rulesForEvent()`
with a pre-sorted list of name and values, that is faster still; but you may not
be able to do the field-list preparation as fast as `rulesForJSONEvent()` does.

### The Patterns API

If you think of your events as name/value pairs rather than nested JSON-style
documents, the `Patterns` class (and its `Range` subclass) may be useful in constructing rules.  The following
static methods are useful.

```java
public static ValuePatterns exactMatch(final String value);
public static ValuePatterns prefixMatch(final String prefix);
public static ValuePatterns suffixMatch(final String suffix);
public static ValuePatterns equalsIgnoreCaseMatch(final String value);
public static ValuePatterns wildcardMatch(final String value);
public static AnythingBut anythingButMatch(final String anythingBut);
public static AnythingBut anythingButPrefix(final String prefix);
public static ValuePatterns numericEquals(final double val);
public static Range lessThan(final double val);
public static Range lessThanOrEqualTo(final double val);
public static Range greaterThan(final double val);
public static Range greaterThanOrEqualTo(final double val);
public static Range between(final double bottom, final boolean openBottom, final double top, final boolean openTop);
```

Once you have constructed appropriate `Patterns` matchers with these methods, you can use the
following form of `Machine.addRule()` to add them to your machine:

```java
public void addPatternRule(final String name, final Map<String, List<Patterns>> namevals);
```

## JSON text matching

The field values in rules must be provided in their JSON representations.
That is to say, string values must be enclosed in "quotes". Unquoted values
are allowed, such as numbers (`-3.0e5`) and certain JSON-specific literals (`true`,
`false`, and `null`).

This can be entirely ignored if rules are provided to `addRule()`() in JSON form,
or if you are working with Patterns as opposed to literal strings.
But if you are providing rules as name/value pairs, and you want to specify
that the field "xyz" matches the string "true", that has to be expressed as
`"xyz", "\"true\""`.  On the other hand, `"xyz", "true"` would match only the
JSON literal `true`.

## JSON Array Matching

Ruler supports rule-matching for events containing arrays, but only when the event
is provided in JSON form - when it's a list of pre-sorted fields, the array structure
in the event is lost.  The behavior also depends on whether you use `rulesForEvent()`
or `rulesForJSONEvent`.

Consider the following Event.


```javascript
{
  "employees":[
    { "firstName":"John", "lastName":"Doe" },
    { "firstName":"Anna", "lastName":"Smith" },
    { "firstName":"Peter", "lastName":"Jones" }
  ]
}
```

Then this rule will match:

```javascript
{ "employees": { "firstName": [ "Anna" ] } }
```

That is to say, the array structure is "crushed out" of the rule pattern,
and any contained objects are treated as if they are the value of the
parent field.  This works for multi-level arrays too:

```javascript
{
  "employees":[
    [
      { "firstName":"John", "lastName":"Doe" },
      { "firstName":"Anna", "lastName":"Smith" }
    ],
    [
      { "firstName":"Peter", "lastName":"Jones" }
    ]
  ]
}
```

In earlier versions of Ruler, the only Machine-based matching method
was `rulesForEvent()` which unfortunately will also match the following rule:

```javascript
{ "employees": { "firstName": [ "Anna" ], "lastName": [ "Jones" ] } }
```

As a fix, Ruler introduced `rulesForJSONEvent()` which, as the name suggests, only
matches events provided in JSON form. `rulesForJsonEvent()` will *not* match the
"Anna"/"Jones" rule above.

Formally: `rulesForJSONEvent()` will refuse to recognize any match in which
any two fields are within JSON objects that are in different elements of the same array.
In practice, this means that it does about what you would expect.

## Compiling and checking rules

There is a supporting class `com.amazon.fsm.ruler.RuleCompiler`.  It contains a
method named `check()` which accepts a JSON rule definition and returns a
String value which, if null, means that the rule was syntactically valid.  If
the return value is non-Null it contains a human-readable error message
describing the problem.

For convenience, it also contains a method named `compile()` which works just
like `check()` but signals an error by throwing an IOException and, on
success, returns a `Map<String>, List<String>>` in the form that Machine's
`addRule()` method expects. Since the Machine class uses this internally,
this method may be a time-saver.

#### Caveat: Compiled rules and JSON keys with dots

When Ruler compiles keys, it uses dot (`.`) as the joining character. This means 
it will compile the following two rules to the same internal representation 

```javascript
## has no dots in keys
{ "detail" : { "state": { "status": [ "running" ] } } }

## has dots in keys
{ "detail" : { "state.status": [ "running" ] } }
```

It also means that these rules will match against following two events : 

```javascript
## has no dots in keys
{ "detail" : { "state": { "status": "running" } } }

## has dots in keys
{ "detail" : { "state.status": "running"  } }
```

This behaviour may change in future version (to avoid any confusions) and should not be relied upon.

## Performance

We measure Ruler's performance by compiling multiple rules into a Machine and matching events provided as JSON strings.

A benchmark which processes 213,068 JSON events with average size about 900 bytes against 5 each exact-match,
prefix-match, suffix-match, equals-ignore-case-match, wildcard-match, numeric-match, and anything-but-match rules and
counts the matches, yields the following on a 2019 MacBook:

Events are processed at over 220K/second except for:
 - equals-ignore-case matches, which are processed at over 200K/second.
 - wildcard matches, which are processed at over 170K/second.
 - anything-but matches, which are processed at over 150K/second.
 - numeric matches, which are processed at over 120K/second.
 - complex array matches, which are processed at over 2.5K/second.

### Suggestions for better performance

Here are some suggestions on processing rules and events:
1. If your team is still using old API -- rulesForEvent, switch to rulesForJSONEvent API. Due to limited resource, old API will not be maintained well thought contributions are always welcomed.
2. If your team does event flattening by yourself,  you are recommended to use Ruler to flatten the event, just pass Json string or Json node. We have many optimizations within Ruler parsing code.
3. if your team does Rule Json parsing by yourself, you are recommended to just pass the Json described rule string directly to Ruler, in which will do some pre-processing, e.g. add “”.
4. In order to well protect the system and prevent ruler from hitting worse condition, limit number of fields in event and rule, e.g. for big event, consider to split to multiple small event and call ruler multiple times. while number of rule is purely depending on your memory budget which is up to you to decide that, but number of fields described in the rule is most important and sensitive on performance, if possible, try to design it as small as possible.

From performance consideration, Ruler is sensitive on below items, so, when you design the schema of your even and rule, here are some suggestions:
1. Try to make Key be diverse both in event and rules, the more heterogeneous fields in event and rule, the higher performance.
2. Shorten number of fields inside rules, the less key in the rules, the short path to find them out.
3. Shorten number of fields inside event,  the less key inside event, the less attempts will be required to find out rules.
4. Shorten number of possible value in […](e.g. “a”:[1,2,3 …100] ) both inside event and rules, the more value, the more branches produced in FSM to iterator, then the more time takes for matching.


## Security

See [CONTRIBUTING](CONTRIBUTING.md#security-issue-notifications) for more information.

## License

This project is licensed under the Apache-2.0 License. See [LICENSE](LICENSE) for more information.


