package com.netcracker.core.declarative.service;

import com.netcracker.core.declarative.service.composite.model.CloudProvider;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.net.InetAddress;
import java.net.UnknownHostException;

@Slf4j
@Getter
@ApplicationScoped
public class CloudProviderDetector {
    protected static final String GKE_DNS_PROBE = "metadata.google.internal.";
    protected static final String EKS_DNS_PROBE = "ec2.internal.";
    protected static final String AKS_DNS_PROBE = "aks-metadata.azure.com";

    private CloudProvider cloudProvider;

    @PostConstruct
    void init() {
        cloudProvider = detect();
        log.info("Detected cloud provider: {}", cloudProvider);
    }

    private CloudProvider detect() {
        if (isGke()) {
            return CloudProvider.GKE;
        }
        if (isEks()) {
            return CloudProvider.EKS;
        }
        if (isAks()) {
            return CloudProvider.AKS;
        }

        return CloudProvider.OnPrem;
    }

    private boolean isGke() {
        try {
            InetAddress.getByName(GKE_DNS_PROBE);
            log.debug("GKE detected via DNS resolution of {}", GKE_DNS_PROBE);
            return true;
        } catch (UnknownHostException e) {
            log.debug("GKE DNS probe failed for {}", GKE_DNS_PROBE);
            return false;
        }
    }

    private boolean isEks() {
        try {
            InetAddress.getByName(EKS_DNS_PROBE);
            log.debug("EKS detected via DNS resolution of {}", EKS_DNS_PROBE);
            return true;
        } catch (UnknownHostException e) {
            log.debug("EKS DNS probe failed for {}", EKS_DNS_PROBE);
            return false;
        }
    }

    private boolean isAks() {
        try {
            InetAddress.getByName(AKS_DNS_PROBE);
            log.debug("AKS detected via DNS resolution of {}", AKS_DNS_PROBE);
            return true;
        } catch (UnknownHostException e) {
            log.debug("AKS DNS probe failed for {}", AKS_DNS_PROBE);
            return false;
        }
    }
}