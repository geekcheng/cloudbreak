package com.sequenceiq.cloudbreak.service.cluster.event;

import java.util.List;

import com.sequenceiq.cloudbreak.controller.json.HostGroupAdjustmentJson;
import com.sequenceiq.cloudbreak.domain.HostMetadata;

public class UpdateAmbariHostsRequest {

    private Long stackId;
    private HostGroupAdjustmentJson hostGroupAdjustment;
    private List<HostMetadata> decommissionCandidates;
    private boolean decommission;

    public UpdateAmbariHostsRequest(Long stackId, HostGroupAdjustmentJson adjustmentJson,
            List<HostMetadata> decommissionCandidates, boolean decommission) {
        this.stackId = stackId;
        this.decommissionCandidates = decommissionCandidates;
        this.hostGroupAdjustment = adjustmentJson;
        this.decommission = decommission;
    }

    public Long getStackId() {
        return stackId;
    }

    public HostGroupAdjustmentJson getHostGroupAdjustment() {
        return hostGroupAdjustment;
    }

    public boolean isDecommission() {
        return decommission;
    }

    public List<HostMetadata> getDecommissionCandidates() {
        return decommissionCandidates;
    }
}
