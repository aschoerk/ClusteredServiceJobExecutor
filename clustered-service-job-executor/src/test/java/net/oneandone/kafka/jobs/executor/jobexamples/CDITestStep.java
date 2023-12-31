package net.oneandone.kafka.jobs.executor.jobexamples;

import java.util.HashSet;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.oneandone.kafka.jobs.api.Step;
import net.oneandone.kafka.jobs.api.StepResult;
import net.oneandone.kafka.jobs.dtos.JobDataImpl;
import net.oneandone.kafka.jobs.executor.ApiTests;
import net.oneandone.kafka.jobs.executor.cdi_scopes.CdbThreadScoped;
import net.oneandone.kafka.jobs.implementations.StepImpl;

/**
 * @author aschoerk
 */
@CdbThreadScoped
public class CDITestStep implements Step<TestContext> {

    Logger logger = LoggerFactory.getLogger("ApiTests");

    public static AtomicInteger collisionsDetected = new AtomicInteger();

    public static AtomicLong stepEntered = new AtomicLong();
    public static AtomicLong stepLeft = new AtomicLong();
    /**
     * Instance variable to check if ThreadScoped is maintained despite encapsulation
     */
    AtomicBoolean used = new AtomicBoolean(false);

    public static AtomicInteger staticThreadCount = new AtomicInteger(0);

    static AtomicLong callCount = new AtomicLong(0L);

    static Map<String, String> handlingGroups = new ConcurrentHashMap<>();

    Random random = new Random();

    public static int successesInSequence = 4;

    public static void initStatics() {
        successesInSequence = 4;
        handlingGroups.clear();
        callCount.set(0);
        stepLeft.set(0);
        stepEntered.set(0);
        collisionsDetected.set(0);
    }

    HashSet<Long> correlationIdsSeen = new HashSet<>();

    @Override
    public StepResult handle(final TestContext context) {
        int threads = staticThreadCount.incrementAndGet();
        stepEntered.incrementAndGet();
        final JobDataImpl jobData = StepImpl.getJobData();
        try {
            if (correlationIdsSeen.contains(context.getCorrelationId())) {
                logger.error("Multiple Handling of E: {} grouped {} id: {} step: {} retry: {} part:  {} offs: {}",
                        StepImpl.getBeans().getNodeId(), context.groupId, jobData.getId(), jobData.getStep(),
                        jobData.getRetries(), jobData.getPartition(), jobData.getOffset());
            } else {
                correlationIdsSeen.add(context.getCorrelationId());
            }
            Thread.sleep(random.nextInt(10));
            ApiTests.logger.trace("Handle was called Threads: {} ", threads);
            if (context.groupId != null) {
                ApiTests.logger.trace("Starting E: {} grouped {} id: {} step: {} retry: {} part:  {} offs: {} ",
                        StepImpl.getBeans().getNodeId(),
                        context.groupId, jobData.getId(), jobData.getStep(), jobData.getRetries(), jobData.getPartition(),
                        jobData.getOffset());
            }
            if(!used.compareAndSet(false, true)) {
                collisionsDetected.incrementAndGet();
                logger.error("Collision in entering threadscoped Step");
            }
            if (context.groupId != null) {
                if (handlingGroups.containsKey(context.groupId)) {
                    final String threadName = handlingGroups.get(context.groupId);
                    logger.error("Group {} already handled by Thread:  {}", context.groupId,threadName);
                    return StepResult.errorResult(threadName);
                } else {
                    handlingGroups.put(context.getGroupId(), Thread.currentThread().getName());
                }
            }
            Thread.sleep(random.nextInt(10));
            context.i++;
            if ((successesInSequence != 0) && ((callCount.incrementAndGet() % (successesInSequence + 1)) == 0)) {
                return StepResult.DELAY.error("repeat every "+ successesInSequence + " calls please");
            } else {
                return StepResult.DONE;
            }
        } catch (Exception e) {
            logger.error("Exception in CDITestStep", e);
            throw new RuntimeException(e);
        } finally {
            context.incCorrelationId();
            staticThreadCount.decrementAndGet();
            if(!used.compareAndSet(true, false)) {
                collisionsDetected.incrementAndGet();
                logger.error("Collision in exiting threadscoped Step");
            }
            if (context.groupId != null) {
                String res = handlingGroups.remove(context.groupId);
                if (res == null) {
                    logger.error("Expected GroupEntry, {} not there anymore",context.groupId);
                } else if (!res.equals(Thread.currentThread().getName())) {
                    logger.error("Group Entry changed meanwhile to {}",res);
                }
                ApiTests.logger.trace("Ended E:{}  grouped {} id: {} step {} retry: {} part:  {} offs: {}",
                        StepImpl.getBeans().getNodeId(),
                        context.groupId,
                        jobData.getId(), jobData.getStep(), jobData.getRetries(), jobData.getPartition(),
                        jobData.getOffset());
            }
            ApiTests.logger.trace("Handle was ready  Threads: {} ", threads);
            stepLeft.incrementAndGet();
        }

    }
}
