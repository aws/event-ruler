package software.amazon.event.ruler.input;

/**
 * A RuntimeException that indicates an error parsing a rule's value.
 */
public class ParseException extends RuntimeException {

    public ParseException(String msg) {
        super(msg);
    }

}
