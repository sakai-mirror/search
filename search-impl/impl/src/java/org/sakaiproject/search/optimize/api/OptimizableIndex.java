/**********************************************************************************
 * $URL$
 * $Id$
 ***********************************************************************************
 *
 * Copyright (c) 2003, 2004, 2005, 2006, 2007 The Sakai Foundation.
 *
 * Licensed under the Educational Community License, Version 1.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.opensource.org/licenses/ecl1.php
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 **********************************************************************************/

package org.sakaiproject.search.optimize.api;

import java.io.File;

import org.apache.lucene.index.IndexWriter;

/**
 * An optimisable index has a number of segments that could be merged and a
 * permanent index writer into which those segments are merged
 * 
 * @author ieb
 */
public interface OptimizableIndex
{

	/**
	 * Get a list of segments that can be optimized
	 * 
	 * @return
	 */
	File[] getOptimizableSegments();

	/**
	 * @return
	 */
	IndexWriter getPermanentIndexWriter();

	/**
	 * @param optimzableSegments
	 */
	void removeOptimizableSegments(File[] optimzableSegments);

}
