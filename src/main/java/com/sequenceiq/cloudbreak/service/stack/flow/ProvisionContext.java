package com.sequenceiq.cloudbreak.service.stack.flow;

import java.util.Map;

import javax.annotation.Resource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import reactor.core.Reactor;
import reactor.event.Event;

import com.sequenceiq.cloudbreak.conf.ReactorConfig;
import com.sequenceiq.cloudbreak.domain.CloudPlatform;
import com.sequenceiq.cloudbreak.domain.Stack;
import com.sequenceiq.cloudbreak.domain.Status;
import com.sequenceiq.cloudbreak.domain.WebsocketEndPoint;
import com.sequenceiq.cloudbreak.repository.RetryingStackUpdater;
import com.sequenceiq.cloudbreak.repository.StackRepository;
import com.sequenceiq.cloudbreak.service.stack.connector.Provisioner;
import com.sequenceiq.cloudbreak.service.stack.connector.UserDataBuilder;
import com.sequenceiq.cloudbreak.service.stack.event.StackCreationFailure;
import com.sequenceiq.cloudbreak.websocket.WebsocketService;
import com.sequenceiq.cloudbreak.websocket.message.StatusMessage;

@Service
public class ProvisionContext {

    private static final Logger LOGGER = LoggerFactory.getLogger(ProvisionContext.class);

    @Autowired
    private StackRepository stackRepository;

    @Autowired
    private RetryingStackUpdater stackUpdater;

    @Autowired
    private WebsocketService websocketService;

    @Resource
    private Map<CloudPlatform, Provisioner> provisioners;

    @Autowired
    private Reactor reactor;

    @Autowired
    private UserDataBuilder userDataBuilder;

    public void buildStack(CloudPlatform cloudPlatform, Long stackId, Map<String, Object> setupProperties, Map<String, String> userDataParams) {
        try {
            Stack stack = stackRepository.findById(stackId);
            if (stack.getStatus().equals(Status.REQUESTED)) {
                stack = stackUpdater.updateStackStatus(stack.getId(), Status.CREATE_IN_PROGRESS);
                websocketService.sendToTopicUser(stack.getUser().getEmail(), WebsocketEndPoint.STACK, new StatusMessage(stack.getId(), stack.getName(), stack
                        .getStatus().name()));
                Provisioner provisioner = provisioners.get(cloudPlatform);
                provisioner.buildStack(stack, userDataBuilder.build(cloudPlatform, stack.getHash(), userDataParams), setupProperties);
            } else {
                LOGGER.info("CloudFormation stack creation was requested for a stack, that is not in REQUESTED status anymore. [stackId: '{}', status: '{}']",
                        stack.getId(), stack.getStatus());
            }
        } catch (Exception e) {
            LOGGER.error("Unhandled exception occured while creating stack.", e);
            LOGGER.info("Publishing {} event [StackId: '{}']", ReactorConfig.STACK_CREATE_FAILED_EVENT, stackId);
            StackCreationFailure stackCreationFailure = new StackCreationFailure(stackId,
                    "Internal server error occured while creating stack.");
            reactor.notify(ReactorConfig.STACK_CREATE_FAILED_EVENT, Event.wrap(stackCreationFailure));
        }
    }

}