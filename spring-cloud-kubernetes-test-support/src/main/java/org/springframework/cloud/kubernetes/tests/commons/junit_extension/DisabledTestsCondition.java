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

package org.springframework.cloud.kubernetes.tests.commons.junit_extension;

import org.junit.jupiter.api.extension.ConditionEvaluationResult;
import org.junit.jupiter.api.extension.ExecutionCondition;
import org.junit.jupiter.api.extension.ExtensionContext;

/**
 * This is mainly needed for our pipeline, to get the test classes names. Its purpose is
 * to act like a 'dry-run': show all the tests fully qualified names without actually
 * running them.
 *
 * The way to use it: "mvn clean test -Dspring.cloud.k8s.skip.tests=true". This way all
 * tests will be skipped, but also will be printed to the standard output as a fully
 * qualified name, i.e.:
 *
 * <pre>
 *     spring.cloud.k8s.test.to.run -> org.springframework.cloud.kubernetes.Fabric8InsideHealthIndicatorTest
 * </pre>
 *
 * @author wind57
 */
@Deprecated
public class DisabledTestsCondition implements ExecutionCondition {

	private static final boolean SKIP_RUNNING_TESTS = "true".equals(System.getProperty("spring.cloud.k8s.skip.tests"));

	@Override
	public ConditionEvaluationResult evaluateExecutionCondition(ExtensionContext extensionContext) {
		if (SKIP_RUNNING_TESTS) {
			System.out.println(
					"\nspring.cloud.k8s.test.to.run -> " + extensionContext.getRequiredTestClass().getName() + " \n");
			return ConditionEvaluationResult.disabled("");
		}
		else {
			return ConditionEvaluationResult.enabled("");
		}
	}

}
