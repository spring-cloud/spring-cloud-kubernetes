/*
 * Copyright 2013-2023 the original author or authors.
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

package org.springframework.cloud.kubernetes.tests.discovery;

import java.io.File;
import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.platform.engine.discovery.DiscoverySelectors;
import org.junit.platform.launcher.Launcher;
import org.junit.platform.launcher.LauncherDiscoveryRequest;
import org.junit.platform.launcher.LauncherSession;
import org.junit.platform.launcher.TestIdentifier;
import org.junit.platform.launcher.TestPlan;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;

/**
 * @author wind57
 */
public class TestsDiscovery {

	public static void main(String[] args) throws Exception {
		List<String> targetClasses = entireClasspath().stream().filter(x -> x.contains("target/classes")).toList();
		List<String> targetTestClasses = targetClasses.stream().map(x -> x.replace("classes", "test-classes")).toList();
		List<String> jars = entireClasspath().stream().filter(x -> x.contains(".jar")).toList();

		List<URL> urls = Stream.of(targetClasses, targetTestClasses, jars).flatMap(List::stream)
				.map(x -> toURL(new File(x).toPath().toUri())).toList();

		Set<Path> paths = Stream.of(targetClasses, targetTestClasses, jars).flatMap(List::stream).map(Paths::get)
				.collect(Collectors.toSet());

		replaceClassloader(urls);

		LauncherDiscoveryRequest request = LauncherDiscoveryRequestBuilder.request()
				.selectors(DiscoverySelectors.selectClasspathRoots(paths)).build();

		try (LauncherSession session = LauncherFactory.openSession()) {
			Launcher launcher = session.getLauncher();
			TestPlan testPlan = launcher.discover(request);
			testPlan.getRoots().stream().flatMap(x -> testPlan.getChildren(x).stream())
					.map(TestIdentifier::getLegacyReportingName).sorted().forEach(test -> {
						System.out.println("spring.cloud.k8s.test.to.run -> " + test);
					});
		}

	}

	private static void replaceClassloader(List<URL> classpathURLs) {
		ClassLoader parentClassLoader = Thread.currentThread().getContextClassLoader();
		URLClassLoader classLoader = URLClassLoader.newInstance(classpathURLs.toArray(new URL[0]), parentClassLoader);
		Thread.currentThread().setContextClassLoader(classLoader);
	}

	// /tmp/deps.txt are created by the pipeline
	private static List<String> entireClasspath() throws Exception {
		try (Stream<String> lines = Files.lines(Paths.get("/tmp/deps.txt"))) {
			return lines.distinct().collect(Collectors.toList());
		}
	}

	private static URL toURL(URI uri) {
		try {
			return uri.toURL();
		}
		catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

}
