/*
 * Copyright 2013-2019 the original author or authors.
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

package org.springframework.cloud.kubernetes.istio.trace;

import com.netflix.hystrix.strategy.HystrixPlugins;
import com.netflix.hystrix.strategy.concurrency.HystrixConcurrencyStrategy;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

/**
 * Use BeanFactoryProcessor to customize Hystrix's thread pool to support Istio's call chain headers.
 *
 * @author wuzishu
 * @see org.springframework.beans.factory.config.BeanFactoryPostProcessor
 * @see org.springframework.cloud.kubernetes.istio.trace.ThreadLocalHystrixConcurrencyStrategy
 * @see com.netflix.hystrix.strategy.HystrixPlugins
 */
@ConditionalOnClass(HystrixConcurrencyStrategy.class)
@ConditionalOnProperty(value = "spring.cloud.istio.enabled", matchIfMissing = true)
public class HystrixConcurrencyStategyBeanFactoryPostProcessor
		implements BeanFactoryPostProcessor {

	@Override
	public void postProcessBeanFactory(
			ConfigurableListableBeanFactory configurableListableBeanFactory)
			throws BeansException {
		HystrixPlugins.getInstance()
				.registerConcurrencyStrategy(new ThreadLocalHystrixConcurrencyStrategy());
	}

}
