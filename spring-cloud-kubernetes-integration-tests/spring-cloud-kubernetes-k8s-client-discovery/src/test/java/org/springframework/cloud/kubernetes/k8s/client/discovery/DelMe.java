package org.springframework.cloud.kubernetes.k8s.client.discovery;

import org.junit.jupiter.api.Test;
import org.testcontainers.shaded.org.apache.commons.io.FileUtils;

import java.io.File;
import java.util.zip.Adler32;
import java.util.zip.Checksum;

public class DelMe {

	String leftPath = "/var/folders/ky/nbnwnjpn7lb3pj8qc50s3w200000gn/T/left.tar";
	String rightPath = "/var/folders/ky/nbnwnjpn7lb3pj8qc50s3w200000gn/T/right.tar";


	@Test
	void test() throws Exception {

		Checksum checksum = new Adler32();
		FileUtils.checksum(new File(leftPath), checksum);
		System.out.println(checksum.getValue());

		checksum = new Adler32();
		FileUtils.checksum(new File(rightPath), checksum);
		System.out.println(checksum.getValue());

	}

}
