package com.netcracker.core.declarative.client.rest;

public enum ProcessStatus {
    NOT_STARTED("NOT_STARTED"),
    IN_PROGRESS("IN_PROGRESS"),
    FAILED("FAILED"),
    COMPLETED("COMPLETED");

    ProcessStatus(String value) {
    }
}
