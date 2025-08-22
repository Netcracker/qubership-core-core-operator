package com.netcracker.core.declarative.service;

import com.netcracker.core.declarative.client.rest.CompositeClient;
import io.vertx.core.Future;
import io.vertx.ext.consul.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import com.netcracker.cloud.consul.provider.common.TokenStorage;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class CompositeConsulUpdaterImplTest {

    private ConsulClient consulClient;
    private ConsulClientFactory consulClientFactory;

    @BeforeEach
    void setUp() {
        consulClientFactory = mock(ConsulClientFactory.class);
        consulClient = mock(ConsulClient.class);
        when(consulClientFactory.create(any())).thenReturn(consulClient);
    }

    @Test
    void compositeStructureUpdateStep_cleanup_base() throws InterruptedException, ExecutionException {
        when(consulClient.getValues(
                anyString())).thenReturn(
                Future.succeededFuture(new KeyValueList().setList(List.of(
                                        new KeyValue()
                                                .setKey("composite/%s/structure/%s/compositeRole".formatted("BO", "BC"))
                                                .setValue("baseline"),
                                        new KeyValue()
                                                .setKey("composite/%s/structure/%s/controllerNamespace".formatted("BO", "BO"))
                                                .setValue("BC"),
                                        new KeyValue()
                                                .setKey("composite/%s/structure/%s/controllerNamespace".formatted("BO", "BP"))
                                                .setValue("BC")
                                )
                        )
                )
        );
        when(consulClient.transaction(any())).thenReturn(Future.succeededFuture(mock(TxnResponse.class)));

        CompositeConsulUpdater compositeConsulUpdater = new CompositeConsulUpdaterImpl("BO", consulClientFactory, mock(TokenStorage.class));
        compositeConsulUpdater.updateCompositeStructureInConsul(new CompositeSpec(null, "BO", null, null));

        ArgumentCaptor<TxnRequest> txnRequestArgumentCaptor = ArgumentCaptor.forClass(TxnRequest.class);
        verify(consulClient).transaction(txnRequestArgumentCaptor.capture());
        List<TxnOperation> operations = txnRequestArgumentCaptor.getValue().getOperations();
        verifyConsulKeyDeleteTxn(operations, List.of(
                "composite/%s/structure/%s".formatted("BO", "BC"),
                "composite/%s/structure/%s".formatted("BO", "BO"),
                "composite/%s/structure/%s".formatted("BO", "BP")
        ));
    }

    @Test
    void compositeStructureUpdateStep_cleanup_satellite() throws InterruptedException, ExecutionException {
        when(consulClient.getValues(
                anyString())).thenReturn(
                Future.succeededFuture(new KeyValueList().setList(List.of(
                                        new KeyValue()
                                                .setKey("composite/%s/structure/%s/compositeRole".formatted("BO", "SC"))
                                                .setValue("satellite"),
                                        new KeyValue()
                                                .setKey("composite/%s/structure/%s/controllerNamespace".formatted("BO", "SO"))
                                                .setValue("SC"),
                                        new KeyValue()
                                                .setKey("composite/%s/structure/%s/controllerNamespace".formatted("BO", "SP"))
                                                .setValue("SC")
                                )
                        )
                )
        );
        when(consulClient.transaction(any())).thenReturn(Future.succeededFuture(mock(TxnResponse.class)));

        CompositeConsulUpdater compositeConsulUpdater = new CompositeConsulUpdaterImpl("BO", consulClientFactory, mock(TokenStorage.class));
        compositeConsulUpdater.updateCompositeStructureInConsul(new CompositeSpec(null, "SO", null,
                new CompositeSpec.CompositeSpecBaseline(null, "BO", null))
        );

        ArgumentCaptor<TxnRequest> txnRequestArgumentCaptor = ArgumentCaptor.forClass(TxnRequest.class);
        verify(consulClient).transaction(txnRequestArgumentCaptor.capture());
        List<TxnOperation> operations = txnRequestArgumentCaptor.getValue().getOperations();
        verifyConsulKeyDeleteTxn(operations, List.of(
                "composite/%s/structure/%s".formatted("BO", "SC"),
                "composite/%s/structure/%s".formatted("BO", "SO"),
                "composite/%s/structure/%s".formatted("BO", "SP")
        ));
    }

    @Test
    void compositeStructureUpdateStep() throws InterruptedException, ExecutionException {
        when(consulClient.getValues(anyString())).thenReturn(Future.succeededFuture(new KeyValueList().setList(Collections.emptyList())));
        when(consulClient.transaction(any())).thenReturn(Future.succeededFuture(mock(TxnResponse.class)));

        CompositeConsulUpdater compositeConsulUpdater = new CompositeConsulUpdaterImpl("BO", consulClientFactory, mock(TokenStorage.class));
        compositeConsulUpdater.updateCompositeStructureInConsul(new CompositeSpec(null, "BO", null, null));

        ArgumentCaptor<TxnRequest> txnRequestArgumentCaptor = ArgumentCaptor.forClass(TxnRequest.class);
        verify(consulClient).transaction(txnRequestArgumentCaptor.capture());
        List<TxnOperation> operations = txnRequestArgumentCaptor.getValue().getOperations();
        verifyConsulValueSetTxn(operations, List.of(
                "config/BO/application/composite/structureRef", "composite/BO/structure",
                "composite/BO/structure/BO/compositeRole", "baseline"
        ));
    }

    @Test
    void compositeStructureUpdateStep_BO() throws InterruptedException, ExecutionException {
        when(consulClient.getValues(anyString())).thenReturn(Future.succeededFuture(new KeyValueList().setList(Collections.emptyList())));
        when(consulClient.transaction(any())).thenReturn(Future.succeededFuture(mock(TxnResponse.class)));

        CompositeConsulUpdater compositeConsulUpdater = new CompositeConsulUpdaterImpl("BO", consulClientFactory, mock(TokenStorage.class));
        compositeConsulUpdater.updateCompositeStructureInConsul(new CompositeSpec(null, "BO", null, null));

        ArgumentCaptor<TxnRequest> txnRequestArgumentCaptor = ArgumentCaptor.forClass(TxnRequest.class);
        verify(consulClient).transaction(txnRequestArgumentCaptor.capture());
        List<TxnOperation> operations = txnRequestArgumentCaptor.getValue().getOperations();
        verifyConsulValueSetTxn(operations, List.of(
                "config/BO/application/composite/structureRef", "composite/BO/structure",
                "composite/BO/structure/BO/compositeRole", "baseline"
        ));
    }

    @Test
    void compositeStructureUpdateStep_SO() throws InterruptedException, ExecutionException {
        when(consulClient.getValues(anyString())).thenReturn(Future.succeededFuture(new KeyValueList().setList(Collections.emptyList())));
        when(consulClient.transaction(any())).thenReturn(Future.succeededFuture(mock(TxnResponse.class)));

        CompositeConsulUpdater compositeConsulUpdater = new CompositeConsulUpdaterImpl("SO", consulClientFactory, mock(TokenStorage.class));
        compositeConsulUpdater.updateCompositeStructureInConsul(new CompositeSpec(null, "SO", null,
                new CompositeSpec.CompositeSpecBaseline(null, "BO", null)));

        ArgumentCaptor<TxnRequest> txnRequestArgumentCaptor = ArgumentCaptor.forClass(TxnRequest.class);
        verify(consulClient).transaction(txnRequestArgumentCaptor.capture());
        List<TxnOperation> operations = txnRequestArgumentCaptor.getValue().getOperations();
        verifyConsulValueSetTxn(operations, List.of(
                "config/SO/application/composite/structureRef", "composite/BO/structure",
                "composite/BO/structure/SO/compositeRole", "satellite"
        ));
    }

    @Test
    void compositeStructureUpdateStep_BO_BG_SO() throws InterruptedException, ExecutionException {
        when(consulClient.getValues(anyString())).thenReturn(Future.succeededFuture(new KeyValueList().setList(Collections.emptyList())));
        when(consulClient.transaction(any())).thenReturn(Future.succeededFuture(mock(TxnResponse.class)));

        CompositeConsulUpdater compositeConsulUpdater = new CompositeConsulUpdaterImpl("BO", consulClientFactory, mock(TokenStorage.class));
        compositeConsulUpdater.updateCompositeStructureInConsul(new CompositeSpec("BC", "BO", "BP", null));

        ArgumentCaptor<TxnRequest> txnRequestArgumentCaptor = ArgumentCaptor.forClass(TxnRequest.class);
        verify(consulClient).transaction(txnRequestArgumentCaptor.capture());
        List<TxnOperation> operations = txnRequestArgumentCaptor.getValue().getOperations();

        verifyConsulValueSetTxn(operations, List.of(
                "config/BO/application/composite/structureRef", "composite/BO/structure",
                // BC
                "composite/BO/structure/BC/bluegreenRole", "controller",
                "composite/BO/structure/BC/compositeRole", "baseline",
                // BO
                "composite/BO/structure/BO/bluegreenRole", "origin",
                "composite/BO/structure/BO/compositeRole", "baseline",
                "composite/BO/structure/BO/controllerNamespace", "BC",
                // BP
                "composite/BO/structure/BP/bluegreenRole", "peer",
                "composite/BO/structure/BP/compositeRole", "baseline",
                "composite/BO/structure/BP/controllerNamespace", "BC"
        ));
    }

    @Test
    void compositeStructureUpdateStep_BO_SO_BG() throws InterruptedException, ExecutionException {
        when(consulClient.getValues(anyString())).thenReturn(Future.succeededFuture(new KeyValueList().setList(Collections.emptyList())));
        when(consulClient.transaction(any())).thenReturn(Future.succeededFuture(mock(TxnResponse.class)));

        CompositeConsulUpdater compositeConsulUpdater = new CompositeConsulUpdaterImpl("SO", consulClientFactory, mock(TokenStorage.class));
        compositeConsulUpdater.updateCompositeStructureInConsul(new CompositeSpec("SC", "SO", "SP",
                new CompositeSpec.CompositeSpecBaseline(null, "BO", null)));

        ArgumentCaptor<TxnRequest> txnRequestArgumentCaptor = ArgumentCaptor.forClass(TxnRequest.class);
        verify(consulClient).transaction(txnRequestArgumentCaptor.capture());
        List<TxnOperation> operations = txnRequestArgumentCaptor.getValue().getOperations();

        verifyConsulValueSetTxn(operations, List.of(
                "config/SO/application/composite/structureRef", "composite/BO/structure",
                // SC
                "composite/BO/structure/SC/bluegreenRole", "controller",
                "composite/BO/structure/SC/compositeRole", "satellite",
                // SO
                "composite/BO/structure/SO/bluegreenRole", "origin",
                "composite/BO/structure/SO/compositeRole", "satellite",
                "composite/BO/structure/SO/controllerNamespace", "SC",
                // SP
                "composite/BO/structure/SP/bluegreenRole", "peer",
                "composite/BO/structure/SP/compositeRole", "satellite",
                "composite/BO/structure/SP/controllerNamespace", "SC"
        ));
    }

    @Test
    void compositeStructureUpdateStep_BO_BG_SO_BG() throws InterruptedException, ExecutionException {
        when(consulClient.getValues(anyString())).thenReturn(Future.succeededFuture(new KeyValueList().setList(Collections.emptyList())));
        when(consulClient.transaction(any())).thenReturn(Future.succeededFuture(mock(TxnResponse.class)));

        CompositeConsulUpdater compositeConsulUpdater = new CompositeConsulUpdaterImpl("SO", consulClientFactory, mock(TokenStorage.class));
        compositeConsulUpdater.updateCompositeStructureInConsul(new CompositeSpec("SC", "SO", "SP",
                new CompositeSpec.CompositeSpecBaseline("BC", "BO", "BP")));

        ArgumentCaptor<TxnRequest> txnRequestArgumentCaptor = ArgumentCaptor.forClass(TxnRequest.class);
        verify(consulClient).transaction(txnRequestArgumentCaptor.capture());
        List<TxnOperation> operations = txnRequestArgumentCaptor.getValue().getOperations();

        verifyConsulValueSetTxn(operations, List.of(
                "config/SO/application/composite/structureRef", "composite/BO/structure",
                // SC
                "composite/BO/structure/SC/bluegreenRole", "controller",
                "composite/BO/structure/SC/compositeRole", "satellite",
                // SO
                "composite/BO/structure/SO/bluegreenRole", "origin",
                "composite/BO/structure/SO/compositeRole", "satellite",
                "composite/BO/structure/SO/controllerNamespace", "SC",
                // SP
                "composite/BO/structure/SP/bluegreenRole", "peer",
                "composite/BO/structure/SP/compositeRole", "satellite",
                "composite/BO/structure/SP/controllerNamespace", "SC"
        ));
    }

    @Test
    void getCompositeMembers() throws ExecutionException, InterruptedException {
        String basePath = "composite/first/structure";

        when(consulClient.getKeys(basePath))
                .thenReturn(Future.succeededFuture(List.of(
                        basePath + "/first" + "/compositeRole",
                        basePath + "/second" + "/compositeRole",
                        basePath + "/third" + "/compositeRole"
                )));

        CompositeClient compositeClient = mock(CompositeClient.class);
        when(compositeClient.structures(any())).thenReturn(jakarta.ws.rs.core.Response.noContent().build());

        CompositeConsulUpdater compositeConsulUpdater = new CompositeConsulUpdaterImpl("first", consulClientFactory, mock(TokenStorage.class));
        Set<String> compositeMembers = compositeConsulUpdater.getCompositeMembers("first");
        assertEquals(Set.of("first", "second", "third"), compositeMembers);
    }

    private void verifyConsulValueSetTxn(List<TxnOperation> ops, List<String> kvs) {
        for (int i = 0, j = 0; i < kvs.size() / 2; i++, j += 2) {
            TxnKVOperation op = (TxnKVOperation) ops.get(i);
            assertEquals(kvs.get(j), op.getKey());
            assertEquals(kvs.get(j + 1), op.getValue());
        }
    }

    private void verifyConsulKeyDeleteTxn(List<TxnOperation> ops, List<String> keysToDelete) {
        keysToDelete.forEach(s -> assertTrue(
                ops.stream().anyMatch(txnOperation -> ((TxnKVOperation) txnOperation).getKey().equals(s)))
        );
    }
}