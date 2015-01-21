package com.sequenceiq.it.cloudbreak;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ContextConfiguration;
import org.testng.Assert;
import org.testng.annotations.AfterSuite;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Optional;
import org.testng.annotations.Parameters;
import org.testng.annotations.Test;

import com.jayway.restassured.http.ContentType;
import com.jayway.restassured.response.Response;
import com.sequenceiq.it.IntegrationTestContext;
import com.sequenceiq.it.config.IntegrationTestConfiguration;
import com.sequenceiq.it.util.FreeMarkerUtil;
import com.sequenceiq.it.util.RestUtil;

import freemarker.template.Template;

@ContextConfiguration(classes = IntegrationTestConfiguration.class)
public class StackCreationTest extends AbstractCloudbreakIntegrationTest {
    @Autowired
    private Template stackCreationTemplate;

    @BeforeMethod
    public void setContextParams() {
//        if (StringUtils.hasLength(templateName)) {
//            itContext.putContextParam(CloudbreakITContextConstants.TEMPLATE_ID, getResourceIdByName("/user/templates/{name}", templateName));
//        }
//        if (StringUtils.hasLength(credentialName)) {
//            itContext.putContextParam(CloudbreakITContextConstants.CREDENTIAL_ID, getResourceIdByName("/user/credentials/{name}", credentialName));
//        }
        Assert.assertNotNull(itContext.getContextParam(CloudbreakITContextConstants.TEMPLATE_ID, List.class), "Template id is mandatory.");
        Assert.assertNotNull(itContext.getContextParam(CloudbreakITContextConstants.CREDENTIAL_ID), "Credential id is mandatory.");
    }

    @Test
    @Parameters({ "stackName", "region", "ambariUser", "ambariPassword" })
    public void testStackCreation(@Optional("testing1") String stackName, @Optional("EUROPE_WEST1_B") String region, @Optional("admin") String ambariUser,
            @Optional("admin") String ambariPassword) {
        // GIVEN
        Map<String, Object> templateModel = new HashMap<>();
        templateModel.put("stackName", stackName);
        templateModel.put("region", region);
        templateModel.put("ambariUser", ambariUser);
        templateModel.put("ambariPassword", ambariPassword);
        templateModel.put("instanceGroups", itContext.getContextParam(CloudbreakITContextConstants.TEMPLATE_ID, List.class));
        templateModel.put("credentialId", itContext.getContextParam(CloudbreakITContextConstants.CREDENTIAL_ID));
        // WHEN
        Response resourceCreationResponse = RestUtil.createEntityRequest(itContext.getContextParam(CloudbreakITContextConstants.CLOUDBREAK_SERVER),
                itContext.getContextParam(IntegrationTestContext.AUTH_TOKEN), FreeMarkerUtil.renderTemplate(stackCreationTemplate, templateModel))
                .log().all().post("/user/stacks");
        // THEN
        checkResponse(resourceCreationResponse, HttpStatus.CREATED, ContentType.JSON);
        String stackId = resourceCreationResponse.jsonPath().getString("id");
        itContext.putCleanUpParam(CloudbreakITContextConstants.STACK_ID, stackId);
        waitForStackStatus(stackId, "AVAILABLE");
        itContext.putContextParam(CloudbreakITContextConstants.STACK_ID, stackId);
    }

    @AfterSuite(groups = "stack")
    public void cleanUp() {
        String stackId = itContext.getCleanUpParameter(CloudbreakITContextConstants.STACK_ID);
        if (stackId != null) {
            RestUtil.entityPathRequest(itContext.getContextParam(CloudbreakITContextConstants.CLOUDBREAK_SERVER),
                    itContext.getContextParam(IntegrationTestContext.AUTH_TOKEN), "stackId", stackId).delete("/stacks/{stackId}");
            waitForStackStatus(stackId, "TERMINATED");
        }
    }
}
