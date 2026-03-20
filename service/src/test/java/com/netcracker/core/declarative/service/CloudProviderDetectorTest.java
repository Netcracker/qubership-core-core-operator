package com.netcracker.core.declarative.service;

import com.netcracker.core.declarative.service.composite.model.CloudProvider;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.net.UnknownHostException;

import static com.netcracker.core.declarative.service.composite.model.CloudProvider.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class CloudProviderDetectorTest {

    private static final String GKE_DNS_PROBE = "metadata.google.internal";
    private static final String EKS_DNS_PROBE = "ec2.internal";
    private static final String AKS_DNS_PROBE = "aks-metadata.azure.com";

    @Test
    void detectsGke() throws Exception {
        try (var inet = mockStatic(InetAddress.class)) {
            inet.when(() -> InetAddress.getByName(GKE_DNS_PROBE)).thenReturn(mock(InetAddress.class));

            assertEquals(GKE, detect());
        }
    }

    @Test
    void detectsEks() throws Exception {
        try (var inet = mockStatic(InetAddress.class)) {
            inet.when(() -> InetAddress.getByName(GKE_DNS_PROBE)).thenThrow(new UnknownHostException());
            inet.when(() -> InetAddress.getByName(EKS_DNS_PROBE)).thenReturn(mock(InetAddress.class));

            assertEquals(EKS, detect());
        }
    }

    @Test
    void detectsAks() throws Exception {
        try (var inet = mockStatic(InetAddress.class)) {
            inet.when(() -> InetAddress.getByName(GKE_DNS_PROBE)).thenThrow(new UnknownHostException());
            inet.when(() -> InetAddress.getByName(EKS_DNS_PROBE)).thenThrow(new UnknownHostException());
            inet.when(() -> InetAddress.getByName(AKS_DNS_PROBE)).thenReturn(mock(InetAddress.class));

            assertEquals(AKS, detect());
        }
    }

    @Test
    void detectsOnPremWhenAllProbesFail() throws Exception {
        try (var inet = mockStatic(InetAddress.class)) {
            inet.when(() -> InetAddress.getByName(any())).thenThrow(new UnknownHostException());

            assertEquals(OnPrem, detect());
        }
    }

    // -- helpers --

    private CloudProvider detect() {
        CloudProviderDetector detector = new CloudProviderDetector();
        detector.init();
        return detector.getCloudProvider();
    }
}