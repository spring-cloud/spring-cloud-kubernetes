package org.springframework.cloud.kubernetes.fabric8.catalog.watch.it;

import org.junit.platform.suite.api.BeforeSuite;
import org.junit.platform.suite.api.SelectClasses;
import org.junit.platform.suite.api.Suite;

@Suite
@SelectClasses({StatefulTestCase.Test1.class, StatefulTestCase.Test2.class})
public class Sandbox {

	@BeforeSuite
	static void beforeSuite() {
		System.out.println("go");
	}

}
