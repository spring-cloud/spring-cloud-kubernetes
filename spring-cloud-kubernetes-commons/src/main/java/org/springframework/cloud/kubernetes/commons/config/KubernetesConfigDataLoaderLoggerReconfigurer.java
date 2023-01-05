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

package org.springframework.cloud.kubernetes.commons.config;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;

import org.apache.commons.logging.Log;

import org.springframework.boot.logging.DeferredLogFactory;
import org.springframework.cloud.kubernetes.client.config.K8sNativeKubernetesConfigDataLoggerHidden;
import org.springframework.cloud.kubernetes.fabric8.config.Fabric8KubernetesConfigDataLoggerHidden;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.util.ClassUtils;

/**
 *
 * See :
 * <a href="https://github.com/spring-cloud/spring-cloud-kubernetes/issues/1173">this
 * issue</a>.
 *
 * @author wind57
 */
final class KubernetesConfigDataLoaderLoggerReconfigurer {

	private static final String COMMONS_PACKAGE = "org/springframework/cloud/kubernetes/commons/config";

	private static final String FABRIC8_PACKAGE = "org/springframework/cloud/kubernetes/fabric8/config";

	private static final String K8S_NATIVE_PACKAGE = "org/springframework/cloud/kubernetes/client/config";

	private static final PathMatchingResourcePatternResolver RESOLVER = new PathMatchingResourcePatternResolver();

	private static Log log;

	private KubernetesConfigDataLoaderLoggerReconfigurer() {
	}

	static void reconfigureLoggers(DeferredLogFactory logFactory) {

		log = logFactory.getLog(KubernetesConfigDataLoaderLoggerReconfigurer.class);

		try {
			Resource[] commonResources = RESOLVER.getResources("classpath*:" + COMMONS_PACKAGE + "/*.class");
			Resource[] fabric8Resources = RESOLVER.getResources("classpath*:" + FABRIC8_PACKAGE + "/*.class");
			Resource[] k8sNativeResources = RESOLVER.getResources("classpath*:" + K8S_NATIVE_PACKAGE + "/*.class");

			reconfigure(commonResources, logFactory, COMMONS_PACKAGE, KubernetesConfigDataLoggerHidden.class);
			reconfigure(fabric8Resources, logFactory, FABRIC8_PACKAGE, Fabric8KubernetesConfigDataLoggerHidden.class);
			reconfigure(k8sNativeResources, logFactory, K8S_NATIVE_PACKAGE,
					K8sNativeKubernetesConfigDataLoggerHidden.class);

		}
		catch (Throwable e) {
			throw new RuntimeException(e);
		}
	}

	private static void reconfigure(Resource[] resources, DeferredLogFactory logFactory, String base,
			Class<?> hiddenClass) {
		try {
			for (Resource resource : resources) {
				String fqdn = base.replaceAll("/", "\\.") + "." + resource.getFilename().replaceFirst("\\.class", "");
				Class<?> cls = forName(fqdn);

				Method methodWeCareAbout = Arrays.stream(cls.getDeclaredMethods())
						.filter(method -> Modifier.isStatic(method.getModifiers()))
						.filter(method -> method.getParameters().length == 1)
						.filter(method -> method.getParameterTypes()[0] == Log.class).findFirst().orElse(null);

				if (methodWeCareAbout != null) {
					System.out.println("will reconfigure logger for : " + fqdn);
					MethodHandles.Lookup privateLookup = MethodHandles.privateLookupIn(cls, MethodHandles.lookup());
					MethodHandles.Lookup lookup = privateLookup.defineHiddenClass(cls.getClassLoader()
							.getResourceAsStream(hiddenClass.getName().replace('.', '/') + ".class").readAllBytes(),
							true, MethodHandles.Lookup.ClassOption.NESTMATE);

					lookup.findStatic(lookup.lookupClass().getNestHost(), methodWeCareAbout.getName(),
							MethodType.methodType(methodWeCareAbout.getReturnType(), Log.class))
							.invokeExact(logFactory.getLog(cls));
				}

			}
		}
		catch (Throwable t) {
			// retry might not be on the classpath as it is optional
			if (t.getMessage().contains("org/springframework/retry")) {
				log.debug("will ignore exception with message : " + t.getMessage());
			}
			else {
				throw new RuntimeException(t);
			}
		}
	}

	private static Class<?> forName(String name) {
		try {
			return ClassUtils.forName(name, KubernetesConfigDataLoader.class.getClassLoader());
		}
		catch (ClassNotFoundException e) {
			throw new RuntimeException(e);
		}
	}

}
