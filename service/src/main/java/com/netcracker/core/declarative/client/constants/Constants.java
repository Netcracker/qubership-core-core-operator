package com.netcracker.core.declarative.client.constants;

public class Constants {
    public static final String RESOURCE_NAME = "resource-name";
    public static final String KIND = "kind";
    public static final String SUB_KIND = "sub-kind";
    public static final String PHASE = "phase";
    public static String X_REQUEST_ID = "x-request-id";
    public static final String SESSION_ID_KEY = "sessionId";
    public static final String SUB_KIND_ROLE = "Role";
    public static final String SUB_KIND_UI_CLIENT = "UIClient";
    public static final String SUB_KIND_EXTERNAL_CLIENT = "ExternalClient";
    public static final String SUB_KIND_EXTERNAL_CREDS_CLIENT = "ExternalClientWithProvidedCreds";
    public static final String VALIDATED_STEP_NAME = "Validated";
    public static final String TYPE_UNKNOWN = "Unknown";
    public static final String MESSAGE_UNKNOWN = "Can not parse error response";
    public static final double CR_RETRY_MAX_INTERVAL = 900D;
    public static final String CR_RETRY_EXCEEDED_MESSAGE = "CR processing retries exceeded";
    public static final String CR_RETRY_EXCEEDED_REASON = "Exceeded the amount of retries while waiting for CR to be processed";

    private Constants() {
    }
}
