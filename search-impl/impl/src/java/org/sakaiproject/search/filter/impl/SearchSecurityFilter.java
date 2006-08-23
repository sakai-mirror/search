/**
 * 
 */
package org.sakaiproject.search.filter.impl;

import java.util.HashMap;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.sakaiproject.component.api.ComponentManager;
import org.sakaiproject.entity.api.EntityManager;
import org.sakaiproject.entity.api.Reference;
import org.sakaiproject.search.api.EntityContentProducer;
import org.sakaiproject.search.api.SearchIndexBuilder;
import org.sakaiproject.search.api.SearchResult;
import org.sakaiproject.search.filter.SearchItemFilter;

/**
 * @author ieb
 */
public class SearchSecurityFilter implements SearchItemFilter
{

	private static final Log log = LogFactory.getLog(SearchSecurityFilter.class);

	private SearchIndexBuilder searchIndexBuilder = null;

	private EntityManager entityManager = null;

	private SearchItemFilter nextFilter = null;

	public void init()
	{
		ComponentManager cm = org.sakaiproject.component.cover.ComponentManager
				.getInstance();
		entityManager = (EntityManager) load(cm, EntityManager.class.getName());
		if (entityManager == null)
		{
			log.error(" entityManager must be set");
			throw new RuntimeException("Must set entityManager");
		}

	}
	private Object load(ComponentManager cm, String name)
	{
		Object o = cm.get(name);
		if (o == null)
		{
			log.error("Cant find Spring component named " + name);
		}
		return o;
	}


	/**
	 * @return Returns the nextFilter.
	 */
	public SearchItemFilter getNextFilter()
	{
		return nextFilter;
	}

	/**
	 * @param nextFilter
	 *        The nextFilter to set.
	 */
	public void setNextFilter(SearchItemFilter nextFilter)
	{
		this.nextFilter = nextFilter;
	}

	/**
	 * @return Returns the searchIndexBuilder.
	 */
	public SearchIndexBuilder getSearchIndexBuilder()
	{
		return searchIndexBuilder;
	}

	/**
	 * @param searchIndexBuilder
	 *        The searchIndexBuilder to set.
	 */
	public void setSearchIndexBuilder(SearchIndexBuilder searchIndexBuilder)
	{
		this.searchIndexBuilder = searchIndexBuilder;
	}

	public SearchResult filter(SearchResult result)
	{
		String reference = result.getReference();
		Reference ref = entityManager.newReference(reference);
		EntityContentProducer ecp = searchIndexBuilder
				.newEntityContentProducer(ref);
		
		if (ecp == null || !ecp.canRead(ref))
		{
			result = new CensoredSearchResult();
		}
		if (nextFilter == null)
		{
			return result;
		}
		return nextFilter.filter(result);
	}

	public class CensoredSearchResult implements SearchResult
	{

		public float getScore()
		{
			return 0;
		}

		public String getId()
		{
			return "";
		}

		public String[] getFieldNames()
		{
			// TODO Auto-generated method stub
			return new String[0];
		}

		public String[] getValues(String string)
		{
			return new String[0];
		}

		public Map getValueMap()
		{
			return new HashMap();
		}

		public String getUrl()
		{
			return "";
		}

		public String getTitle()
		{
			return "You do not have permission to view this search result, please contact the worksite administrator";
		}

		public int getIndex()
		{
			return 0;
		}

		public String getSearchResult()
		{
			return "";
		}

		public String getReference()
		{
			return "";
		}

	}

}
