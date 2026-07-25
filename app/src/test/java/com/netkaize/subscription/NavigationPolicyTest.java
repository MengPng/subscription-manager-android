package com.netkaize.subscription;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class NavigationPolicyTest {
    @Test
    public void keepsOnlyProductionSubscriptionPagesInsideTheApp() {
        assertTrue(NavigationPolicy.shouldStayInApp("https://subscription.netkaize.com/"));
        assertTrue(NavigationPolicy.shouldStayInApp("https://subscription.netkaize.com/profile"));

        assertFalse(NavigationPolicy.shouldStayInApp("http://subscription.netkaize.com/"));
        assertFalse(NavigationPolicy.shouldStayInApp("https://admin.netkaize.com/"));
        assertFalse(NavigationPolicy.shouldStayInApp("https://example.com/"));
        assertFalse(NavigationPolicy.shouldStayInApp("not a url"));
        assertFalse(NavigationPolicy.shouldStayInApp(null));
    }
}
