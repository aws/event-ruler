package software.amazon.event.ruler.input;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class ParseExceptionTest {

    @Test
    public void testGetMessage() {
        String msg = "kaboom";
        ParseException e = new ParseException(msg);
        assertEquals(msg, e.getMessage());
    }
}
