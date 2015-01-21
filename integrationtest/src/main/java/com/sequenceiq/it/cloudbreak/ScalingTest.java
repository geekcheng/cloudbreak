package com.sequenceiq.it.cloudbreak;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.util.StringUtils;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Optional;
import org.testng.annotations.Parameters;
import org.testng.annotations.Test;

import com.jayway.restassured.response.Response;
import com.jayway.restassured.specification.RequestSpecification;
import com.sequenceiq.ambari.client.AmbariClient;
import com.sequenceiq.it.IntegrationTestContext;
import com.sequenceiq.it.config.IntegrationTestConfiguration;
import com.sequenceiq.it.util.FreeMarkerUtil;
import com.sequenceiq.it.util.RestUtil;

import freemarker.template.Template;

@ContextConfiguration(classes = IntegrationTestConfiguration.class)
public class ScalingTest extends AbstractCloudbreakIntegrationTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(ScalingTest.class);

    @Autowired
    Template hostgroupAdjustmentTemplate;

    @BeforeMethod
    @Parameters({ "stackName" })
    public void setContextParameters(@Optional("") String stackName) {
        if (StringUtils.hasLength(stackName)) {
            itContext.putContextParam(CloudbreakITContextConstants.STACK_ID, getResourceIdByName("/user/stacks/{name}", stackName));
        }
        Assert.assertNotNull(itContext.getContextParam(CloudbreakITContextConstants.STACK_ID), "Stack id is mandatory.");
    }

    @Test
    @Parameters({ "hostGroup", "scalingAdjustment" })
    public void testScaling(@Optional("slave_1") String hostGroup, @Optional("1") int scalingAdjustment) {
        // GIVEN
        String stackId = itContext.getContextParam(CloudbreakITContextConstants.STACK_ID);
        Map<String, Object> templateModel = new HashMap<>();
        templateModel.put("hostGroup", hostGroup);
        templateModel.put("scalingAdjustment", String.valueOf(scalingAdjustment));
        RequestSpecification stackRequest = RestUtil.entityPathRequest(itContext.getContextParam(CloudbreakITContextConstants.CLOUDBREAK_SERVER),
                itContext.getContextParam(IntegrationTestContext.AUTH_TOKEN), "stackId", itContext.getContextParam(CloudbreakITContextConstants.STACK_ID)).
                body(FreeMarkerUtil.renderTemplate(hostgroupAdjustmentTemplate, templateModel)).basePath("/stacks/{stackId}").log().all();
        RequestSpecification hostGroupRequest = RestUtil.entityPathRequest(itContext.getContextParam(CloudbreakITContextConstants.CLOUDBREAK_SERVER),
                itContext.getContextParam(IntegrationTestContext.AUTH_TOKEN), "stackId", itContext.getContextParam(CloudbreakITContextConstants.STACK_ID)).
                body(FreeMarkerUtil.renderTemplate(hostgroupAdjustmentTemplate, templateModel)).basePath("/stacks/{stackId}/cluster").log().all();
        // WHEN
        if (scalingAdjustment < 0) {
            putRequest(hostGroupRequest, stackId);
            putRequest(stackRequest, stackId);
        } else {
            putRequest(stackRequest, stackId);
            putRequest(hostGroupRequest, stackId);
        }
        // THEN
        checkCluster(stackId, hostGroup);
    }

    private void putRequest(RequestSpecification request, String stackId) {
        Response response = request.put();
        response.then().statusCode(HttpStatus.NO_CONTENT.value());
        waitForStackStatus(stackId, "AVAILABLE");
    }

    protected void checkCluster(String stackId, String hostGroup) {
        Response stackResponse = RestUtil.getRequest(itContext.getContextParam(CloudbreakITContextConstants.CLOUDBREAK_SERVER),
                itContext.getContextParam(IntegrationTestContext.AUTH_TOKEN)).pathParam("stackId", stackId)
                .get("/stacks/{stackId}");

        Assert.assertEquals("AVAILABLE", stackResponse.jsonPath().get("cluster.status"), "The cluster hasn't been started!");
        Assert.assertEquals("AVAILABLE", stackResponse.jsonPath().get("status"), "The stack hasn't been started!");

        String ambariIp = stackResponse.jsonPath().get("ambariServerIp");
        Assert.assertNotNull(ambariIp, "The Ambari IP is not available!");

        AmbariClient ambariClient = new AmbariClient(ambariIp);
        Assert.assertEquals("RUNNING", ambariClient.healthCheck(), "The Ambari server is not running!");
        Assert.assertEquals(ambariClient.getClusterHosts().size(), getNodeCount(stackResponse),
                "The number of cluster nodes in the stack differs from the number of nodes registered in ambari");
    }

    private int getNodeCount(Response stackResponse) {
        List<Map<String, Object>> instanceGroups = stackResponse.jsonPath().get("instanceGroups");
        int nodeCount = 0;
        for (Map<String, Object> instanceGroup : instanceGroups) {
            nodeCount += (Integer)instanceGroup.get("nodeCount");
        }
        return nodeCount;
    }
}
