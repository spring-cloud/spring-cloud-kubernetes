package org.springframework.cloud.kubernetes.discovery;

import java.util.ArrayList;
import java.util.List;

public class SelectorsService {
	private List<Selector> selectors = new ArrayList<>();

	public List<Selector> getSelectors(){
		return selectors;
	}

	public void addSelector(Selector selector){
		selectors.add(selector);
	}

	public void clearSelectors(){
		selectors.clear();
	}


}
