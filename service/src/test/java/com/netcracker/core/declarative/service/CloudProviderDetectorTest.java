package com.netcracker.core.declarative.service;

import com.netcracker.core.declarative.service.composite.model.CloudProvider;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.OutputStream;
import java.net.InetSocketAddress;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests use a real HTTP server on localhost to avoid mocking static URL/connection internals.
 * Each test starts a server on a random port and rewrites METADATA_URL via reflection.
 */
@ExtendWith(MockitoExtension.class)
class CloudProviderDetectorTest {

    private HttpServer server;
    private CloudProviderDetector detector;

    @BeforeEach
    void setUp() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.start();
        detector = new CloudProviderDetector("http://127.0.0.1:" + server.getAddress().getPort());
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    @Test
    void detect_returnsGke_whenMetadataFlavorIsGoogle() throws Exception {
        server.createContext("/computeMetadata/v1/", exchange -> {
            exchange.getResponseHeaders().add("Metadata-Flavor", "Google");
            exchange.sendResponseHeaders(200, 0);
            exchange.getResponseBody().close();
        });
        stubNotFound("/latest/meta-data/");
        stubNotFound("/metadata/instance");

        assertThat(detector.detect()).isEqualTo(CloudProvider.GKE);
    }

    @Test
    void detect_notGke_whenMetadataFlavorHeaderAbsent() throws Exception {
        server.createContext("/computeMetadata/v1/", exchange -> {
            exchange.sendResponseHeaders(200, 0);
            exchange.getResponseBody().close();
        });
        stubNotFound("/latest/meta-data/");
        stubNotFound("/metadata/instance");

        assertThat(detector.detect()).isEqualTo(CloudProvider.OnPrem);
    }

    @Test
    void detect_returnsEks_whenImdsV1Returns200() throws Exception {
        stubNotFound("/computeMetadata/v1/");
        server.createContext("/latest/meta-data/", exchange -> {
            exchange.sendResponseHeaders(200, 0);
            exchange.getResponseBody().close();
        });
        stubNotFound("/metadata/instance");

        assertThat(detector.detect()).isEqualTo(CloudProvider.EKS);
    }

    @Test
    void detect_returnsEks_whenImdsV2Returns401() throws Exception {
        stubNotFound("/computeMetadata/v1/");
        server.createContext("/latest/meta-data/", exchange -> {
            exchange.sendResponseHeaders(401, 0);
            exchange.getResponseBody().close();
        });
        stubNotFound("/metadata/instance");

        assertThat(detector.detect()).isEqualTo(CloudProvider.EKS);
    }

    @Test
    void detect_notEks_whenImdsReturns403() throws Exception {
        stubNotFound("/computeMetadata/v1/");
        server.createContext("/latest/meta-data/", exchange -> {
            exchange.sendResponseHeaders(403, 0);
            exchange.getResponseBody().close();
        });
        stubNotFound("/metadata/instance");

        assertThat(detector.detect()).isEqualTo(CloudProvider.OnPrem);
    }

    @Test
    void detect_returnsAks_whenAzureInstanceEndpointReturns200() throws Exception {
        stubNotFound("/computeMetadata/v1/");
        stubNotFound("/latest/meta-data/");
        server.createContext("/metadata/instance", exchange -> {
            byte[] body = "{\"compute\":{\"azEnvironment\":\"AzurePublicCloud\"}}".getBytes();
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body);
            }
        });

        assertThat(detector.detect()).isEqualTo(CloudProvider.AKS);
    }

    @Test
    void detect_notAks_whenAzureEndpointReturns404() throws Exception {
        stubNotFound("/computeMetadata/v1/");
        stubNotFound("/latest/meta-data/");
        stubNotFound("/metadata/instance");

        assertThat(detector.detect()).isEqualTo(CloudProvider.OnPrem);
    }

    @Test
    void detect_returnsOnPrem_whenMetadataUnreachable() {
        detector = new CloudProviderDetector("http://127.0.0.1:19999");
        assertThat(detector.getCloudProvider()).isNull();

        detector.init();

        assertThat(detector.getCloudProvider()).isEqualTo(CloudProvider.OnPrem);
    }

    @Test
    void detect_returnsOnPrem_whenAllEndpointsReturn500() throws Exception {
        stubStatus("/computeMetadata/v1/", 500);
        stubStatus("/latest/meta-data/", 500);
        stubStatus("/metadata/instance", 500);

        assertThat(detector.detect()).isEqualTo(CloudProvider.OnPrem);
    }

    @Test
    void detect_prefersGke_whenBothGkeAndEksProbesSucceed() throws Exception {
        server.createContext("/computeMetadata/v1/", exchange -> {
            exchange.getResponseHeaders().add("Metadata-Flavor", "Google");
            exchange.sendResponseHeaders(200, 0);
            exchange.getResponseBody().close();
        });
        server.createContext("/latest/meta-data/", exchange -> {
            exchange.sendResponseHeaders(200, 0);
            exchange.getResponseBody().close();
        });
        stubNotFound("/metadata/instance");

        assertThat(detector.detect()).isEqualTo(CloudProvider.GKE);
    }

    @Test
    void init_setsCloudProviderField() throws Exception {
        stubNotFound("/computeMetadata/v1/");
        stubNotFound("/latest/meta-data/");
        stubNotFound("/metadata/instance");

        detector.init();

        assertThat(detector.getCloudProvider()).isEqualTo(CloudProvider.OnPrem);
    }

    private void stubNotFound(String path) {
        server.createContext(path, exchange -> {
            exchange.sendResponseHeaders(404, 0);
            exchange.getResponseBody().close();
        });
    }

    private void stubStatus(String path, int status) {
        server.createContext(path, exchange -> {
            exchange.sendResponseHeaders(status, 0);
            exchange.getResponseBody().close();
        });
    }
}