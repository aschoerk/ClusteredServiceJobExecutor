package net.oneandone.kafka.jobs.beans;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;

import net.oneandone.kafka.clusteredjobs.NodeFactory;
import net.oneandone.kafka.clusteredjobs.api.ClusterTask;
import net.oneandone.kafka.clusteredjobs.api.Node;
import net.oneandone.kafka.clusteredjobs.api.TaskDefaults;
import net.oneandone.kafka.clusteredjobs.api.TaskDefinition;
import net.oneandone.kafka.jobs.api.Container;
import net.oneandone.kafka.jobs.dtos.JobDataState;
import net.oneandone.kafka.jobs.implementations.JobImpl;

/**
 * @author aschoerk
 */
public class Beans extends StoppableBase {

    static AtomicInteger beanCounter = new AtomicInteger(0);
    private final Container container;
    private final EngineImpl engine;
    private final Executor executor;
    private final JobsSender jobsSender;
    private final Receiver receiver;
    private final JobsPendingHandler jobsPendingHandler;
    private final Map<String, JobDataState> jobDataStates;
    private final Map<String, JobImpl<?>> jobs;
    private final Map<String, JobDataState> jobDataCorrelationIds;
    private final JobTools jobTools;
    private final MetricCounts metricCounts;
    private final ClusteredJobReviver reviver;
    int count;
    private final RemoteExecutors remoteExecutors;
    private final Map<String, Queue<String>> statesByGroup;
    private final Node node;
    private final NodeFactory nodeFactory;

    public Beans(Container container, BeansFactory beansFactory) {
        super(null);
        this.container = container;
        this.count = beanCounter.incrementAndGet();
        this.jobs = beansFactory.createJobsMap();
        jobDataStates = beansFactory.createJobDataStates();
        jobDataCorrelationIds = beansFactory.createJobDataCorrelationIds();
        this.nodeFactory = beansFactory.createNodeFactory();
        this.node = beansFactory.createNode(container, nodeFactory);
        this.node.run();
        this.engine = beansFactory.createEngine(this);
        this.executor = beansFactory.createExecutor(this);
        this.jobsSender = beansFactory.createSender(this);
        this.receiver = beansFactory.createReceiver(this);
        this.jobTools = beansFactory.createJobTools(this);
        this.jobsPendingHandler = beansFactory.createPendingHandler(this);
        this.metricCounts = beansFactory.createMetricCounts(this);
        this.remoteExecutors = beansFactory.createRemoteExecutors(this);
        this.reviver = beansFactory.createReviver(this);
        this.statesByGroup = beansFactory.createStatesByGroup();



        TaskDefinition reviverDef = new TaskDefaults() {

            @Override
            public Duration getPeriod() {
                return getContainer().getConfiguration().getReviverPeriod();
            }

            @Override
            public Instant getInitialTimestamp() {
                return Instant.now();
            }

            @Override
            public String getName() {
                return "ClusteredReviver";
            }

            @Override
            public ClusterTask getClusterTask(final Node nodeP) {
                return reviver;
            }
        };
        node.register(reviverDef);
        setRunning();
    }

    public Node getNode() {
        return node;
    }

    public Map<String, JobDataState> getJobDataCorrelationIds() {
        return jobDataCorrelationIds;
    }

    public Map<String, Queue<String>> getStatesByGroup() {
        return statesByGroup;
    }

    public Container getContainer() {
        return container;
    }

    public EngineImpl getEngine() {
        return engine;
    }

    public Executor getExecutor() {
        return executor;
    }

    public JobsSender getSender() {
        return jobsSender;
    }

    public Receiver getReceiver() {
        return receiver;
    }

    public JobsPendingHandler getPendingHandler() {
        return jobsPendingHandler;
    }

    /**
     * registered jobs by signature
     *
     * @return the registered jobs by signature
     */
    public Map<String, JobImpl<?>> getInternalJobs() {
        return jobs;
    }

    /**
     * the currently last state of all jobs as
     *
     * @return the currently last state of all jobs
     */
    public Map<String, JobDataState> getJobDataStates() {
        return jobDataStates;
    }

    public JobTools getJobTools() {
        return jobTools;
    }

    public MetricCounts getMetricCounts() {
        return metricCounts;
    }

    public RemoteExecutors getRemoteExecutors() {return remoteExecutors;}

    public int getCount() {
        return count;
    }

    @Override
    public void setShutDown() {
        this.beans = this;
        super.setShutDown();
        node.shutdown();
        reviver.setShutDown();
        receiver.setShutDown();
        executor.setShutDown();
        waitForStoppables(receiver, executor, reviver);
        jobsPendingHandler.setShutDown();
        waitForStoppables(jobsPendingHandler);
        this.stopStoppables(engine, jobTools, jobsSender, metricCounts, remoteExecutors);
        setRunning(false);
    }

    public ClusteredJobReviver getReviver() {
        return reviver;
    }

    public KafkaConsumer<String, String> createConsumer(Map<String, Object> config) {
        return new KafkaConsumer<>(config);
    }

    public KafkaProducer<String, String> createProducer(Map<String, Object> config) {
        return new KafkaProducer<>(config);
    }

    public String getNodeId() {
        if (node != null) {
            return node.getUniqueNodeId();
        } else {
            return "Beans" + count;
        }
    }
}
