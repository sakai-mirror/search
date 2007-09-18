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

package org.sakaiproject.search.journal.api;

import org.apache.lucene.index.IndexReader;

/**
 * @author ieb
 *
 */
public interface IndexListener
{

	/**
	 * Called when the index reader is closed
	 * @param oldMultiReader the index reader that has been detached and is ready to close
	 */
	void doIndexReaderClose(IndexReader oldMultiReader);

	/**
	 * @param newMultiReader
	 */
	void doIndexReaderOpen(IndexReader newMultiReader);


}
