package com.learnerview.chitchat.webhook;

import com.learnerview.chitchat.common.error.ApiException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WebhookUrlValidatorTest {

    @Test
    void acceptsAPublicHttpsEndpoint() {
        // Literal public IP - no DNS lookup involved.
        assertThat(WebhookUrlValidator.validate("https://93.184.216.34/hooks/incoming").getHost())
                .isEqualTo("93.184.216.34");
    }

    @Test
    void rejectsLocalhostNamesAndLoopbackAddresses() {
        assertBlocked("http://localhost/hook");
        assertBlocked("http://127.0.0.1/hook");
        assertBlocked("http://[::1]/hook");
        assertBlocked("http://0.0.0.0/hook");
    }

    @Test
    void rejectsPrivateAndLinkLocalAddresses() {
        assertBlocked("http://10.0.0.5/hook");
        assertBlocked("http://192.168.1.10/hook");
        assertBlocked("http://172.16.0.2/hook");
        assertBlocked("http://169.254.169.254/latest/meta-data/");
    }

    @Test
    void rejectsCarrierGradeNatAndReservedRanges() {
        assertBlocked("http://100.64.0.1/hook");
        assertBlocked("http://100.127.255.254/hook");
        assertBlocked("http://198.18.0.1/hook");
        assertBlocked("http://224.0.0.1/hook");
    }

    @Test
    void rejectsNonHttpSchemesAndMissingHosts() {
        assertBlocked("ftp://93.184.216.34/hook");
        assertBlocked("file:///etc/passwd");
        assertBlocked("https:///no-host");
        assertBlocked("not a uri at all ::");
        assertBlocked("");
    }

    @Test
    void rejectsEmbeddedCredentials() {
        assertBlocked("https://user:pass@93.184.216.34/hook");
    }

    private void assertBlocked(String url) {
        assertThatThrownBy(() -> WebhookUrlValidator.validate(url))
                .isInstanceOf(ApiException.class);
    }
}
