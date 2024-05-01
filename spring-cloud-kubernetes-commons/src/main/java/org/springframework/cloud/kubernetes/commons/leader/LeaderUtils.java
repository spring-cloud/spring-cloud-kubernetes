/*
 * Copyright 2013-2024 the original author or authors.
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

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.concurrent.locks.ReentrantLock;

import org.springframework.cloud.kubernetes.commons.EnvReader;
import org.springframework.util.StringUtils;

/**
 * @author wind57
 */
public final class LeaderUtils {

	// k8s environment variable responsible for host name
	private static final String HOSTNAME = "HOSTNAME";

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
