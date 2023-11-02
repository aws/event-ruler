package software.amazon.event.ruler;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ConfigurationTest {

    @Test
    public void testAdditionalNameStateReuseTrue() {
        assertTrue(new Configuration.Builder().withAdditionalNameStateReuse(true).build().isAdditionalNameStateReuse());
    }

    @Test
    public void testAdditionalNameStateReuseFalse() {
        assertFalse(new Configuration.Builder().withAdditionalNameStateReuse(false).build().isAdditionalNameStateReuse());
    }
}
