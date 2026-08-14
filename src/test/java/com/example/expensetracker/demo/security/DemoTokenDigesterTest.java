package com.example.expensetracker.demo.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import java.util.Base64;

class DemoTokenDigesterTest {

    @Test
    void producesTheKnownLowercaseHmacSha256Digest() {
        DemoTokenDigester digester = new DemoTokenDigester(
            "0123456789abcdef0123456789abcdef"
        );

        assertThat(digester.digest("dmo_example-token"))
            .isEqualTo("b619d3d7f9c824cb97dada1b91fac5686609ae67a64f4308efa66baf7c272efb");
    }

    @Test
    void differentKeysProduceDifferentDigests() {
        String token = "dmo_example-token";

        assertThat(new DemoTokenDigester("0123456789abcdef0123456789abcdef").digest(token))
            .isNotEqualTo(new DemoTokenDigester("abcdef0123456789abcdef0123456789").digest(token));
    }

    @Test
    void matchesOnlyTheRawTokenForTheExpectedDigest() {
        DemoTokenDigester digester = new DemoTokenDigester(
            "0123456789abcdef0123456789abcdef"
        );
        String expectedDigest = "b619d3d7f9c824cb97dada1b91fac5686609ae67a64f4308efa66baf7c272efb";

        assertThat(digester.matches("dmo_example-token", expectedDigest)).isTrue();
        assertThat(digester.matches("dmo_other-token", expectedDigest)).isFalse();
    }

    @Test
    void generatesPrefixedAccessTokensWithThirtyTwoRandomBytes() {
        DemoTokenDigester digester = new DemoTokenDigester(
            "0123456789abcdef0123456789abcdef"
        );

        String first = digester.generateAccessToken();
        String second = digester.generateAccessToken();

        assertThat(first).startsWith("dmo_");
        assertThat(Base64.getUrlDecoder().decode(first.substring(4))).hasSize(32);
        assertThat(second).isNotEqualTo(first);
    }

    @Test
    void rejectsKeysShorterThanThirtyTwoBytes() {
        assertThatThrownBy(() -> new DemoTokenDigester("too-short"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("32 bytes");
    }
}
