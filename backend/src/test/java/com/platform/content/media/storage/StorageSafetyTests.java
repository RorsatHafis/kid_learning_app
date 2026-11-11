package com.platform.content.media.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.InetAddress;
import java.util.Base64;
import org.junit.jupiter.api.Test;

/** M11: a school's storage address can't reach the platform's network, and a sealed key opens only where it was sealed. */
class StorageSafetyTests {

    @Test
    void anAddressOnThePlatformsNetworkIsRefused() throws Exception {
        for (String internal : new String[] {"127.0.0.1", "10.1.2.3", "172.16.0.9", "192.168.1.1", "169.254.169.254", "100.64.0.1",
                "0.0.0.0", "::1", "fd00::1", "fe80::1"}) {
            assertThat(EndpointPolicy.internal(InetAddress.getByName(internal))).as(internal).isTrue();
        }
        assertThat(EndpointPolicy.internal(InetAddress.getByName("8.8.8.8"))).isFalse();
        assertThatThrownBy(() -> EndpointPolicy.check("https://127.0.0.1", false)).hasMessageContaining("private network");
        assertThatThrownBy(() -> EndpointPolicy.check("https://localhost", false)).hasMessageContaining("private network");
        assertThatThrownBy(() -> EndpointPolicy.check("https://169.254.169.254", false)).hasMessageContaining("private network");
    }

    @Test
    void onlyAPlainHttpsAddressOnAUsualPortIsAccepted() {
        assertThatThrownBy(() -> EndpointPolicy.check("http://8.8.8.8", false)).hasMessageContaining("https://");
        assertThatThrownBy(() -> EndpointPolicy.check("https://8.8.8.8:22", false)).hasMessageContaining("port");
        assertThatThrownBy(() -> EndpointPolicy.check("https://user@8.8.8.8", false)).hasMessageContaining("only the storage address");
        assertThatThrownBy(() -> EndpointPolicy.check("https://8.8.8.8/bucket", false)).hasMessageContaining("only the storage address");
        assertThatThrownBy(() -> EndpointPolicy.check("ftp://8.8.8.8", false)).hasMessageContaining("https://");
        assertThat(EndpointPolicy.check("https://8.8.8.8/", false).toString()).isEqualTo("https://8.8.8.8");
        // Local and test only: a server on this machine.
        assertThat(EndpointPolicy.check("http://127.0.0.1:9000", true).toString()).isEqualTo("http://127.0.0.1:9000");
    }

    @Test
    void aSealedKeyOpensOnlyForItsConnectionAndOnlyWithItsKey() {
        StorageSeal seal = new StorageSeal(new StorageProperties(key(), false));
        String sealed = seal.seal("the-access-key", "school|endpoint|bucket|id");
        assertThat(sealed).doesNotContain("the-access-key");
        assertThat(seal.open(sealed, "school|endpoint|bucket|id")).isEqualTo("the-access-key");
        assertThatThrownBy(() -> seal.open(sealed, "other|endpoint|bucket|id")).isInstanceOf(IllegalStateException.class);
        byte[] bytes = Base64.getDecoder().decode(sealed);
        bytes[bytes.length - 1] ^= 1;
        assertThatThrownBy(() -> seal.open(Base64.getEncoder().encodeToString(bytes), "school|endpoint|bucket|id"))
                .isInstanceOf(IllegalStateException.class);
        StorageSeal another = new StorageSeal(new StorageProperties(key(), false));
        assertThatThrownBy(() -> another.open(sealed, "school|endpoint|bucket|id")).isInstanceOf(IllegalStateException.class);
        // Without a key, own storage isn't offered at all.
        StorageSeal none = new StorageSeal(new StorageProperties(null, false));
        assertThat(none.available()).isFalse();
        assertThatThrownBy(() -> none.seal("x", "y")).hasMessageContaining("isn't set up");
    }

    private static String key() {
        byte[] bytes = new byte[32];
        new java.security.SecureRandom().nextBytes(bytes);
        return Base64.getEncoder().encodeToString(bytes);
    }
}
