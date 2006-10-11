/**********************************************************************************
 * $URL$
 * $Id$
 ***********************************************************************************
 *
 * Copyright (c) 2003, 2004, 2005, 2006 The Sakai Foundation.
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

package org.sakaiproject.search.api;

import java.io.Reader;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.sakaiproject.entity.api.Entity;
import org.sakaiproject.entity.api.Reference;
import org.sakaiproject.event.api.Event;

/**
 * This is a special class than handles ContentResources for the purposes of
 * search. This must be impelented in a thread safe way. The aim is to map the
 * content handler to the mime type
 * 
 * @author ieb
 */
public interface EntityContentProducer
{

	/**
	 * Should the consumer use the reader or is it Ok to use a memory copy of
	 * the content
	 * 
	 * @param cr
	 * @return
	 */
	boolean isContentFromReader(Entity cr);

	/**
	 * Get a reader for the supplied content resource
	 * 
	 * @param cr
	 * @return
	 */
	Reader getContentReader(Entity cr);

	/**
	 * Get the content as a string
	 * 
	 * @param cr
	 * @return
	 */
	String getContent(Entity cr);

	/**
	 * get the title for the content
	 * 
	 * @param cr
	 * @return
	 */
	String getTitle(Entity cr);

	/**
	 * Gets the url that displays the entity
	 * 
	 * @param entity
	 * @return
	 */
	String getUrl(Entity entity);

	/**
	 * If the reference matches this EntityContentProducer return true
	 * 
	 * @param ref
	 * @return
	 */
	boolean matches(Reference ref);

	/**
	 * Gets a list of Entity resource as a String to represent all indexable
	 * content
	 * @deprecated
	 * @return
	 */
	List getAllContent();

	/**
	 * Get the search builder action associated with the event
	 * @param event
	 * @return
	 */
	Integer getAction(Event event);

	/**
	 * Is the event owned by this EntityContentProducer
	 * @param event
	 * @return
	 */
	boolean matches(Event event);

	/**
	 * What is the name of the tool, 
	 * @return
	 */
	String getTool();

	/**
	 * get the site Id from the reference, just incase there are any translations 
	 * @param ref
	 * @return
	 */
	String getSiteId(Reference ref);
	
	/**
	 * get the site ID from the resource Name
	 * @param resourceName
	 * @return
	 */
	String getSiteId(String resourceName);

	/**
	 * get all the content associated with a site managed by this EntityContentProducer
	 * @deprecated
	 * @param context
	 * @return
	 */
	List getSiteContent(String context);
	
	/**
	 * Get the site content as an iterator
	 * @param context
	 * @return
	 */
	Iterator getSiteContentIterator(String context);

	/**
	 * If the reference should be indexed, return true
	 * @param ref
	 * @return
	 */
	boolean isForIndex(Reference ref);

	/**
	 * returns true if the current user can view the search result
	 * @param ref 
	 * @return
	 */
	boolean canRead(Reference ref);

	/**
	 * Gets a map of custom document properties. The names of the map map will contain 
	 * the index name to which the value is added.
	 * The value is expected to be a String or String[], containig the value, values to be
	 * added. Before using this method in your entity producer, be certain that the value
	 * is not already in the index. ( See SearchService for list of Fields)
	 * @return
	 */
	Map getCustomProperties();

	/**
	 * At the moment this is a placeholder, but eventually
	 * It will return a block of Custom RDF, that the EntityContentProducer wants
	 * the search index to index. This is ontop of any RDF that the search index is 
	 * already processing. 
	 * @return
	 */
	String getCustomRDF();

}
