package com.sequenceiq.cloudbreak.service.stack.flow;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ecwid.consul.v1.ConsulClient;
import com.sequenceiq.cloudbreak.controller.InternalServerException;
import com.sequenceiq.cloudbreak.logger.MDCBuilder;
import com.sequenceiq.cloudbreak.service.StatusCheckerTask;

public class ConsulHostCheckerTask implements StatusCheckerTask<ConsulContext> {

    private static final Logger LOGGER = LoggerFactory.getLogger(ConsulHostCheckerTask.class);

    @Override
    public boolean checkStatus(ConsulContext consulContext) {
        MDCBuilder.buildMdcContext(consulContext.getStack());
        List<String> privateIps = consulContext.getTargets();
        List<ConsulClient> clients = consulContext.getConsulClients();
        LOGGER.info("Checking '{}' different hosts for consul agents: '{}'", clients.size(), privateIps);
        Map<String, String> members = ConsulUtils.getMembers(clients);
        for (String ip : privateIps) {
            if (members.get(ip) == null) {
                LOGGER.info("Consul agent didn't join on host: {}", ip);
                return false;
            }
        }
        return true;
    }

    @Override
    public void handleTimeout(ConsulContext t) {
        throw new InternalServerException(String.format("Operation timed out. Consul agents didn't join in time %s", t.getTargets()));
    }

    @Override
    public String successMessage(ConsulContext t) {
        return String.format("Consul agents successfully joined '%s'", t.getTargets());
    }

}
