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
package nl.esciencecenter.xenon.adaptors.shared.ssh;

import org.apache.sshd.client.SshClient;
import org.apache.sshd.client.session.ClientSession;
import org.junit.ClassRule;
import org.junit.Test;

import com.palantir.docker.compose.DockerComposeRule;
import com.palantir.docker.compose.connection.waiting.HealthChecks;

import nl.esciencecenter.xenon.credentials.Credential;
import nl.esciencecenter.xenon.credentials.PasswordCredential;

public class SshUtilDockerTest extends SshUtilTest {
	
	@ClassRule
	public static DockerComposeRule docker = DockerComposeRule.builder()
		.file("src/integrationTest/resources/docker-compose/openssh.yml")
		.waitingForService("ssh", HealthChecks.toHaveAllPortsOpen())
		.build();

	public String getLocation() {
		return docker.containers().container("ssh").port(22).inFormat("$HOST:$EXTERNAL_PORT");
	}
	
	public Credential getCorrectCredential() { 
		return new PasswordCredential("xenon", "javagat".toCharArray());
	}
	
	public Credential getInvalidUserCredential() { 
		return new PasswordCredential("aap", "javagat".toCharArray());
	}
	
	public Credential getInvalidPasswordCredential() { 
		return new PasswordCredential("xenon", "aap".toCharArray());
	}
	
	
}
