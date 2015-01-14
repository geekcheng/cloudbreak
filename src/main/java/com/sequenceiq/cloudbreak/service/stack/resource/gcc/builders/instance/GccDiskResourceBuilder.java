package com.sequenceiq.cloudbreak.service.stack.resource.gcc.builders.instance;

import static com.sequenceiq.cloudbreak.service.stack.connector.azure.AzureStackUtil.ERROR;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.services.compute.Compute;
import com.google.api.services.compute.model.Disk;
import com.google.api.services.compute.model.Operation;
import com.google.common.base.Optional;
import com.sequenceiq.cloudbreak.controller.InternalServerException;
import com.sequenceiq.cloudbreak.controller.json.JsonHelper;
import com.sequenceiq.cloudbreak.domain.GccCredential;
import com.sequenceiq.cloudbreak.domain.GccTemplate;
import com.sequenceiq.cloudbreak.domain.Resource;
import com.sequenceiq.cloudbreak.domain.ResourceType;
import com.sequenceiq.cloudbreak.domain.Stack;
import com.sequenceiq.cloudbreak.domain.TemplateGroup;
import com.sequenceiq.cloudbreak.repository.StackRepository;
import com.sequenceiq.cloudbreak.service.PollingService;
import com.sequenceiq.cloudbreak.service.stack.connector.gcc.GccRemoveCheckerStatus;
import com.sequenceiq.cloudbreak.service.stack.connector.gcc.GccRemoveReadyPollerObject;
import com.sequenceiq.cloudbreak.service.stack.connector.gcc.GccResourceCheckerStatus;
import com.sequenceiq.cloudbreak.service.stack.connector.gcc.GccResourceCreationException;
import com.sequenceiq.cloudbreak.service.stack.connector.gcc.GccResourceReadyPollerObject;
import com.sequenceiq.cloudbreak.service.stack.connector.gcc.GccStackUtil;
import com.sequenceiq.cloudbreak.service.stack.resource.CreateResourceRequest;
import com.sequenceiq.cloudbreak.service.stack.connector.gcc.domain.GccZone;
import com.sequenceiq.cloudbreak.service.stack.resource.gcc.GccSimpleInstanceResourceBuilder;
import com.sequenceiq.cloudbreak.service.stack.resource.gcc.model.GccDeleteContextObject;
import com.sequenceiq.cloudbreak.service.stack.resource.gcc.model.GccDescribeContextObject;
import com.sequenceiq.cloudbreak.service.stack.resource.gcc.model.GccProvisionContextObject;

@Component
@Order(1)
public class GccDiskResourceBuilder extends GccSimpleInstanceResourceBuilder {
    private static final long SIZE = 20L;

    @Autowired
    private StackRepository stackRepository;
    @Autowired
    private GccResourceCheckerStatus gccResourceCheckerStatus;
    @Autowired
    private PollingService<GccResourceReadyPollerObject> gccDiskReadyPollerObjectPollingService;
    @Autowired
    private GccRemoveCheckerStatus gccRemoveCheckerStatus;
    @Autowired
    private PollingService<GccRemoveReadyPollerObject> gccRemoveReadyPollerObjectPollingService;
    @Autowired
    private JsonHelper jsonHelper;
    @Autowired
    private GccStackUtil gccStackUtil;

    @Override
    public Boolean create(final CreateResourceRequest createResourceRequest, TemplateGroup templateGroup, String region) throws Exception {
        final GccDiskCreateRequest gDCR = (GccDiskCreateRequest) createResourceRequest;
        Stack stack = stackRepository.findById(gDCR.getStackId());
        Compute.Disks.Insert insDisk = gDCR.getCompute().disks().insert(gDCR.getProjectId(), GccZone.valueOf(region).getValue(), gDCR.getDisk());
        insDisk.setSourceImage(gccStackUtil.getAmbariUbuntu(gDCR.getProjectId()));
        Operation execute = insDisk.execute();
        if (execute.getHttpErrorStatusCode() == null) {
            Compute.ZoneOperations.Get zoneOperations = createZoneOperations(gDCR.getCompute(), gDCR.getGccCredential(), execute, GccZone.valueOf(region));
            GccResourceReadyPollerObject gccDiskReady = new GccResourceReadyPollerObject(zoneOperations, stack, gDCR.getDisk().getName(), execute.getName());
            gccDiskReadyPollerObjectPollingService.pollWithTimeout(gccResourceCheckerStatus, gccDiskReady, POLLING_INTERVAL, MAX_POLLING_ATTEMPTS);
            return true;
        } else {
            throw new GccResourceCreationException(execute.getHttpErrorMessage(), resourceType(), gDCR.getDisk().getName());
        }
    }

    @Override
    public Boolean delete(Resource resource, GccDeleteContextObject deleteContextObject, String region) throws Exception {
        Stack stack = stackRepository.findById(deleteContextObject.getStackId());
        try {
            GccCredential gccCredential = (GccCredential) stack.getCredential();
            Operation operation = deleteContextObject.getCompute().disks()
                    .delete(gccCredential.getProjectId(), GccZone.valueOf(region).getValue(), resource.getResourceName()).execute();
            Compute.ZoneOperations.Get zoneOperations = createZoneOperations(deleteContextObject.getCompute(), gccCredential, operation, GccZone.valueOf(region));
            Compute.GlobalOperations.Get globalOperations = createGlobalOperations(deleteContextObject.getCompute(), gccCredential, operation);
            GccRemoveReadyPollerObject gccRemoveReady =
                    new GccRemoveReadyPollerObject(zoneOperations, globalOperations, stack, resource.getResourceName(), operation.getName());
            gccRemoveReadyPollerObjectPollingService.pollWithTimeout(gccRemoveCheckerStatus, gccRemoveReady, POLLING_INTERVAL, MAX_POLLING_ATTEMPTS);
        } catch (GoogleJsonResponseException ex) {
            exceptionHandler(ex, resource.getResourceName(), stack);
        } catch (IOException e) {
            throw new InternalServerException(e.getMessage());
        }
        return true;
    }

    @Override
    public Optional<String> describe(Resource resource, GccDescribeContextObject describeContextObject, String region) throws Exception {
        Stack stack = stackRepository.findById(describeContextObject.getStackId());
        GccCredential gccCredential = (GccCredential) stack.getCredential();
        try {
            Compute.Disks.Get getDisk =
                    describeContextObject.getCompute().disks().get(gccCredential.getProjectId(), GccZone.valueOf(region).getValue(),
                            resource.getResourceName());
            return Optional.fromNullable(getDisk.execute().toPrettyString());
        } catch (IOException e) {
            return Optional.fromNullable(jsonHelper.createJsonFromString(String.format("{\"Disk\": {%s}}", ERROR)).toString());
        }
    }

    @Override
    public List<Resource> buildResources(GccProvisionContextObject provisionContextObject, int index, List<Resource> resources, TemplateGroup templateGroup) {
        Stack stack = stackRepository.findById(provisionContextObject.getStackId());
        Resource resource = new Resource(resourceType(), String.format("%s-%s-%s", stack.getName(), index, new Date().getTime()), stack);
        return Arrays.asList(resource);
    }

    @Override
    public CreateResourceRequest buildCreateRequest(GccProvisionContextObject provisionContextObject, List<Resource> resources,
            List<Resource> buildResources, int index, TemplateGroup templateGroup) throws Exception {
        Stack stack = stackRepository.findById(provisionContextObject.getStackId());
        GccCredential gccCredential = (GccCredential) stack.getCredential();
        GccTemplate gccTemplate = (GccTemplate) templateGroup.getTemplate();
        Disk disk = new Disk();
        disk.setSizeGb(SIZE);
        disk.setName(buildResources.get(0).getResourceName());
        disk.setKind(gccTemplate.getGccRawDiskType().getUrl(provisionContextObject.getProjectId(), GccZone.valueOf(stack.getRegion())));
        return new GccDiskCreateRequest(provisionContextObject.getStackId(), resources, disk, provisionContextObject.getProjectId(),
                provisionContextObject.getCompute(), gccTemplate, gccCredential, buildResources);
    }

    @Override
    public ResourceType resourceType() {
        return ResourceType.GCC_DISK;
    }

    public class GccDiskCreateRequest extends CreateResourceRequest {

        private Long stackId;
        private List<Resource> resources = new ArrayList<>();
        private Disk disk;
        private String projectId;
        private Compute compute;
        private GccTemplate gccTemplate;
        private GccCredential gccCredential;

        public GccDiskCreateRequest(Long stackId, List<Resource> resources, Disk disk,
                String projectId, Compute compute, GccTemplate gccTemplate, GccCredential gccCredential, List<Resource> buildNames) {
            super(buildNames);
            this.stackId = stackId;
            this.resources = resources;
            this.disk = disk;
            this.projectId = projectId;
            this.compute = compute;
            this.gccTemplate = gccTemplate;
            this.gccCredential = gccCredential;
        }

        public Long getStackId() {
            return stackId;
        }

        public List<Resource> getResources() {
            return resources;
        }

        public Disk getDisk() {
            return disk;
        }

        public String getProjectId() {
            return projectId;
        }

        public Compute getCompute() {
            return compute;
        }

        public GccTemplate getGccTemplate() {
            return gccTemplate;
        }

        public GccCredential getGccCredential() {
            return gccCredential;
        }
    }

}
