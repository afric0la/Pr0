package com.pr0gramm.app;

import android.content.SharedPreferences;
import android.util.Log;

import com.google.common.base.Optional;

import java.net.CookieHandler;
import java.net.HttpCookie;
import java.net.URI;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;
import javax.inject.Singleton;

/**
 */
@Singleton
public class LoginCookieHandler extends CookieHandler {
    private static final String PREF_LOGIN_COOKIE = "LoginCookieHandler.cookieValue";

    private final Object lock = new Object();
    private final SharedPreferences preferences;
    private HttpCookie loginCookie;

    @Inject
    public LoginCookieHandler(SharedPreferences preferences) {
        this.preferences = preferences;

        String restored = preferences.getString(PREF_LOGIN_COOKIE, null);
        if (restored != null) {
            Log.i("LoginCookieHandler", "restoring cookie value from prefs");
            setLoginCookie(restored);
        }
    }

    @Override
    public Map<String, List<String>> get(URI uri, Map<String, List<String>> requestHeaders) {
        if (!isApiRequest(uri))
            return requestHeaders;

        if (loginCookie == null)
            return Collections.emptyMap();

        Log.i("LoginCookieHandler", "Add login cookie to request");
        return cookiesToHeaders(Collections.singletonList(loginCookie));
    }

    @Override
    public void put(URI uri, Map<String, List<String>> responseHeaders) {
        if (!isApiRequest(uri)) return;

        for (Map.Entry<String, List<String>> entry : responseHeaders.entrySet()) {
            if (!"Set-Cookie".equalsIgnoreCase(entry.getKey()))
                continue;

            List<String> values = entry.getValue();
            for (String value : values) {
                List<HttpCookie> cookies;
                try {
                    cookies = HttpCookie.parse(value);
                } catch (IllegalArgumentException ignored) {
                    Log.d("LoginCookieHandler", "invalid cookie format");
                    continue;
                }

                for (HttpCookie cookie : cookies)
                    handleCookie(cookie);
            }
        }
    }

    private boolean isApiRequest(URI uri) {
        return uri.getHost().equalsIgnoreCase("pr0gramm.com");
    }

    private void handleCookie(HttpCookie cookie) {
        if ("me".equals(cookie.getName())) {
            Log.i("LoginCookieHandler", "Got login cookie");
            storeLoginCookie(cookie);
        }
    }

    private void storeLoginCookie(HttpCookie cookie) {
        synchronized (lock) {
            loginCookie = cookie;

            // store cookie for next time
            preferences.edit()
                    .putString(PREF_LOGIN_COOKIE, cookie.getValue())
                    .apply();
        }
    }

    public Optional<String> getLoginCookie() {
        return Optional.fromNullable(loginCookie != null ? loginCookie.getValue() : null);
    }

    public void setLoginCookie(String value) {
        storeLoginCookie(new HttpCookie("me", value));
    }

    public void clearLoginCookie() {
        synchronized (lock) {
            loginCookie = null;
            preferences.edit().remove(PREF_LOGIN_COOKIE).apply();
        }
    }

    /**
     * Got this one from {@link java.net.CookieManager}
     *
     * @param cookies The cookies to encode.
     * @return A map that can be returned from
     * {@link java.net.CookieHandler#get(java.net.URI, java.util.Map)}.
     */
    private static Map<String, List<String>> cookiesToHeaders(List<HttpCookie> cookies) {
        if (cookies.isEmpty()) {
            return Collections.emptyMap();
        }

        StringBuilder result = new StringBuilder();

        // If all cookies are version 1, add a version 1 header. No header for version 0 cookies.
        int minVersion = 1;
        for (HttpCookie cookie : cookies) {
            minVersion = Math.min(minVersion, cookie.getVersion());
        }
        if (minVersion == 1) {
            result.append("$Version=\"1\"; ");
        }

        result.append(cookies.get(0).toString());
        for (int i = 1; i < cookies.size(); i++) {
            result.append("; ").append(cookies.get(i).toString());
        }

        return Collections.singletonMap("Cookie", Collections.singletonList(result.toString()));
    }
}
