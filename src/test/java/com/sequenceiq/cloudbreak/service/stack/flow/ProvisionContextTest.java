package com.sequenceiq.cloudbreak.service.stack.flow;

import static org.mockito.BDDMockito.given;
import static org.mockito.Matchers.anyList;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Matchers.anyMap;
import static org.mockito.Matchers.anyObject;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.test.util.ReflectionTestUtils;

import com.sequenceiq.cloudbreak.conf.ReactorConfig;
import com.sequenceiq.cloudbreak.domain.CloudPlatform;
import com.sequenceiq.cloudbreak.domain.Stack;
import com.sequenceiq.cloudbreak.domain.Status;
import com.sequenceiq.cloudbreak.repository.RetryingStackUpdater;
import com.sequenceiq.cloudbreak.repository.StackRepository;
import com.sequenceiq.cloudbreak.service.ServiceTestUtils;
import com.sequenceiq.cloudbreak.service.stack.connector.CloudPlatformConnector;
import com.sequenceiq.cloudbreak.service.stack.connector.UserDataBuilder;
import com.sequenceiq.cloudbreak.service.stack.flow.dummy.DummyResourceBuilderInit;
import com.sequenceiq.cloudbreak.service.stack.flow.dummy.instance.DummyAttachedDiskResourceBuilder;
import com.sequenceiq.cloudbreak.service.stack.flow.dummy.instance.DummyExVirtualMachineResourceBuilder;
import com.sequenceiq.cloudbreak.service.stack.flow.dummy.instance.DummyVirtualMachineResourceBuilder;
import com.sequenceiq.cloudbreak.service.stack.flow.dummy.network.DummyExNetworkResourceBuilder;
import com.sequenceiq.cloudbreak.service.stack.flow.dummy.network.DummyNetworkResourceBuilder;
import com.sequenceiq.cloudbreak.service.stack.resource.ResourceBuilder;
import com.sequenceiq.cloudbreak.service.stack.resource.ResourceBuilderInit;

import reactor.core.Reactor;
import reactor.event.Event;

public class ProvisionContextTest {

    private static final String DUMMY_NAME = "dummyName";

    @InjectMocks
    private ProvisionContext underTest;

    @Mock
    private StackRepository stackRepository;

    @Mock
    private RetryingStackUpdater stackUpdater;

    @Mock
    private Map<CloudPlatform, CloudPlatformConnector> cloudPlatformConnectors;

    @Mock
    private Reactor reactor;

    @Mock
    private UserDataBuilder userDataBuilder;

    @Mock
    private AsyncTaskExecutor resourceBuilderExecutor;

    @Mock
    private Map<CloudPlatform, ResourceBuilderInit> resourceBuilderInits;

    @Mock
    private Map<CloudPlatform, List<ResourceBuilder>> instanceResourceBuilders;

    @Mock
    private Map<CloudPlatform, List<ResourceBuilder>> networkResourceBuilders;

    private Map<String, Object> setupProperties = new HashMap<>();

    private Map<String, String> userDataParams = new HashMap<>();

    private CloudPlatform cloudPlatform = CloudPlatform.GCC;

    private Stack stack;

    @Before
    public void setUp() {
        underTest = new ProvisionContext();
        MockitoAnnotations.initMocks(this);
        stack = ServiceTestUtils.createStack();

        Map<CloudPlatform, ResourceBuilderInit> resourceBuilderInits = new HashMap<>();
        resourceBuilderInits.put(CloudPlatform.GCC, new DummyResourceBuilderInit());
        ReflectionTestUtils.setField(underTest, "resourceBuilderInits", resourceBuilderInits);

        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(100);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("resourceBuilderExecutor-");
        executor.initialize();
        ReflectionTestUtils.setField(underTest, "resourceBuilderExecutor", executor);
    }

    @Test
    public void buildStackWhenAllResourceBuilderWorksFine() {
        prepareInstanceResourceBuilders();
        prepareNetWorkResourceBuilders();

        // GIVEN
        given(stackRepository.findOneWithLists(1L)).willReturn(stack);

        given(stackUpdater.updateStackStatus(anyLong(), any(Status.class), anyString())).willReturn(stack);
        given(stackUpdater.updateStackStatusReason(anyLong(), anyString())).willReturn(stack);
        given(stackUpdater.addStackResources(anyLong(), anyList())).willReturn(stack);

        given(reactor.notify(any(), any(Event.class))).willReturn(null);

        given(userDataBuilder.build(any(CloudPlatform.class), anyString(), anyMap())).willReturn("userdata dummy");
        // WHEN
        underTest.buildStack(cloudPlatform, 1L, setupProperties, userDataParams);
        // THEN
        verify(stackUpdater, times(9)).addStackResources(anyLong(), anyList());
        verify(reactor, times(1)).notify(eq(ReactorConfig.PROVISION_COMPLETE_EVENT), Event.wrap(anyObject()));
    }

    @Test
    public void buildStackWhenNetworkResourceBuilderDropException() {
        prepareInstanceResourceBuilders();
        prepareExNetWorkResourceBuilders();

        // GIVEN
        given(stackRepository.findOneWithLists(1L)).willReturn(stack);

        given(stackUpdater.updateStackStatus(anyLong(), any(Status.class), anyString())).willReturn(stack);
        given(stackUpdater.updateStackStatusReason(anyLong(), anyString())).willReturn(stack);
        given(stackUpdater.addStackResources(anyLong(), anyList())).willReturn(stack);

        given(reactor.notify(any(), any(Event.class))).willReturn(null);

        given(userDataBuilder.build(any(CloudPlatform.class), anyString(), anyMap())).willReturn("userdata dummy");
        // WHEN
        underTest.buildStack(cloudPlatform, 1L, setupProperties, userDataParams);
        // THEN
        verify(stackUpdater, times(1)).addStackResources(anyLong(), anyList());
        verify(reactor, times(1)).notify(eq(ReactorConfig.STACK_CREATE_FAILED_EVENT), Event.wrap(anyObject()));
    }

    @Test
    public void buildStackWhenInstanceResourceBuilderDropException() {
        prepareExInstanceResourceBuilders();
        prepareNetWorkResourceBuilders();

        // GIVEN
        given(stackRepository.findOneWithLists(1L)).willReturn(stack);

        given(stackUpdater.updateStackStatus(anyLong(), any(Status.class), anyString())).willReturn(stack);
        given(stackUpdater.updateStackStatusReason(anyLong(), anyString())).willReturn(stack);
        given(stackUpdater.addStackResources(anyLong(), anyList())).willReturn(stack);

        given(reactor.notify(any(), any(Event.class))).willReturn(null);

        given(userDataBuilder.build(any(CloudPlatform.class), anyString(), anyMap())).willReturn("userdata dummy");
        // WHEN
        underTest.buildStack(cloudPlatform, 1L, setupProperties, userDataParams);
        // THEN
        verify(stackUpdater, times(9)).addStackResources(anyLong(), anyList());
        verify(reactor, times(1)).notify(eq(ReactorConfig.STACK_CREATE_FAILED_EVENT), Event.wrap(anyObject()));
    }

    private void prepareNetWorkResourceBuilders() {
        Map<CloudPlatform, List<ResourceBuilder>> networkResourceBuilders = new HashMap<>();
        List<ResourceBuilder> networks = new ArrayList<>();
        networks.add(0, new DummyNetworkResourceBuilder());
        networkResourceBuilders.put(CloudPlatform.GCC, networks);
        ReflectionTestUtils.setField(underTest, "networkResourceBuilders", networkResourceBuilders);
    }

    private void prepareExNetWorkResourceBuilders() {
        Map<CloudPlatform, List<ResourceBuilder>> networkResourceBuilders = new HashMap<>();
        List<ResourceBuilder> networks = new ArrayList<>();
        networks.add(0, new DummyExNetworkResourceBuilder());
        networkResourceBuilders.put(CloudPlatform.GCC, networks);
        ReflectionTestUtils.setField(underTest, "networkResourceBuilders", networkResourceBuilders);
    }

    private void prepareExInstanceResourceBuilders() {
        Map<CloudPlatform, List<ResourceBuilder>> instanceResourceBuilders = new HashMap<>();
        List<ResourceBuilder> instances = new ArrayList<>();
        instances.add(0, new DummyAttachedDiskResourceBuilder());
        instances.add(1, new DummyExVirtualMachineResourceBuilder());
        instanceResourceBuilders.put(CloudPlatform.GCC, instances);
        ReflectionTestUtils.setField(underTest, "instanceResourceBuilders", instanceResourceBuilders);
    }

    private void prepareInstanceResourceBuilders() {
        Map<CloudPlatform, List<ResourceBuilder>> instanceResourceBuilders = new HashMap<>();
        List<ResourceBuilder> instances = new ArrayList<>();
        instances.add(0, new DummyAttachedDiskResourceBuilder());
        instances.add(1, new DummyVirtualMachineResourceBuilder());
        instanceResourceBuilders.put(CloudPlatform.GCC, instances);
        ReflectionTestUtils.setField(underTest, "instanceResourceBuilders", instanceResourceBuilders);
    }
}
