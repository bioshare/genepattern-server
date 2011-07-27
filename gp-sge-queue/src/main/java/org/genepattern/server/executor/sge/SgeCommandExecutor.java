package org.genepattern.server.executor.sge;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.log4j.Logger;
import org.broadinstitute.zamboni.server.batchsystem.BatchJob;
import org.broadinstitute.zamboni.server.batchsystem.sge.SgeBatchSystem;
import org.genepattern.server.config.ServerConfiguration;
import org.genepattern.server.executor.CommandExecutor;
import org.genepattern.server.executor.CommandExecutorException;
import org.genepattern.server.executor.CommandProperties;
import org.genepattern.webservice.JobInfo;
import org.genepattern.webservice.JobStatus;

import scala.Option;
import scala.Some;

/**
 * CommandExecutor for SGE with DRMAA / Java, based on Zamboni implementation, via src code provided circa July 2011.
 * 
 * @author pcarr
 *
 */
public class SgeCommandExecutor implements CommandExecutor {
    public static Logger log = Logger.getLogger(SgeCommandExecutor.class);
    
    //configuration property names; loaded from the config.file at runtime
    public enum Prop {
        SGE_PROJECT,
        SGE_ROOT,
        SGE_CELL,
        SGE_SESSION_FILE,
        SGE_BATCH_SYSTEM_NAME;
    }
    
    private SgeBatchSystem sgeBatchSystem = null;
    private final CommandProperties configurationProperties = new CommandProperties();

    public void setConfigurationFilename(String filename) {
    }

    public void setConfigurationProperties(CommandProperties properties) {
        //set properties from config.file, section for this executor
        this.configurationProperties.putAll( properties );
    }

    public void start() { 
        log.info("starting SGE CommandExecutor...");
        
        String sgeRoot = configurationProperties.getProperty(Prop.SGE_ROOT.toString(), System.getProperty(Prop.SGE_ROOT.toString()));
        String sgeCell = configurationProperties.getProperty(Prop.SGE_CELL.toString(), System.getProperty(Prop.SGE_CELL.toString()));
        String sgeProject = configurationProperties.getProperty(Prop.SGE_PROJECT.toString());
        //if the session_file is relative, assume it is relative to the resources directory (rather than the working directory)
        String sgeSessionFile = configurationProperties.getProperty(Prop.SGE_SESSION_FILE.toString());
        if (sgeSessionFile == null) {
            sgeSessionFile = System.getProperty("resources", ".") + "/conf/sge_contact.txt";
        }
        String sgeBatchSystemName = configurationProperties.getProperty(Prop.SGE_BATCH_SYSTEM_NAME.toString(), "gpSge");
        
        log.info(Prop.SGE_ROOT+"="+sgeRoot);
        log.info(Prop.SGE_CELL+"="+sgeCell);
        log.info(Prop.SGE_PROJECT+"="+sgeProject);
        log.info(Prop.SGE_SESSION_FILE+"="+sgeSessionFile);
        log.info(Prop.SGE_BATCH_SYSTEM_NAME+"="+sgeBatchSystemName);

        System.setProperty("SGE_ROOT", sgeRoot);
        System.setProperty("SGE_CELL", sgeCell);
        System.setProperty("SGE_PROJECT", sgeProject);
        System.setProperty("SGE_SESSION_FILE", sgeSessionFile);
        
        //initialize Zamboni's SGE service
        sgeBatchSystem = new SgeBatchSystem(sgeBatchSystemName);
    }

    public void stop() {
        if (sgeBatchSystem != null) {
            try {
                log.info("Shutting down SGE Batch System ...");
                sgeBatchSystem.shutDown();
                log.info("Done.");
            }
            catch (Throwable t) {
                log.error("Error shutting down SGE Batch System: "+t.getLocalizedMessage(), t);
            }
        }
    }

    public int handleRunningJob(JobInfo jobInfo) throws Exception {
        if (jobInfo == null) {
            throw new IllegalArgumentException("jobInfo is null");
        }
        if (sgeBatchSystem == null) {
            throw new Exception("sgeBatchSystem not initialized; handleRunningJob(jobId="+jobInfo.getJobNumber()+")");
        }
        
        BatchJob sgeJob = getBatchJobFromGpJobInfo( jobInfo );
        File workingDir = getWorkingDir(jobInfo);
        sgeJob.setWorkingDirectory(new scala.Some<String>(workingDir.getPath()));
        
        
        
        List<BatchJob> sgeJobs = new ArrayList<BatchJob>();
        sgeJobs.add(sgeJob);
        sgeBatchSystem.restore( scala.collection.JavaConversions.asScalaBuffer( sgeJobs ) );

        // don't change the status
        int currentStatus = JobStatus.JOB_PROCESSING;
        if (JobStatus.STATUS_MAP.get( jobInfo.getStatus() ) instanceof Integer) {
            currentStatus = (Integer) JobStatus.STATUS_MAP.get( jobInfo.getStatus() );
        }
        return currentStatus;
    }

    public void runCommand(String[] commandLine, Map<String, String> environmentVariables, File runDir, File stdoutFile, File stderrFile, JobInfo jobInfo, File stdinFile) throws CommandExecutorException {
        //TODO: handle environmentVariables
        //TODO: handle stdinFile
        
        try {
            BatchJob sgeJob = getBatchJobFromGpJobInfo( jobInfo );
            //TODO: need to optionally set the sgeJob.jobId by looking it up in the GP DB table
            sgeJob.setCommand(new scala.Some<String>(commandLine[0])); 
            String[] args = null;
            if (commandLine.length <= 1) {
                args = new String[0];
            }
            else {
                args = new String[ commandLine.length -1 ];
            }
            for(int i=1; i<commandLine.length; ++i) {
                args[i-1] = commandLine[i];
            } 
            sgeJob.setArgs( new scala.Some<String[]>(args) );
            sgeJob.setWorkingDirectory( new scala.Some<String>(runDir.getPath()) );
            sgeJob.setOutputPath( new scala.Some<String>(stdoutFile.getPath()) );
            sgeJob.setErrorPath( new scala.Some<String>(stderrFile.getPath()) );
            sgeJob.setJobName( new scala.Some<String>(""+jobInfo.getJobNumber()) );
            
            sgeJob = sgeBatchSystem.submit(sgeJob);
            //TODO: record the sge job id in the db
            log.info("submitted job to SGE, gp_job_id="+jobInfo.getJobNumber()+", sge_job_id="+sgeJob.getJobId());
        }
        catch (Throwable t) {
            throw new CommandExecutorException("Error submitting job "+jobInfo.getJobNumber()+" to SGE: "+t.getLocalizedMessage(), t);
        } 
    }


    public void terminateJob(JobInfo jobInfo) throws Exception {
        log.info("termminating SGE job, gp_job_id="+jobInfo.getJobNumber());
        BatchJob sgeJob = getBatchJobFromGpJobInfo( jobInfo );
        //TODO: need to optionally set the sgeJob.jobId by looking it up in the GP DB table 
        sgeBatchSystem.kill( sgeJob );
    }
    
    //helper methods
    /**
     * Get the working directory for the given job.
     * 
     * @param jobInfo
     * @return
     * @throws Exception
     */
    private File getWorkingDir(JobInfo jobInfo) throws Exception {
        boolean gp_3_3_3 = false;
        if (gp_3_3_3) {
            //TODO: if you are working with GP 3.3.3 or later, use JobManager#getWorkingDirectory
            //return JobManager.getWorkingDirectory(jobInfo);
        }
        
        //3.3.2 based 
        if (jobInfo == null) {
            throw new IllegalArgumentException("Can't get working directory for jobInfo=null");
        }
        if (jobInfo.getJobNumber() < 0) {
            throw new IllegalArgumentException("Can't get working directory for jobInfo.jobNumber="+jobInfo.getJobNumber());
        }

        File jobDir = null;
        try {
            ServerConfiguration.Context jobContext = ServerConfiguration.Context.getContextForJob(jobInfo);
            File rootJobDir = ServerConfiguration.instance().getRootJobDir(jobContext);
            jobDir = new File(rootJobDir, ""+jobInfo.getJobNumber());
        }
        catch (ServerConfiguration.Exception e) {
            throw new Exception(e.getLocalizedMessage());
        }
        return jobDir;
    }
    
    /**
     * Create a new BatchJob instance, based on the given JobInfo. If we already have a BatchJob.id for the job set it.
     * @param jobInfo
     * @return
     */
    private BatchJob getBatchJobFromGpJobInfo(JobInfo jobInfo) throws Exception {
        File runDir = getWorkingDir(jobInfo);
        Option<String> workingDirectory = new Some<String>(runDir.getPath());
        Option<String> command = new Some<String>( null );
        String[] args = null;
        Option<String> outputPath = new Some<String>( null );
        Option<String>  errorPath = new Some<String>( null );
        Option<String[]> emailAddresses = new Some<String[]>( null );
        Option<Integer> priority = new Some<Integer>( null );
        Option<String> jobName = new Some<String>( ""+jobInfo.getJobNumber() );
        Option<String> queueName = new Some<String>(null);
        Option<Boolean> exclusive = new Some<Boolean>(null);
        Option<Integer> maxRunningTime = new Some<Integer>(null);
        Option<Integer> memoryReservation = new Some<Integer>(null);
        Option<Integer> maxMemory = new Some<Integer>(null);
        Option<Integer> slotReservation = new Some<Integer>(null);
        Option<Integer> maxSlots = new Some<Integer>(null);
        Option<Boolean> restartable = new Some<Boolean>(null);

        BatchJob sgeJob = sgeBatchSystem.newBatchJob(
                workingDirectory,
                command,
                args,
                outputPath,
                errorPath,
                emailAddresses,
                priority,
                jobName,
                queueName,
                exclusive,
                maxRunningTime,
                memoryReservation,
                maxMemory,
                slotReservation,
                maxSlots,
                restartable );
       
        return sgeJob;
    }
}
