package net.oneandone.kafka.jobs.beans;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

import net.oneandone.kafka.jobs.api.State;
import net.oneandone.kafka.jobs.dtos.TransportImpl;

/**
 * @author aschoerk
 */
public class JobsPendingHandler extends StoppableBase {
    protected final SortedSet<TransportImpl> sortedPending = Collections.synchronizedSortedSet(new TreeSet<>(new TimestampComparator()));

    private final Map<String, TransportImpl> pendingByIdentifier = Collections.synchronizedMap(new HashMap<>());

    private final int defaultWaitMillis = 10000;

    final Future pendingHandlerThread;

    public static class TimestampComparator implements Comparator<TransportImpl> {
        @Override
        public int compare(final TransportImpl o1, final TransportImpl o2) {
            int result = o1.jobData().getDate().compareTo(o2.jobData().getDate());
            return (result != 0) ? result : o1.jobData().getId().compareTo(o2.jobData().getId());
        }
    }

    public JobsPendingHandler(final Beans beans) {
        super(beans);
        pendingHandlerThread = submitLongRunning(
                () -> run()
        );
    }

    /**
     * <b>The</b> scheduling function
     * @param e the pendingEntry to be scheduled
     */
    public void schedulePending(final TransportImpl e) {
        logger.info("Node: {} Scheduling JobData: {} in {} milliseconds",
                beans.getNodeId(),
                e.jobData().getId(),
                Duration.between(Instant.now(),e.jobData().getDate()).toMillis());
        removePending(e.jobData().getId(), false);
        pendingByIdentifier.put(e.jobData().getId(), e);
        sortedPending.add(e);
        synchronized (this) {
            if(sortedPending.size() > 0 && sortedPending.first().equals(e)) {
                this.notify();
            }
        }
    }

    /**
     * Remove a pendingEntry, if enfore is true log an error if it is gnerally found but no entry is currently scheduled
     * @param jobDataId the identifier of the entry to be removed
     * @param enforce if true log an error if it is gnerally found but no entry is currently scheduled
     */
    private void removePending(final String jobDataId, boolean enforce) {
        logger.trace("Removing pending {}", jobDataId);
        TransportImpl e = pendingByIdentifier.get(jobDataId);
        pendingByIdentifier.remove(jobDataId);
        if(e != null) {
            boolean result = sortedPending.remove(e);
            if (!result && enforce) {
                logger.error("Could not remove pending {} ", e.jobData().getId());
            }
        }
    }

    public void run() {
        initThreadName("JobsPendingHandler");
        setRunning();
        try {
            while (!doShutDown()) {
                loopBody();
            }
        } finally {
            setRunning(false);
        }
    }

    void loopBody() {
        selectAndExecute();
        Duration toWait;
        toWait = determineWaitTime();
        waitOrAcceptNotify(toWait);
    }

    private void waitOrAcceptNotify(final Duration toWait) {
        if(!toWait.isNegative()) {
            try {
                long toWaitTime = toWait.toMillis();
                if (toWaitTime > 500) {
                    toWaitTime = 500;
                }
                if (sortedPending.size() > 0) {
                    logger.trace("Waiting for notify or {} milliseconds next entry {} jobdata: {}", toWaitTime, sortedPending.first().context(), sortedPending.first().jobData());
                }
                if (toWaitTime > 0) {
                    synchronized (this) {
                        this.wait(toWaitTime);
                    }
                }
            } catch (InterruptedException e) {
                if (!doShutDown()) {
                    logger.error("JobsPendingHandler N: {} got interrupted {}", beans.getNodeId(), e);
                }
                else {
                    logger.info("JobsPendingHandler N: {} got interrupted {}", beans.getNodeId(), e);
                }
            }
        }
    }

    private Duration determineWaitTime() {
        Duration toWait;
        if(sortedPending.size() > 0) {
            TransportImpl nextTask = sortedPending.first();
            toWait = Duration.between(beans.getContainer().getClock().instant(), nextTask.jobData().getDate()).plusMillis(1);
        }
        else {
            toWait = Duration.ofMillis(defaultWaitMillis);
        }
        return toWait;
    }

    private void selectAndExecute() {
        while ((sortedPending.size() > 0) && !sortedPending.first().jobData().getDate().isAfter(beans.getContainer().getClock().instant())) {
            TransportImpl pendingTask = sortedPending.first();
            sortedPending.remove(pendingTask);
            logger.info("Executing Pending: {}", pendingTask.jobData());
            try {
                if(Objects.requireNonNull(pendingTask.jobData().getState()) == State.DELAYED) {
                    beans.getMetricCounts().incWokenUpDelayed();
                    beans.getJobTools().prepareJobDataForRunning(pendingTask.jobData());
                    beans.getSender().send(pendingTask);
                }
            } catch (Throwable t) {
                logger.error(String.format("Executing PendingTask: %s Exception:", pendingTask.jobData().getId()), t);
            }
        }
    }

    @Override
    public void setShutDown() {
        super.setShutDown();
        waitForThreads(pendingHandlerThread);
        waitForStoppables(this);
        sortedPending.stream().collect(Collectors.toList()).forEach(p -> beans.getSender().send(p));
    }
}
