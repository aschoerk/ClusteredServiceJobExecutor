package net.oneandone.kafka.jobs.api;

import java.time.Clock;
import java.util.concurrent.Future;

import net.oneandone.kafka.jobs.api.events.Event;

/**
 * Used to support container characteristics like Thread-Management.
 */
public interface Container extends net.oneandone.kafka.clusteredjobs.api.Container {

    /**
     * return the name of the topic to use for synchronization. Must have exactly one partition
     *
     * @return the name of the topic to use for synchronization. Must have exactly one partition
     */
    @Override
    default String getSyncTopicName() {return "SyncTopic"; }

    /**
     * @return the name of the topic used to exchange JobData
     */
    default String getJobDataTopicName() { return "JobDataTopic"; }


    /**
     * the kafka bootstrapservers
     *
     * @return the kafka bootstrapservers
     */
    @Override
    String getBootstrapServers();



    /**
     * signal the beginning of a threadusage.
     * Must be always paired with stopThreadUsage.
     * Only valid for threads created by createThread.
     */
    default void startThreadUsage() {}

    /**
     * signal that a thread is not used anymore.
     * This is only valid if startThreadUsage was called before.
     */
    default void stopThreadUsage() {}

    /**
     * marshal all contexts. if it returns null, Gson will be used
     * @param context the context to convert to json
     * @return the json representing the value of context as String
     * @param <T> the Type of the context.
     */
    default <T> String marshal(T context) {return null;}

    /**
     * convert the string-param to an object of Type clazz
     * @param value the string to be converted
     * @param clazz the expected class
     * @return null if no conversion is possible, otherwise the object.
     * @param <T> the Class of the context
     */
    default <T> T unmarshal(String value, Class<T> clazz) {return null;}

    /**
     * the clock to be used for Instant-Creation
     *
     * @return the clock to be used for Instant-Creation
     */
    default Clock getClock() { return Clock.systemUTC(); }

    /**
     * return the containers interface for (user)transaction handling
     *
     * @return the containers interface for (user)transaction handling
     */
    default Transaction getTransaction() { return null; }

    /**
     * fire an Event
     *
     * @param event the event to be fired.
     */
    default void fire(Event event) {}

    /**
     * Allows to use the Container-Threadpooling. This pool is used exclusively for steps
     * @param runnable The runnable to execute when starting the thread
     * @return the thread created in the container environment
     */
    Future submitInWorkerThread(Runnable runnable);



    @Override
    default Configuration getConfiguration() {
        return new Configuration() {
        };
    }

    default RemoteExecutor[] getRemoteExecutors() { return new RemoteExecutor[0]; }
}
