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

import nl.esciencecenter.xenon.XenonException;
import nl.esciencecenter.xenon.schedulers.JobDescription;

public class MockInteractiveProcessFactory implements InteractiveProcessFactory{
	
	private long delay;
	private long killDelay;
	
	public MockInteractiveProcessFactory() { 
		this(0);
	}
	
	public MockInteractiveProcessFactory(long delay) { 
		this(delay, 0);
	}
	
	public MockInteractiveProcessFactory(long delay, long killDelay) { 
		this.delay = delay;
		this.killDelay = killDelay;
	}
	
	public InteractiveProcess createInteractiveProcess(JobDescription description, String jobIdentifier) throws XenonException {
		return new MockInteractiveProcess(description, jobIdentifier, delay, killDelay);
	}

	@Override
	public void close() throws XenonException {
		
	}

	@Override
	public boolean isOpen() throws XenonException {
		return true;
	}

}
