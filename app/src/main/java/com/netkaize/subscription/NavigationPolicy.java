package com.netkaize.subscription;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;

final class NavigationPolicy {
    private static final String APP_HOST = "subscription.netkaize.com";

    private NavigationPolicy() {
    }

    static boolean shouldStayInApp(String url) {
        if (url == null || url.isBlank()) {
            return false;
        }
        try {
            URI uri = new URI(url);
            String scheme = uri.getScheme();
            String host = uri.getHost();
            return "https".equalsIgnoreCase(scheme)
                    && host != null
                    && APP_HOST.equals(host.toLowerCase(Locale.ROOT));
        } catch (URISyntaxException exception) {
            return false;
        }
    }
}
