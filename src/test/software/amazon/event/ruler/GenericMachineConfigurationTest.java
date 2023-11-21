package software.amazon.event.ruler;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class GenericMachineConfigurationTest {

    @Test
    public void testAdditionalNameStateReuseTrue() {
        assertTrue(new GenericMachineConfiguration(true).isAdditionalNameStateReuse());
    }

    @Test
    public void testAdditionalNameStateReuseFalse() {
        assertFalse(new GenericMachineConfiguration(false).isAdditionalNameStateReuse());
    }
}
