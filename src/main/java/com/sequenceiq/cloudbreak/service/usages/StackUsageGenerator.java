package com.sequenceiq.cloudbreak.service.usages;

import static java.util.Calendar.HOUR_OF_DAY;
import static java.util.Calendar.MILLISECOND;
import static java.util.Calendar.MINUTE;
import static java.util.Calendar.SECOND;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.sequenceiq.cloudbreak.domain.BillingStatus;
import com.sequenceiq.cloudbreak.domain.CloudbreakEvent;
import com.sequenceiq.cloudbreak.domain.CloudbreakUsage;
import com.sequenceiq.cloudbreak.logger.MDCBuilder;
import com.sequenceiq.cloudbreak.repository.CloudbreakEventRepository;

@Component
public class StackUsageGenerator {
    private static final Logger LOGGER = LoggerFactory.getLogger(StackUsageGenerator.class);
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd");

    @Autowired
    private CloudbreakEventRepository eventRepository;

    @Autowired
    private IntervalStackUsageGenerator iSUG;

    public List<CloudbreakUsage> generate(List<CloudbreakEvent> stackEvents) {
        List<CloudbreakUsage> stackUsages = new LinkedList<>();
        CloudbreakEvent actEvent = null;
        try {
            CloudbreakEvent start = null;
            for (CloudbreakEvent cbEvent : stackEvents) {
                MDCBuilder.buildMdcContext(cbEvent);
                actEvent = cbEvent;
                if (isStartEvent(cbEvent) && start == null) {
                    start = cbEvent;
                } else if (start != null && start.getEventTimestamp().before(cbEvent.getEventTimestamp())) {
                    if (isStopEvent(cbEvent)) {
                        addAllGeneratedUsages(stackUsages, start, cbEvent);
                        start = null;
                    } else if (isBillingChangedEvent(cbEvent)) {
                        addAllGeneratedUsages(stackUsages, start, cbEvent);
                        start = cbEvent;
                    }
                }
            }

            generateRunningStackUsage(stackUsages, start);
            stackUsages = sumUsagesByDay(stackUsages);
        } catch (ParseException e) {
            LOGGER.error("Usage generation is failed for stack(id:{})! Invalid date in event(id:{})! Ex: {}", actEvent.getStackId(), actEvent.getId(), e);
            throw new IllegalStateException(e);
        }
        return stackUsages;
    }


    private boolean isStopEvent(CloudbreakEvent event) {
        return BillingStatus.BILLING_STOPPED.name().equals(event.getEventType());
    }

    private boolean isStartEvent(CloudbreakEvent event) {
        return BillingStatus.BILLING_STARTED.name().equals(event.getEventType());
    }

    private boolean isBillingChangedEvent(CloudbreakEvent event) {
        return BillingStatus.BILLING_CHANGED.name().equals(event.getEventType());
    }

    private void addAllGeneratedUsages(List<CloudbreakUsage> stackUsages, CloudbreakEvent start, CloudbreakEvent cbEvent) throws ParseException {
        Map<String, CloudbreakUsage> usages = iSUG.getUsages(start.getEventTimestamp(), cbEvent.getEventTimestamp(), start);
        stackUsages.addAll(usages.values());
    }

    private void generateRunningStackUsage(List<CloudbreakUsage> dailyCbUsages, CloudbreakEvent startEvent) throws ParseException {
        if (startEvent != null) {
            Calendar cal = Calendar.getInstance();
            setDayToBeginning(cal);
            Date billingStart = startEvent.getEventTimestamp();
            Map<String, CloudbreakUsage> usages = iSUG.getUsages(billingStart, cal.getTime(), startEvent);
            dailyCbUsages.addAll(usages.values());

            //get overflowed minutes from the start event
            Calendar start = Calendar.getInstance();
            start.setTime(billingStart);
            cal.set(MINUTE, start.get(MINUTE));
            //save billing start event for daily usage generation
            CloudbreakEvent newBilling = createBillingStarterCloudbreakEvent(startEvent, cal);
            eventRepository.save(newBilling);
            LOGGER.debug("BILLING_STARTED is created with date:{} for running stack {}.", cal.getTime(), newBilling.getStackId());
        }
    }

    private List<CloudbreakUsage> sumUsagesByDay(List<CloudbreakUsage> usages) {
        sortUsagesByDate(usages);
        Map<String, CloudbreakUsage> usagesByDay = new HashMap<>();

        for (CloudbreakUsage usage : usages) {
            String day = DATE_FORMAT.format(usage.getDay());
            CloudbreakUsage usageOfDay = usagesByDay.get(day);
            if (usageOfDay != null) {
                long sum = usageOfDay.getInstanceHours() + usage.getInstanceHours();
                usageOfDay.setInstanceHours(sum);
            } else {
                usagesByDay.put(day, usage);
            }
        }

        return new LinkedList<>(usagesByDay.values());
    }

    private void sortUsagesByDate(List<CloudbreakUsage> usageList) {
        Collections.sort(usageList, new Comparator<CloudbreakUsage>() {
            @Override
            public int compare(CloudbreakUsage actual, CloudbreakUsage next) {
                return actual.getDay().compareTo(next.getDay());
            }
        });
    }

    private void setDayToBeginning(Calendar calendar) {
        calendar.set(HOUR_OF_DAY, 0);
        calendar.set(MINUTE, 0);
        calendar.set(SECOND, 0);
        calendar.set(MILLISECOND, 0);
    }

    private CloudbreakEvent createBillingStarterCloudbreakEvent(CloudbreakEvent startEvent, Calendar cal) {
        CloudbreakEvent event = new CloudbreakEvent();
        event.setEventType(BillingStatus.BILLING_STARTED.name());
        event.setAccount(startEvent.getAccount());
        event.setOwner(startEvent.getOwner());
        event.setEventMessage(startEvent.getEventMessage());
        event.setBlueprintId(startEvent.getBlueprintId());
        event.setBlueprintName(startEvent.getBlueprintName());
        event.setEventTimestamp(cal.getTime());
        event.setVmType(startEvent.getVmType());
        event.setCloud(startEvent.getCloud());
        event.setRegion(startEvent.getRegion());
        event.setStackId(startEvent.getStackId());
        event.setStackStatus(startEvent.getStackStatus());
        event.setStackName(startEvent.getStackName());
        event.setNodeCount(startEvent.getNodeCount());
        event.setInstanceGroup(startEvent.getInstanceGroup());
        return event;
    }

    void setiSUG(IntervalStackUsageGenerator iSUG) {
        this.iSUG = iSUG;
    }
}
