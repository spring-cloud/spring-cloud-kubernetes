package org.springframework.cloud.kubernetes.examples;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.cloud.kubernetes.ribbon.KubernetesEndpointsServerFilter;


import io.fabric8.kubernetes.api.model.EndpointAddress;

public class ServerFilterExample implements KubernetesEndpointsServerFilter {

	private static final Log LOG = LogFactory.getLog(ServerFilterExample.class);

	@Override
	public boolean isFilter(String serviceId, EndpointAddress adress) {
		LOG.debug("*******************serviceId:" + serviceId);
		LOG.debug("*******************adress:" + adress);
		
		// TODO Auto-generated method stub
		if(!serviceId.equals("nameservice"))
			return true;
		if(adress.getTargetRef().getName().startsWith("service2")) 
			return true;
		return false;
	}

}
