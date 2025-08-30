/*
 * Copyright 2013-present the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.cloud.kubernetes.commons.leader;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.locks.ReentrantLock;

import org.springframework.cloud.kubernetes.commons.EnvReader;
import org.springframework.core.log.LogAccessor;
import org.springframework.util.StringUtils;

import static org.springframework.cloud.kubernetes.commons.KubernetesClientProperties.SERVICE_ACCOUNT_NAMESPACE_PATH;

/**
 * @author wind57
 */
public final class LeaderUtils {

	/**
	 * Prefix for all properties related to leader election.
	 */
	public static final String LEADER_ELECTION_PROPERTY_PREFIX = "spring.cloud.kubernetes.leader.election";

	/**
	 * Property that controls whether leader election is enabled.
	 */
	public static final String LEADER_ELECTION_ENABLED_PROPERTY = LEADER_ELECTION_PROPERTY_PREFIX + ".enabled";

	private static final LogAccessor LOG = new LogAccessor(LeaderUtils.class);

	// k8s environment variable responsible for host name
	private static final String HOSTNAME = "HOSTNAME";

	private static final String POD_NAMESPACE = "POD_NAMESPACE";

	private LeaderUtils() {

	}

	public static String hostName() throws UnknownHostException {
		String hostName = EnvReader.getEnv(HOSTNAME);
		if (StringUtils.hasText(hostName)) {
			return hostName;
		}
		else {
			return InetAddress.getLocalHost().getHostName();
		}
	}

	/**
	 * ideally, should always be present. If not, downward api must enable this one.
	 */
	public static Optional<String> podNamespace() {
		Path serviceAccountPath = new File(SERVICE_ACCOUNT_NAMESPACE_PATH).toPath();
		boolean serviceAccountNamespaceExists = Files.isRegularFile(serviceAccountPath);
		if (serviceAccountNamespaceExists) {
			try {
				String namespace = new String(Files.readAllBytes(serviceAccountPath)).replace(System.lineSeparator(),
						"");
				LOG.info(() -> "read namespace : " + namespace + " from service account " + serviceAccountPath);
				return Optional.of(namespace);
			}
			catch (IOException e) {
				throw new RuntimeException(e);
			}

		}
		return Optional.ofNullable(EnvReader.getEnv(POD_NAMESPACE));
	}

	public static void guarded(ReentrantLock lock, Runnable runnable) {
		try {
			lock.lock();
			runnable.run();
		}
		finally {
			lock.unlock();
		}
	}

}
