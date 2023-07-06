package net.oneandone.kafka.jobs.api;

import java.time.Duration;

/**
 * @author aschoerk
 */
public interface Configuration {

    default int getMaxStepsInOneLoop() { return 100; }

    default int getMaxPhase1Tries() {
        return 10;
    }

    default int getMaxPhase2Tries() {
        return 10;
    }

    default Duration getInitialWaitTimePhase1() {
        return Duration.ofSeconds(15);
    }

    default Duration getInitialWaitTimePhase2() {
        return Duration.ofSeconds(150);
    }


    default Duration getStepMaxSuspendTime() {
        return Duration.ofSeconds(7200);
    }

    default Duration getStepLockTime() {
        return Duration.ofSeconds(300);
    }

    default String getNodeName() {
        return "undefined";
    }

    default boolean preferRemoteExecution() { return true; }

    /**
     * how long to wait after a state expired before trying to revive the job-step
     * @return how long to wait after a state expired before trying to revive the job-step
     */
    default Duration getMaxDelayOfStateMessages() { return Duration.ofMillis(10000); }

    /**
     * time between searches in state-data for expired job-steps
     * @return time between searches in state-data for expired job-steps
     */
    default Duration getReviverPeriod() { return Duration.ofMillis(20000); }

}
