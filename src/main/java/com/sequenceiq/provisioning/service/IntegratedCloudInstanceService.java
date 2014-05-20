package com.sequenceiq.provisioning.service;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UnknownFormatConversionException;

import javax.annotation.Resource;
import javax.persistence.EntityNotFoundException;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.sequenceiq.provisioning.controller.json.CloudInstanceRequest;
import com.sequenceiq.provisioning.controller.json.CloudInstanceResult;
import com.sequenceiq.provisioning.controller.json.InfraRequest;
import com.sequenceiq.provisioning.converter.AwsCloudInstanceConverter;
import com.sequenceiq.provisioning.converter.AzureCloudInstanceConverter;
import com.sequenceiq.provisioning.domain.AwsCloudInstance;
import com.sequenceiq.provisioning.domain.AzureCloudInstance;
import com.sequenceiq.provisioning.domain.CloudInstance;
import com.sequenceiq.provisioning.domain.CloudPlatform;
import com.sequenceiq.provisioning.domain.User;
import com.sequenceiq.provisioning.repository.CloudInstanceRepository;

@Service
public class IntegratedCloudInstanceService implements CloudInstanceService {

    @Autowired
    private AwsCloudInstanceConverter awsCloudInstanceConverter;

    @Autowired
    private AzureCloudInstanceConverter azureCloudInstanceConverter;

    @Autowired
    private CloudInstanceRepository cloudInstanceRepository;
    //
    // @Autowired
    // private UserRepository userRepository;

    @Autowired
    private CommonInfraService commonInfraService;

    @Resource
    private Map<CloudPlatform, ProvisionService> provisionServices;

    @Override
    public Set<CloudInstanceRequest> getAll(User user) {
        Set<CloudInstanceRequest> result = new HashSet<>();
        result.addAll(awsCloudInstanceConverter.convertAllEntityToJson(user.getAwsCloudInstanceList()));
        result.addAll(azureCloudInstanceConverter.convertAllEntityToJson(user.getAzureCloudInstanceList()));
        return result;
    }

    @Override
    public CloudInstanceRequest get(Long id) {
        CloudInstance one = cloudInstanceRepository.findOne(id);
        if (one == null) {
            throw new EntityNotFoundException("Entity not exist with id: " + id);
        } else {
            switch (one.cloudPlatform()) {
            case AWS:
                return awsCloudInstanceConverter.convert((AwsCloudInstance) one);
            case AZURE:
                return azureCloudInstanceConverter.convert((AzureCloudInstance) one);
            default:
                throw new UnknownFormatConversionException("The cloudPlatform type not supported.");
            }
        }
    }

    @Override
    public CloudInstanceResult create(User user, CloudInstanceRequest cloudInstanceRequest) {
        InfraRequest infraRequest = commonInfraService.get(cloudInstanceRequest.getInfraId());
        if (infraRequest == null) {
            throw new EntityNotFoundException("Infra config not exist with id: " + cloudInstanceRequest.getInfraId());
        }
        CloudInstance cloudInstance;
        switch (infraRequest.getCloudPlatform()) {
        case AWS:
            cloudInstance = awsCloudInstanceConverter.convert(cloudInstanceRequest);
            cloudInstance.setUser(user);
            cloudInstanceRepository.save(cloudInstance);
            break;
        case AZURE:
            cloudInstance = azureCloudInstanceConverter.convert(cloudInstanceRequest);
            cloudInstance.setUser(user);
            cloudInstanceRepository.save(cloudInstance);
            break;
        default:
            throw new UnknownFormatConversionException("The cloudPlatform type is not supported.");
        }

        ProvisionService provisionService = provisionServices.get(infraRequest.getCloudPlatform());
        return provisionService.createCloudInstance(user, cloudInstance);
    }

}
