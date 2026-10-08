package com.learnerview.chitchat.webhook;

import com.learnerview.chitchat.common.error.ApiException;
import com.learnerview.chitchat.common.error.ErrorCode;

import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;

/**
 * SSRF guard for outbound webhook URLs. Validated at registration AND again at
 * delivery time (DNS answers can change between the two).
 */
public final class WebhookUrlValidator {

    private WebhookUrlValidator() {
    }

    public static URI validate(String rawUrl) {
        if (rawUrl == null || rawUrl.isBlank()) {
            throw validation("Webhook URL is required");
        }
        URI uri;
        try {
            uri = new URI(rawUrl.trim());
        } catch (URISyntaxException ex) {
            throw validation("Webhook URL is not a valid URI");
        }

        String scheme = uri.getScheme();
        if (scheme == null || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))) {
            throw validation("Webhook URL must use http or https");
        }
        if (uri.getUserInfo() != null) {
            throw validation("Webhook URL must not contain credentials");
        }
        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw validation("Webhook URL must include a host");
        }

        InetAddress[] addresses;
        try {
            addresses = InetAddress.getAllByName(host);
        } catch (UnknownHostException ex) {
            throw validation("Webhook URL host cannot be resolved");
        }
        if (addresses.length == 0) {
            throw validation("Webhook URL host cannot be resolved");
        }
        for (InetAddress address : addresses) {
            if (isBlocked(address)) {
                throw validation("Webhook URL must resolve to a public address");
            }
        }
        return uri;
    }

    private static boolean isBlocked(InetAddress address) {
        if (address.isAnyLocalAddress() || address.isLoopbackAddress()
                || address.isLinkLocalAddress() || address.isSiteLocalAddress()
                || address.isMulticastAddress()) {
            return true;
        }
        byte[] bytes = address.getAddress();
        if (bytes.length == 4) {
            int first = bytes[0] & 0xFF;
            int second = bytes[1] & 0xFF;
            if (first == 0) {
                return true; // 0.0.0.0/8 "this network"
            }
            if (first == 100 && second >= 64 && second <= 127) {
                return true; // CGNAT 100.64.0.0/10
            }
            if (first == 192 && second == 0 && (bytes[2] & 0xFF) == 0) {
                return true; // 192.0.0.0/24 IETF protocol assignments
            }
            if (first == 198 && (second == 18 || second == 19)) {
                return true; // benchmarking 198.18.0.0/15
            }
            return first >= 224; // multicast + reserved
        }
        // IPv6: fc00::/7 unique-local
        return (bytes[0] & 0xFE) == 0xFC;
    }

    private static ApiException validation(String message) {
        return new ApiException(ErrorCode.VALIDATION_FAILED, message);
    }
}
