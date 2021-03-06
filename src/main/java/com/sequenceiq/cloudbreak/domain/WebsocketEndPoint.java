package com.sequenceiq.cloudbreak.domain;

public enum WebsocketEndPoint {

    STACK("/stack"),
    UPTIME("/uptime"),
    BLUEPRINT("/blueprint"),
    CREDENTIAL("/credential"),
    COPY_IMAGE("/copyimage"),
    TERMINATE("/teminate"),
    CLUSTER("/cluster");

    private final String value;

    private WebsocketEndPoint(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }
}
