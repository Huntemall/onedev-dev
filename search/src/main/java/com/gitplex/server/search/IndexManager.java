package com.gitplex.server.search;

import org.eclipse.jgit.lib.ObjectId;

import com.gitplex.server.model.Depot;
import com.gitplex.server.util.IndexResult;

public interface IndexManager {
	
	IndexResult index(Depot depot, ObjectId commit);
	
	void indexAsync(Depot depot, ObjectId commit);
	
	boolean isIndexed(Depot depot, ObjectId commit);
	
}
