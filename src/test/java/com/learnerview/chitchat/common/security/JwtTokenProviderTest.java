package com.learnerview.chitchat.common.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtTokenProviderTest {

    private JwtTokenProvider tokenProvider;

    @BeforeEach
    void setUp() {
        tokenProvider = new JwtTokenProvider();
    }

    @Test
    void refusesToStartWhenExternalAuthIsEnabledWithoutSignatureVerification() {
        ReflectionTestUtils.setField(tokenProvider, "externalAuthEnabled", true);
        ReflectionTestUtils.setField(tokenProvider, "externalVerifySignature", false);

        assertThatThrownBy(tokenProvider::validateExternalAuthConfiguration)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("verifySignature");
    }

    @Test
    void allowsDisabledSignatureVerificationWhenExternalAuthIsDisabled() {
        ReflectionTestUtils.setField(tokenProvider, "externalAuthEnabled", false);
        ReflectionTestUtils.setField(tokenProvider, "externalVerifySignature", false);

        assertThatCode(tokenProvider::validateExternalAuthConfiguration).doesNotThrowAnyException();
    }

    @Test
    void allowsExternalAuthWithSignatureVerification() {
        ReflectionTestUtils.setField(tokenProvider, "externalAuthEnabled", true);
        ReflectionTestUtils.setField(tokenProvider, "externalVerifySignature", true);

        assertThatCode(tokenProvider::validateExternalAuthConfiguration).doesNotThrowAnyException();
    }

    @Test
    void externalTokensAreRejectedWhenSignatureVerificationIsDisabled() {
        ReflectionTestUtils.setField(tokenProvider, "externalAuthEnabled", true);
        ReflectionTestUtils.setField(tokenProvider, "externalVerifySignature", false);

        // header.payload.signature with an unsigned/tampered payload must not be trusted.
        String forged = "eyJhbGciOiJub25lIn0.eyJzdWIiOiJhdHRhY2tlciIsInByZWZlcnJlZF91c2VybmFtZSI6ImF0dGFja2VyIn0.";
        assertThat(tokenProvider.validateExternalToken(forged)).isFalse();
    }
}
