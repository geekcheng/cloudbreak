package com.sequenceiq.cloudbreak.converter;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

import com.sequenceiq.cloudbreak.controller.BadRequestException;
import com.sequenceiq.cloudbreak.controller.json.ClusterResponse;
import com.sequenceiq.cloudbreak.controller.json.StackJson;
import com.sequenceiq.cloudbreak.controller.json.InstanceGroupJson;
import com.sequenceiq.cloudbreak.domain.Stack;
import com.sequenceiq.cloudbreak.domain.Status;
import com.sequenceiq.cloudbreak.repository.CredentialRepository;
import com.sequenceiq.cloudbreak.repository.TemplateRepository;

@Component
public class StackConverter extends AbstractConverter<StackJson, Stack> {

    private static final int MIN_NODE_COUNT = 3;
    @Autowired
    private TemplateRepository templateRepository;

    @Autowired
    private CredentialRepository credentialRepository;

    @Autowired
    private ClusterConverter clusterConverter;

    @Autowired
    private InstanceGroupConverter instanceGroupConverter;

    @Autowired
    private MetaDataConverter metaDataConverter;

    @Override
    public StackJson convert(Stack entity) {
        StackJson stackJson = new StackJson();
        stackJson.setName(entity.getName());
        stackJson.setOwner(entity.getOwner());
        stackJson.setAccount(entity.getAccount());
        stackJson.setPublicInAccount(entity.isPublicInAccount());
        stackJson.setId(entity.getId());
        stackJson.setCloudPlatform(entity.cloudPlatform());
        stackJson.setCredentialId(entity.getCredential().getId());
        stackJson.setStatus(entity.getStatus());
        stackJson.setStatusReason(entity.getStatusReason());
        stackJson.setAmbariServerIp(entity.getAmbariIp());
        stackJson.setUserName(entity.getUserName());
        stackJson.setPassword(entity.getPassword());
        stackJson.setHash(entity.getHash());
        stackJson.setRegion(entity.getRegion());
        List<InstanceGroupJson> templateGroups = new ArrayList<>();
        templateGroups.addAll(instanceGroupConverter.convertAllEntityToJson(entity.getInstanceGroups()));
        stackJson.setInstanceGroups(templateGroups);
        if (entity.getCluster() != null) {
            stackJson.setCluster(clusterConverter.convert(entity.getCluster(), "{}"));
        } else {
            stackJson.setCluster(new ClusterResponse());
        }
        return stackJson;
    }

    @Override
    public Stack convert(StackJson json) {
        Stack stack = new Stack();
        stack.setName(json.getName());
        stack.setUserName(json.getUserName());
        stack.setPassword(json.getPassword());
        stack.setPublicInAccount(json.isPublicInAccount());
        stack.setRegion(json.getRegion());
        try {
            stack.setCredential(credentialRepository.findOne(json.getCredentialId()));
        } catch (AccessDeniedException e) {
            throw new AccessDeniedException(String.format("Access to credential '%s' is denied or credential doesn't exist.", json.getCredentialId()), e);
        }
        stack.setStatus(Status.REQUESTED);
        stack.setInstanceGroups(instanceGroupConverter.convertAllJsonToEntity(json.getInstanceGroups(), stack));
        if (stack.getFullNodeCount() < MIN_NODE_COUNT) {
            throw new BadRequestException("NodeCount of stack has to be at least 3.");
        }
        return stack;
    }

    public Stack convert(StackJson json, boolean publicInAccount) {
        Stack stack = convert(json);
        stack.setPublicInAccount(publicInAccount);
        return stack;
    }

    public Set<StackJson> convertAllEntityToJsonWithClause(Collection<Stack> entityList) {
        Set<StackJson> stackJsons = new HashSet<>();
        for (Stack stack : entityList) {
            stackJsons.add(convert(stack));
        }
        return stackJsons;
    }

    public Map<String, Object> convertStackStatus(Stack stack) {
        Map<String, Object> stackStatus = new HashMap<>();
        stackStatus.put("id", stack.getId());
        stackStatus.put("status", stack.getStatus().name());
        return stackStatus;
    }
}
