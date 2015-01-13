package com.sequenceiq.cloudbreak.service.stack.resource.azure.model;

import com.sequenceiq.cloud.azure.client.AzureClient;
import com.sequenceiq.cloudbreak.domain.Stack;
import com.sequenceiq.cloudbreak.service.stack.resource.StartStopContextObject;

public class AzureStartStopContextObject extends StartStopContextObject {

    private AzureClient azureClient;

    public AzureStartStopContextObject(Stack stack, AzureClient azureClient) {
        super(stack);
        this.azureClient = azureClient;
    }

    public AzureClient getAzureClient() {
        return azureClient;
    }

}
