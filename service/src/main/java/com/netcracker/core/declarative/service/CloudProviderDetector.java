package com.netcracker.core.declarative.service;

import com.netcracker.core.declarative.service.composite.model.CloudProvider;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.net.HttpURLConnection;
import java.net.URI;

@Slf4j
@Getter
@ApplicationScoped
public class CloudProviderDetector {

    protected static final String DEFAULT_METADATA_URL = "http://169.254.169.254";
    private static final int CONNECT_TIMEOUT_MS = 300;
    private static final int READ_TIMEOUT_MS = 300;

    private final String metadataUrl;
    private CloudProvider cloudProvider;

    CloudProviderDetector() {
        this(DEFAULT_METADATA_URL);
    }

    CloudProviderDetector(String metadataUrl) {
        this.metadataUrl = metadataUrl;
    }

    @PostConstruct
    void init() {
        cloudProvider = detect();
        log.info("Detected cloud provider: {}", cloudProvider);
    }

    protected CloudProvider detect() {
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
            HttpURLConnection conn = openConnection(metadataUrl + "/computeMetadata/v1/");
            conn.setRequestProperty("Metadata-Flavor", "Google");
            int code = conn.getResponseCode();
            String flavor = conn.getHeaderField("Metadata-Flavor");
            log.debug("GKE probe: status={} Metadata-Flavor={}", code, flavor);
            return "Google".equals(flavor);
        } catch (Exception e) {
            log.debug("GKE probe failed: {}", e.toString());
            return false;
        }
    }

    private boolean isEks() {
        try {
            HttpURLConnection conn = openConnection(metadataUrl + "/latest/meta-data/");
            int code = conn.getResponseCode();
            log.debug("EKS probe: status={}", code);
            // 200 = IMDSv1 open, 401 = IMDSv2 required — both confirm EC2/EKS
            return code == 200 || code == 401;
        } catch (Exception e) {
            log.debug("EKS probe failed: {}", e.toString());
            return false;
        }
    }

    private boolean isAks() {
        try {
            HttpURLConnection conn = openConnection(metadataUrl + "/metadata/instance?api-version=2021-02-01");
            conn.setRequestProperty("Metadata", "true");
            int code = conn.getResponseCode();
            log.debug("AKS probe: status={}", code);
            return code == 200;
        } catch (Exception e) {
            log.debug("AKS probe failed: {}", e.toString());
            return false;
        }
    }

    private static HttpURLConnection openConnection(String url) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URI(url).toURL().openConnection();
        conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
        conn.setReadTimeout(READ_TIMEOUT_MS);
        conn.setRequestMethod("GET");
        conn.setInstanceFollowRedirects(false);
        return conn;
    }
}