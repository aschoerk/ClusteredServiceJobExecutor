package net.oneandone.kafka.jobs.beans;

import java.util.Map;

import net.oneandone.kafka.jobs.api.Engine;
import net.oneandone.kafka.jobs.api.Job;
import net.oneandone.kafka.jobs.api.KjeException;
import net.oneandone.kafka.jobs.api.RemoteExecutor;
import net.oneandone.kafka.jobs.api.Transport;
import net.oneandone.kafka.jobs.dtos.JobDataImpl;
import net.oneandone.kafka.jobs.dtos.JobDataState;
import net.oneandone.kafka.jobs.dtos.TransportImpl;
import net.oneandone.kafka.jobs.implementations.JobImpl;
import net.oneandone.kafka.jobs.tools.JsonMarshaller;
import net.oneandone.kafka.jobs.tools.ResumeJob;
import net.oneandone.kafka.jobs.tools.ResumeJobData;

/**
 * @author aschoerk
 */
public class EngineImpl extends StoppableBase implements Engine {


    public EngineImpl(Beans beans) {
        super(beans);
    }


    @Override
    public <T> void register(final Job<T> job, Class<T> clazz) {
        JobImpl<T> result = new JobImpl<>(job, clazz);
        beans.getJobs().put(result.signature(), result);
    }

    public void register(final RemoteExecutor remoteExecutor) {
        beans.getRemoteExecutors().addExecutor(remoteExecutor);
    }

    @Override
    public <T> Transport create(final Job<T> job, final T context) {
        return create(job, context, null);
    }

    @Override
    public <T> Transport create(final Job<T> job, final T context, String correlationId) {

        JobImpl<T> jobImpl = beans.getJobs().get(job.signature());

        if(jobImpl == null) {
            throw new KjeException("expected job first to be registered with executor");
        }

        if(correlationId != null) {
            Map<String, JobDataState> perJobName = beans.getJobDataCorrelationIds().get(job.name());
            if(perJobName != null) {
                JobDataState state = perJobName.get(correlationId);
                if(state != null) {
                    TransportImpl existing = beans.getReceiver().readJob(state);
                    return existing;
                }
            }
        }

        JobDataImpl jobData = new JobDataImpl(jobImpl, (Class<T>) context.getClass(), correlationId, beans.getContainer());

        beans.getJobTools().prepareJobDataForRunning(jobData);

        TransportImpl contextImpl = new TransportImpl(jobData, context, context.getClass(), beans);

        beans.getSender().send(contextImpl);

        return contextImpl;
    }

    @Override
    public <R> void resume(final String jobID, final R resumeData, String correlationID) {
        String data = beans.getContainer().marshal(resumeData);
        if(data == null) {
            data = JsonMarshaller.gson.toJson(resumeData);
        }
        if(correlationID != null) {
            create(new ResumeJob(beans), new ResumeJobData(jobID, correlationID, data, resumeData.getClass()), jobID + "__" + correlationID);
        }
        else {
            create(new ResumeJob(beans), new ResumeJobData(jobID, correlationID, data, resumeData.getClass()));
        }
    }

    @Override
    public <R> void resume(final String jobID, final R resumeData) {
        resume(jobID, resumeData, null);
    }

}