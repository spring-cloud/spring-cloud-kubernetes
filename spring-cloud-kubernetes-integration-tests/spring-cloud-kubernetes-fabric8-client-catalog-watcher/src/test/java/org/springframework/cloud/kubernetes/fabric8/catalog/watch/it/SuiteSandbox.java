package org.springframework.cloud.kubernetes.fabric8.catalog.watch.it;

import org.junit.platform.suite.api.BeforeSuite;
import org.junit.platform.suite.api.SelectClasses;
import org.junit.platform.suite.api.Suite;

@Suite
@SelectClasses(Sandbox.class)
public class SuiteSandbox {

	@BeforeSuite
	void beforeSuite() {
		System.out.println("beforeSuite");
	}

}
