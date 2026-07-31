package com.netcracker.core.declarative.client.reconciler;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.quarkus.test.junit.QuarkusTestProfile;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import okhttp3.OkHttpClient;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

@QuarkusTest
@TestProfile(ConfigurationHttpClientTimeoutsTest.CustomTimeoutsProfile.class)
class ConfigurationHttpClientTimeoutsTest {

    public static class CustomTimeoutsProfile implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of(
                    "cloud.http-client.connect-timeout", "3s",
                    "cloud.http-client.read-timeout", "7s"
            );
        }
    }

    @Inject
    @Named("meshHttpClient")
    OkHttpClient meshHttpClient;

    @Test
    void timeoutsAreTakenFromConfig() {
        assertEquals(3000, meshHttpClient.connectTimeoutMillis());
        assertEquals(7000, meshHttpClient.readTimeoutMillis());
    }
}
