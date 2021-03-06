/**
 * Copyright 2013 Netherlands eScience Center
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package nl.esciencecenter.xenon.adaptors.schedulers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import static org.junit.Assume.assumeTrue;

import java.io.OutputStream;

import static org.junit.Assume.assumeFalse;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import nl.esciencecenter.xenon.UnsupportedOperationException;
import nl.esciencecenter.xenon.XenonException;
import nl.esciencecenter.xenon.schedulers.JobDescription;
import nl.esciencecenter.xenon.schedulers.JobStatus;
import nl.esciencecenter.xenon.schedulers.NoSuchJobException;
import nl.esciencecenter.xenon.schedulers.NoSuchQueueException;
import nl.esciencecenter.xenon.schedulers.QueueStatus;
import nl.esciencecenter.xenon.schedulers.Scheduler;
import nl.esciencecenter.xenon.schedulers.SchedulerAdaptorDescription;
import nl.esciencecenter.xenon.schedulers.Streams;
import nl.esciencecenter.xenon.utils.StreamForwarder;

public abstract class SchedulerTestParent {
	
	public static final Logger LOGGER = LoggerFactory.getLogger(SchedulerTestParent.class);

	private Scheduler scheduler;
	private SchedulerAdaptorDescription description;
	private SchedulerLocationConfig locationConfig;

	@Before
	public void setup() throws XenonException {
		scheduler = setupScheduler();
		description = setupDescription();
		locationConfig = setupLocationConfig();
	}

	protected abstract SchedulerLocationConfig setupLocationConfig();

    @After
    public void cleanup() throws XenonException {
    	if (scheduler.isOpen()) { 
    		scheduler.close();
    	}
    }

    public abstract Scheduler setupScheduler() throws XenonException;

    private SchedulerAdaptorDescription setupDescription() throws XenonException {
        String name = scheduler.getAdaptorName();
        return Scheduler.getAdaptorDescription(name);
    }

    @Test
    public void test_close() throws XenonException {
    	scheduler.close();
    	assertFalse(scheduler.isOpen());
    }
    	
    @Test
    public void test_getLocation() throws XenonException {
    	
    	String location = scheduler.getLocation();
    	
    	assertEquals(locationConfig.getLocation(), location);
    }
   
    @Test
    public void test_isEmbedded() throws XenonException {
    	assertEquals(locationConfig.isEmbedded(), description.isEmbedded());
    }
    
    @Test
    public void test_supportsBatch() throws XenonException {
    	assertEquals(locationConfig.supportsBatch(), description.supportsBatch());
    }
   
    @Test
    public void test_supportsInteractive() throws XenonException {
    	assertEquals(locationConfig.supportsInteractive(), description.supportsInteractive());
    }
    
    private boolean contains(String [] options, String expected) { 
    
    	if (options == null || options.length == 0) { 
    		return false;
    	}
    	
		for (String s : options) { 
			if (expected == null) { 
				if (s == null) { 
					return true;
				}
			} else { 
				if (expected.equals(s)) { 
					return true;
				}
			}
		}
    	
		return false;
    }
    
    private boolean unorderedEquals(String [] expected, String [] actual) { 
    	
    	if (expected.length != actual.length) { 
    		return false;
    	}
    	
    	for (String s : expected) {
    		if (!contains(actual, s)) { 
    			return false;
    		}
    	}
    		
    	for (String s : actual) {
     		if (!contains(expected, s)) { 
    			return false;
    		}
    	}
    	
    	return true;
    }
    
    @Test
    public void test_getQueueNames() throws XenonException {
    	assertTrue(unorderedEquals(locationConfig.getQueueNames(), scheduler.getQueueNames()));
    }
  
    
    @Test
    public void test_getDefaultQueueNames() throws XenonException {
    	assertEquals(locationConfig.getDefaultQueueName(), scheduler.getDefaultQueueName());
    }

    private JobDescription getSleepJob(String queue, int time) { 
   
    	JobDescription job = new JobDescription();
    	job.setExecutable("/bin/sleep");
    	job.setArguments("" + time);
    
    	if (queue != null) { 
    		job.setQueueName(queue);
    	}
    	
    	return job;
    }
    
    @Test
    public void test_sleep() throws XenonException {
    	
    	assumeTrue(description.supportsBatch());
    	
    	String jobID = scheduler.submitBatchJob(getSleepJob(null, 1));
    	JobStatus status = scheduler.waitUntilDone(jobID, 60*1000);
    	assertTrue("Job is not done yet", status.isDone());
    }
    
    @Test
    public void test_cancel() throws XenonException {
    
    	assumeTrue(description.supportsBatch());
        
    	String jobID = scheduler.submitBatchJob(getSleepJob(null, 240));

    	// Wait -UP TO- 120 seconds until the job is running (torque is slow)
    	JobStatus status = scheduler.waitUntilRunning(jobID, 120*1000);
    	
     	assertTrue("Job is not running yet", status.isRunning());
     	assertFalse("Job is already done", status.isDone());
         	
     	status = scheduler.cancelJob(jobID);

     	if (!status.isDone()) {
     		// Wait up to 60 seconds until the job is completely done
     		status = scheduler.waitUntilDone(jobID, 60*1000);
     	}
     	
    	assertTrue(status.isDone());
    }
    
    private void cleanupJob(String jobID) { 
    
    	JobStatus status = null;
    	
    	// Clean up the mess..
    	try { 
    		status = scheduler.cancelJob(jobID);
    	} catch (Exception e) {
    		LOGGER.warn("Failed to cancel job: " + jobID, e);
    		System.out.println("Failed to cancel job: " + jobID + " " + e);
    		e.printStackTrace();
    		return;
    	}

    	long time = System.currentTimeMillis();
    	
    	try {
    		long delta = System.currentTimeMillis() - time;
    		
    		while (!status.isDone() && delta < 120*1000) { 
    			// Wait up to 5 seconds until the job is completely done
    			status = scheduler.waitUntilDone(jobID, 5*1000);
    			delta = System.currentTimeMillis() - time;
    		}
    		
    		if (status.isDone()) {
    			LOGGER.warn("Job " + jobID + " done after " + delta + " ms.");		
    			System.out.println("Job " + jobID + " done after " + delta + " ms.");		
    		} else {
    			LOGGER.warn("Job " + jobID + " NOT done after " + delta + " ms.");
    			System.out.println("Job " + jobID + " NOT done after " + delta + " ms.");
    		}
    	} catch (Exception e) {
    		LOGGER.warn("Failed to cancel job: " + jobID, e);
    		System.out.println("Failed to cancel job: " + jobID + " " + e);
    		e.printStackTrace();
    		return;
    	}
    }
    
    @Test
    public void test_getJobsQueueNameEmpty() throws XenonException {
    
    	assumeTrue(description.supportsBatch());
        
    	// Get the available queues
    	String [] queueNames = locationConfig.getQueueNames();
    	
    	assumeTrue(queueNames != null);
    	assumeTrue(queueNames.length > 0);
    	
    	// Submit job of 5 seconds to first queue 
     	String jobID = scheduler.submitBatchJob(getSleepJob(queueNames[0], 5));

     	// Retrieve all jobs
     	String [] jobs = scheduler.getJobs();
     	
     	// Our job should be part of this
     	assertTrue(contains(jobs, jobID));
     
     	// Clean up the mess.
     	cleanupJob(jobID);
    }
    
    @Test
    public void test_getJobsQueueNameNull() throws XenonException {
    	
    	assumeTrue(description.supportsBatch());
        
    	// Get the available queues
    	String [] queueNames = locationConfig.getQueueNames();
    	
    	assumeTrue(queueNames != null);
    	assumeTrue(queueNames.length > 0);
    
    	// Submit job of 5 seconds to first queue
     	String jobID = scheduler.submitBatchJob(getSleepJob(queueNames[0], 5));

     	// Retrieve all jobs 
     	String [] jobs = scheduler.getJobs(new String[0]);
     	
     	// Our job should be part of this
     	assertTrue(contains(jobs, jobID));
     
     	// Clean up the mess...
       	cleanupJob(jobID);
      }

    
    @Test
    public void test_getJobsQueueNameCorrect() throws XenonException {
    
    	assumeTrue(description.supportsBatch());
        
    	// Get the available queues
    	String [] queueNames = locationConfig.getQueueNames();
    	
    	assumeTrue(queueNames != null);
    	assumeTrue(queueNames.length > 0);
    	
    	// Submit it
     	String jobID = scheduler.submitBatchJob(getSleepJob(queueNames[0], 5));

     	// Retrieve all jobs
     	String [] jobs = scheduler.getJobs(queueNames[0]);
     	
     	// Our job should be part of this
     	assertTrue(contains(jobs, jobID));
     
     	// Clean up the mess...
       	cleanupJob(jobID);
    }

    @Test
    public void test_getJobsQueueNameOtherQueue() throws XenonException {
    
    	assumeTrue(description.supportsBatch());
        
    	// Get the available queues
    	String [] queueNames = locationConfig.getQueueNames();
    	
    	assumeTrue(queueNames != null);
    	assumeTrue(queueNames.length > 1);
    	
    	// Submit job to one queue 
     	String jobID = scheduler.submitBatchJob(getSleepJob(queueNames[0], 5));

     	// Retrieve all jobs for other queue 
     	String [] jobs = scheduler.getJobs(queueNames[1]);
     	
     	// Our job should NOT be part of this
     	assertFalse(contains(jobs, jobID));
     
     	// Clean up the mess...
       	cleanupJob(jobID);
    }    
 
    @Test(expected=NoSuchJobException.class)
    public void test_getJobStatus_unknownJob() throws XenonException {
    	scheduler.getJobStatus("aap");
    }
    	
    @Test(expected=IllegalArgumentException.class)
    public void test_getJobStatus_null() throws XenonException {
    	scheduler.getJobStatus(null);
    }
    
    @Test
    public void test_getJobStatus_knownJob() throws XenonException {

    	assumeTrue(description.supportsBatch());
        
    	// Get the available queues
    	String [] queueNames = locationConfig.getQueueNames();
    	
    	assumeTrue(queueNames != null);
    	assumeTrue(queueNames.length >= 1);

    	// Submit job to one queue 
     	String jobID = scheduler.submitBatchJob(getSleepJob(queueNames[0], 1));
    	
     	JobStatus status = scheduler.getJobStatus(jobID);
        
     	assertNotNull(status);
     	assertEquals(jobID, status.getJobIdentifier());
     	assertFalse(status.isDone());
     	
     	// Clean up the mess...
       	cleanupJob(jobID);
    }
    
    @Test
    public void test_getJobStatusses_noJobs() throws XenonException {
    
    	// Get the status of no jobs
    	JobStatus [] result = scheduler.getJobStatuses();
    	assertNotNull(result);
    	assertTrue(result.length == 0);
    }    
    
    @Test
    public void test_getJobStatusses_nonExistingJobs() throws XenonException {
    
    	// Get the status of no jobs
    	JobStatus [] result = scheduler.getJobStatuses("aap", "noot");
    	assertNotNull(result);
    	assertTrue(result.length == 2);
    	
    	assertNotNull(result[0]);
    	assertEquals("aap", result[0].getJobIdentifier());
    	assertTrue(result[0].hasException());
    	
    	assertNotNull(result[1]);
    	assertEquals("noot", result[1].getJobIdentifier());
    	assertTrue(result[1].hasException());
    }
    
    @Test
    public void test_getJobStatusses_nonExistingJobsWithNull() throws XenonException {
    
    	// Get the status of no jobs
    	JobStatus [] result = scheduler.getJobStatuses("aap", null, "noot");
    	assertNotNull(result);
    	assertTrue(result.length == 3);
    	
    	assertNotNull(result[0]);
    	assertEquals("aap", result[0].getJobIdentifier());
    	assertTrue(result[0].hasException());
    	
    	assertNull(result[1]);
    	
    	assertNotNull(result[2]);
    	assertEquals("noot", result[2].getJobIdentifier());
    	assertTrue(result[2].hasException());
    }
    
    @Test
    public void test_getJobStatusses_existingJobs() throws XenonException {
    	
    	assumeTrue(description.supportsBatch());
        
    	// Get the available queues
    	String [] queueNames = locationConfig.getQueueNames();
    	
    	assumeTrue(queueNames != null);
    	assumeTrue(queueNames.length >= 1);

    	// Submit two jobs to queue 
     	String jobID1 = scheduler.submitBatchJob(getSleepJob(queueNames[0], 5));
     	String jobID2 = scheduler.submitBatchJob(getSleepJob(queueNames[0], 5));
        
    	// Get the status of no jobs
    	JobStatus [] result = scheduler.getJobStatuses(jobID1, jobID2);
    	
    	assertNotNull(result);
    	assertTrue(result.length == 2);
    	
    	assertNotNull(result[0]);
    	assertEquals(jobID1, result[0].getJobIdentifier());
    	assertFalse(result[0].isDone());
    	
    	assertNotNull(result[1]);
    	assertEquals(jobID2, result[1].getJobIdentifier());
    	assertFalse(result[1].isDone());
        
     	// Clean up the mess...
       	cleanupJob(jobID1);
       	cleanupJob(jobID2);
    }
   
    @Test(expected=IllegalArgumentException.class)
    public void test_getQueueStatus_null() throws XenonException {
    	scheduler.getQueueStatus(null);
    }
    
    @Test(expected=NoSuchQueueException.class)
    public void test_getQueueStatus_unknownQueue() throws XenonException {
    	scheduler.getQueueStatus("aap");
    }
    
    @Test
    public void test_getQueueStatus_knownQueue() throws XenonException {

    	// Get the available queues
    	String [] queueNames = locationConfig.getQueueNames();
    	
    	assumeTrue(queueNames != null);
    	assumeTrue(queueNames.length >= 1);
    	
    	QueueStatus status = scheduler.getQueueStatus(queueNames[0]);
    	
    	assertNotNull(status);
    	assertEquals(queueNames[0], status.getQueueName());    	
    }
    
    @Test(expected=IllegalArgumentException.class)
    public void test_getQueueStatusses_null() throws XenonException {
    	scheduler.getQueueStatuses((String [])null);
    }
    
    @Test
    public void test_getQueueStatusses_empty() throws XenonException {
    	String [] queueNames = locationConfig.getQueueNames();
    	
    	assumeTrue(queueNames != null);
    	assumeTrue(queueNames.length >= 1);
    	
    	QueueStatus [] result = scheduler.getQueueStatuses(new String [0]);
    
    	assertNotNull(result);
    	assertTrue(queueNames.length == result.length);
    	
    	for (int i=0;i<queueNames.length;i++) { 
    		assertNotNull(result[i]);
    		assertEquals(queueNames[i], result[i].getQueueName());
    		assertFalse(result[i].hasException());
    	}
    }
    
    @Test
    public void test_getQueueStatusses_allQueues() throws XenonException {
    	String [] queueNames = locationConfig.getQueueNames();
    	
    	assumeTrue(queueNames != null);
    	assumeTrue(queueNames.length >= 1);
    	
    	QueueStatus [] result = scheduler.getQueueStatuses(queueNames);
    
    	assertNotNull(result);
    	assertTrue(queueNames.length == result.length);
    	
    	for (int i=0;i<queueNames.length;i++) { 
    		assertNotNull(result[i]);
    		assertEquals(queueNames[i], result[i].getQueueName());
    		assertFalse(result[i].hasException());
    	}
    }
 
    @Test
    public void test_getQueueStatusses_withNull() throws XenonException {
    	String [] queueNames = locationConfig.getQueueNames();
    	
    	assumeTrue(queueNames != null);
    	assumeTrue(queueNames.length > 1);
    	
    	String [] alt = new String[queueNames.length+1];
    	
    	alt[0] = queueNames[0];
    	alt[1] = null;
    	
    	for (int i=1;i<queueNames.length;i++) { 
    		alt[i+1] = queueNames[i];
    	}
    	
    	QueueStatus [] result = scheduler.getQueueStatuses(alt);
    
    	assertNotNull(result);
    	assertTrue(alt.length == result.length);
    	
    	assertNotNull(result[0]);
    	assertEquals(queueNames[0], result[0].getQueueName());
    	
    	assertNull(result[1]);
    	
    	for (int i=1;i<queueNames.length;i++) { 
    		assertNotNull(result[i+1]);
    		assertEquals(queueNames[i], result[i+1].getQueueName());
    		assertFalse(result[i+1].hasException());
    	}
    }
    
    @Test(expected=UnsupportedOperationException.class)
    public void test_interactiveJob_notSupported_throwsException() throws XenonException {
    
    	assumeFalse(description.supportsInteractive());
    	
    	JobDescription job = new JobDescription();
    	job.setExecutable("/bin/cat");
    	
    	scheduler.submitInteractiveJob(job);
    }
    
    
    @Test
    public void test_interactiveJob() throws Exception {
    
    	assumeTrue(description.supportsInteractive());
    	
    	JobDescription job = new JobDescription();
    	job.setExecutable("/bin/cat");
    	
    	Streams streams = scheduler.submitInteractiveJob(job);
    	
    	OutputReader out = new OutputReader(streams.getStdout());
    	OutputReader err = new OutputReader(streams.getStderr());
    	
    	OutputStream stdin = streams.getStdin();
    	
    	stdin.write("Hello World\n".getBytes());
    	stdin.write("Goodbye World\n".getBytes());
    	stdin.close();
    	
    	out.waitUntilFinished();
    	err.waitUntilFinished();
    	
    	assertEquals("Hello World\nGoodbye World\n", out.getResultAsString());
    	
       	cleanupJob(streams.getJobIdentifier());
    }
    
    /*
    @Test(expected=XenonException.class)
    public void test_waitUntilDone_unknownJobID_throwsException() throws XenonException {
    	scheduler.waitUntilDone("foobar", 5*1000);
    }
    
    @Test(expected=IllegalArgumentException.class)
    public void test_waitUntilDone_nullJobID_throwsException() throws XenonException {
    	scheduler.waitUntilDone(null, 5*1000);
    }
    
    @Test(expected=IllegalArgumentException.class)
    public void test_waitUntilDone_emptyJobID_throwsException() throws XenonException {
    	scheduler.waitUntilDone("", 5*1000);
    }
    
    @Test(expected=IllegalArgumentException.class)
    public void test_waitUntilDone_invalidTimeout_throwsException() throws XenonException {
    	scheduler.waitUntilDone("foobar", -1000);
    }
   
    @Test(expected=XenonException.class)
    public void test_waitUntilRunning_unknownJobID_throwsException() throws XenonException {
    	scheduler.waitUntilRunning("foobar", 5*1000);
    }
    
    @Test(expected=IllegalArgumentException.class)
    public void test_waitUntilRunning_nullJobID_throwsException() throws XenonException {
    	scheduler.waitUntilRunning(null, 5*1000);
    }
    
    @Test(expected=IllegalArgumentException.class)
    public void test_waitUntilRunning_emptyJobID_throwsException() throws XenonException {
    	scheduler.waitUntilRunning("", 5*1000);
    }
    
    @Test(expected=IllegalArgumentException.class)
    public void test_waitUntilRunning_invalidTimeout_throwsException() throws XenonException {
    	scheduler.waitUntilRunning("foobar", -1000);
    }
    
    @Test(expected=XenonException.class)
    public void test_cancel_unknownJobID_throwsException() throws XenonException {
    	scheduler.cancelJob("foobar");
    }
    
    @Test(expected=IllegalArgumentException.class)
    public void test_cancel_nullJobID_throwsException() throws XenonException {
    	scheduler.cancelJob(null);
    }
    
    @Test(expected=IllegalArgumentException.class)
    public void test_cancel_emptyJobID_throwsException() throws XenonException {
    	scheduler.cancelJob("");
    }
    */
    
    
    
    
}
