package io.toterra.subterra.api.net;

import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.regex.Pattern;

/**
 * L3 API library (p.1.3): network helpers for domain mods.
 * Pure Java — {@link #httpGet} uses the standard HTTP client with a caller
 * timeout and never throws on transport failure (status = -1 + message).
 */
public final class NetApi {

    private static final Pattern IPV4 = Pattern.compile("(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})");

    /** Result of a best-effort HTTP GET; status &lt; 0 means transport failure. */
    public record HttpResult(int status, String body) {
    }

    private NetApi() {
    }

    /** Strict dotted-quad IPv4 validation (each octet 0-255, no leading zeros). */
    public static boolean isValidIpv4(String address) {
        if (address == null) {
            return false;
        }
        var m = IPV4.matcher(address);
        if (!m.matches()) {
            return false;
        }
        for (int i = 1; i <= 4; i++) {
            String octet = m.group(i);
            if (octet.length() > 1 && octet.charAt(0) == '0') {
                return false; // no leading zeros
            }
            if (Integer.parseInt(octet) > 255) {
                return false;
            }
        }
        return true;
    }

    /** Valid UDP/TCP bound port (1-65535; 0 is reserved). */
    public static boolean isValidPort(int port) {
        return port >= 1 && port <= 65535;
    }

    /** application/x-www-form-urlencoded encode (UTF-8). */
    public static String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    /** application/x-www-form-urlencoded decode (UTF-8). */
    public static String urlDecode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }

    /**
     * Best-effort HTTP GET with a hard caller timeout. Transport failures
     * (DNS, connect, read timeout) return status -1 with the failure message
     * in the body; the caller decides. Never throws.
     */
    public static HttpResult httpGet(String url, int timeoutMillis) {
        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofMillis(Math.max(1000, timeoutMillis)))
                    .followRedirects(HttpClient.Redirect.NORMAL)
                    .build();
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofMillis(Math.max(1000, timeoutMillis)))
                    .GET()
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            return new HttpResult(response.statusCode(), response.body());
        } catch (Exception e) {
            return new HttpResult(-1, e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }
}