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

package org.sakaiproject.search.component.adapter.contenthosting;

import java.io.Reader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.sakaiproject.component.api.ServerConfigurationService;
import org.sakaiproject.content.api.ContentHostingService;
import org.sakaiproject.content.api.ContentResource;
import org.sakaiproject.entity.api.EntityManager;
import org.sakaiproject.entity.api.EntityProducer;
import org.sakaiproject.entity.api.Reference;
import org.sakaiproject.entity.api.ResourceProperties;
import org.sakaiproject.event.api.Event;
import org.sakaiproject.exception.IdUnusedException;
import org.sakaiproject.exception.PermissionException;
import org.sakaiproject.exception.TypeException;
import org.sakaiproject.search.api.EntityContentProducer;
import org.sakaiproject.search.api.SearchIndexBuilder;
import org.sakaiproject.search.api.SearchService;
import org.sakaiproject.search.model.SearchBuilderItem;
import org.sakaiproject.site.api.Site;
import org.sakaiproject.site.api.SiteService;
import org.sakaiproject.site.api.SiteService.SelectionType;
import org.sakaiproject.site.api.SiteService.SortType;

public class ContentHostingContentProducer implements EntityContentProducer
{

	private static Log log = LogFactory.getLog(ContentHostingContentProducer.class);

	/**
	 * resolved dep
	 */
	private SearchService searchService = null;

	/**
	 * resolved dep
	 */
	private ContentHostingService contentHostingService = null;

	/**
	 * resolved dep
	 */
	private SearchIndexBuilder searchIndexBuilder = null;

	/**
	 * resolved dep
	 */
	private EntityManager entityManager = null;

	/**
	 * resolved dep
	 */
	private SiteService siteService = null;

	/**
	 * runtime injected
	 */
	private ArrayList digesters = new ArrayList();

	/**
	 * config injected dep
	 */
	private ContentDigester defaultDigester;

	private int readerSizeLimit = 1024 * 1024 * 2; // (2M)

	private int digesterSizeLimit = 1024 * 1024 * 5; // (5M)

	/**
	 * A list of custom properties in the form indexkey.entitykey;indexkey.entitykey;indexkey.entitykey;
	 */
	private List<String> customProperties = null;

	private ServerConfigurationService serverConfigurationService;
	
	
	public ContentHostingContentProducer() {
		customProperties = new ArrayList<String>();
		customProperties.add("dc_created.http://purl.org/dc/terms/created");
		customProperties.add("Tdc_publisher.http://purl.org/dc/elements/1.1/publisher");
		customProperties.add("Tdc_audience.http://purl.org/dc/terms/audience");
		customProperties.add("Tdc_subject.http://purl.org/dc/elements/1.1/subject");
		customProperties.add("Tdc_creator.http://purl.org/dc/elements/1.1/creator");
		customProperties.add("Tdc_educationlevel.http://purl.org/dc/terms/educationLevel");
		customProperties.add("Tdc_alternative.http://purl.org/dc/elements/1.1/alternative");
		customProperties.add("dc_issued.http://purl.org/dc/terms/issued");
		customProperties.add("Tdc_abstract.http://purl.org/dc/terms/abstract");
		customProperties.add("Tdc_contributor.http://purl.org/dc/elements/1.1/contributor");

	}

	public void init()
	{
		try
		{

			if ("true".equals(serverConfigurationService.getString("search.enable",
					"false")))
			{

				searchService.registerFunction(ContentHostingService.EVENT_RESOURCE_ADD);
				searchService
						.registerFunction(ContentHostingService.EVENT_RESOURCE_WRITE);
				searchService
						.registerFunction(ContentHostingService.EVENT_RESOURCE_REMOVE);
				searchIndexBuilder.registerEntityContentProducer(this);

			}

		}
		catch (Throwable t)
		{
			log.error("Failed to init Service ", t);
		}

	}

	public void addDigester(ContentDigester digester)
	{
		digesters.add(digester);
	}

	public void removeDigester(ContentDigester digester)
	{
		digesters.remove(digester);
	}


	public boolean isContentFromReader(String ref)
	{
		boolean debug = log.isDebugEnabled();
		ContentResource contentResource;
		try
		{
			Reference reference = entityManager.newReference(ref);
			contentResource = contentHostingService.getResource(reference.getId());
		}
		catch (Exception e)
		{
			throw new RuntimeException("Failed to resolve resource " + ref, e);
		}
		if (contentResource.getContentLength() > readerSizeLimit)
		{
			if (debug)
			{
				log.debug("ContentHosting.isContentFromReader" + ref + ":yes");
			}
			return true;
		}
		if (debug)
		{
			log.debug("ContentHosting.isContentFromReader" + ref + ":yes");
		}
		return false;
	}

	public Reader getContentReader(String ref)
	{
		boolean debug = log.isDebugEnabled();
		ContentResource contentResource;
		try
		{
			Reference reference = entityManager.newReference(ref);
			contentResource = contentHostingService.getResource(reference.getId());
		}
		catch (Exception e)
		{
			throw new RuntimeException("Failed to resolve resource " + ref, e);
		}
		if (contentResource.getContentLength() <= 0)
		{
			if (debug)
			{
				log.debug("ContentHosting.getContentReader" + ref + ": empty");
			}
			return new StringReader("");
		}

		ContentDigester digester = getDigester(contentResource);
		Reader reader = null;
		try
		{
			reader = digester.getContentReader(contentResource);
		}
		catch (Exception ex)
		{
			log.debug("Failed to digest "+ref+" with " + digester, ex);
			log.warn("Failed to digest "+ref+" with " + digester + " cause: " + ex.getMessage());
			if (!digester.equals(defaultDigester))
			{
				try
				{
					reader = defaultDigester.getContentReader(contentResource);
					log.info("Digested "+ref+" into a Reader with Default Digester ");
				}
				catch (Exception ex2)
				{
					log.debug("Failed to extract content from " + contentResource
							+ " using " + defaultDigester, ex2);
					throw new RuntimeException("Failed to extract content from "
							+ contentResource + " using " + defaultDigester + " and "
							+ digester, ex);
				}
			}
			else
			{
				throw new RuntimeException("Failed to extract content from "
						+ ref + " using " + digester, ex);
			}
		}
		if (debug)
		{
			log.debug("ContentHosting.getContentReader" + ref + ":" + reader);
		}
		return reader;
	}

	public String getContent(String ref)
	{
		return getContent(ref, 3);
	}

	public String getContent(String ref, int minWordLenght)
	{
		boolean debug = log.isDebugEnabled();
		ContentResource contentResource;
		try
		{
			Reference reference = entityManager.newReference(ref);
			contentResource = contentHostingService.getResource(reference.getId());
		}
		catch (Exception e)
		{
			if (debug)
			{
				log.debug("Failed To resolve Resource", e);
			}
			throw new RuntimeException("Failed to resolve resource ", e);
		}
		if (contentResource.getContentLength() <= 0)
		{
			if (debug)
			{
				log.debug("ContentHosting.getContent" + ref + ":empty");
			}
			return "";
		}
		ContentDigester digester = getDigester(contentResource);
		String content = null;
		try
		{
			content = digester.getContent(contentResource);
		}
		catch (Exception ex)
		{
			log.debug("Failed to digest "+ref+" with " + digester, ex);
			log.warn("Failed to digest "+ref+" with " + digester + " cause: " + ex.getMessage());
			if (!digester.equals(defaultDigester))
			{
				try
				{
					content = defaultDigester.getContent(contentResource);
					log.info("Digested "+ref+" into "+content.length()+" characters with Default Digester ");
				}
				catch (Exception ex2)
				{
					log.debug("Failed to extract content from " + ref
							+ " using " + defaultDigester, ex2);
					throw new RuntimeException("Failed to extract content from "
							+ ref + " using " + defaultDigester + " and "
							+ digester, ex);
				}
			}
			else
			{
				if (debug)
				{
					log.debug("Failed To extract content");
				}
				throw new RuntimeException("Failed to extract content from "
						+ ref + " using " + digester, ex);
			}
		}
		if (debug)
		{
			log.debug("ContentHosting.getContent" + ref + ":" + content);
		}
		return content;

	}

	public ContentDigester getDigester(ContentResource cr)
	{
		boolean debug = log.isDebugEnabled();
		if (cr.getContentLength() > digesterSizeLimit)
		{
			return defaultDigester;
		}
		String mimeType = cr.getContentType();
		for (Iterator i = digesters.iterator(); i.hasNext();)
		{
			ContentDigester digester = (ContentDigester) i.next();
			if (digester.accept(mimeType))
			{
				return digester;
			}
		}
		return defaultDigester;

	}

	public String getTitle(String ref)
	{
		boolean debug = log.isDebugEnabled();
		ContentResource contentResource;
		try
		{
			Reference reference = entityManager.newReference(ref);
			contentResource = contentHostingService.getResource(reference.getId());
		}
		catch (Exception e)
		{
			if (debug)
			{
				log.debug("Failed To resolve Resource", e);
			}

			throw new RuntimeException("Failed to resolve resource ", e);
		}
		ResourceProperties rp = contentResource.getProperties();
		String displayNameProp = rp.getNamePropDisplayName();
		String title = rp.getProperty(displayNameProp);
		if (debug)
		{
			log.debug("ContentHosting.getTitle" + ref + ":" + title);
		}
		return title;
	}

	public boolean matches(String ref)
	{
		boolean debug = log.isDebugEnabled();
		try
		{
			Reference reference = entityManager.newReference(ref);
			EntityProducer ep = reference.getEntityProducer();
			boolean m = (ep instanceof ContentHostingService);
			if (debug)
			{
				log.debug("ContentHosting.matches" + ref + ":" + m);
			}
			return m;

		}
		catch (Exception ex)
		{
			if (debug)
			{
				log.debug("ContentHosting.matches" + ref + ":fail-no-match");
			}
			return false;
		}
	}

	public List getAllContent()
	{
		boolean debug = log.isDebugEnabled();
		List sites = siteService.getSites(SelectionType.ANY, null, null, null,
				SortType.NONE, null);
		List l = new ArrayList();
		for (Iterator is = sites.iterator(); is.hasNext();)
		{
			Site s = (Site) is.next();
			String siteCollection = contentHostingService.getSiteCollection(s.getId());
			List siteContent = contentHostingService.getAllResources(siteCollection);
			for (Iterator i = siteContent.iterator(); i.hasNext();)
			{
				ContentResource resource = (ContentResource) i.next();
				l.add(resource.getReference());
			}
		}
		if (debug)
		{
			log.debug("ContentHosting.getAllContent::" + l.size());
		}
		return l;

	}

	public Integer getAction(Event event)
	{
		boolean debug = log.isDebugEnabled();
		String eventName = event.getEvent();
		if (ContentHostingService.EVENT_RESOURCE_ADD.equals(eventName)
				|| ContentHostingService.EVENT_RESOURCE_WRITE.equals(eventName))
		{
			if (debug)
			{
				log.debug("ContentHosting.getAction" + event + ":add");
			}
			if ( isForIndex(event.getResource())) {
				return SearchBuilderItem.ACTION_ADD;
			}
		}
		if (ContentHostingService.EVENT_RESOURCE_REMOVE.equals(eventName))
		{
			if (debug)
			{
				log.debug("ContentHosting.getAction" + event + ":delete");
			}
			if ( isForIndexDelete(event.getResource())) {
				return SearchBuilderItem.ACTION_DELETE;
			}
		}
		if (debug)
		{
			log.debug("ContentHosting.getAction" + event + ":unknown");
		}
		return SearchBuilderItem.ACTION_UNKNOWN;
	}

	public boolean matches(Event event)
	{
		boolean debug = log.isDebugEnabled();
		boolean m = !SearchBuilderItem.ACTION_UNKNOWN.equals(getAction(event));
		if (debug)
		{
			log.debug("ContentHosting.matches" + event + ":" + m);
		}
		return m;
	}

	public String getTool()
	{
		return "content";
	}

	public String getUrl(String ref)
	{
		boolean debug = log.isDebugEnabled();
		Reference reference = entityManager.newReference(ref);
		String url = reference.getUrl();
		if (debug)
		{
			log.debug("ContentHosting.getAction" + ref + ":" + url);
		}
		return url;
	}

	private String getSiteId(Reference ref)
	{
		String r = ref.getContext();
		if (log.isDebugEnabled())
		{
			log.debug("ContentHosting.getSiteId" + ref + ":" + r);
		}
		return r;
	}

	public String getSiteId(String resourceName)
	{
		String r = getSiteId(entityManager.newReference(resourceName));
		if (log.isDebugEnabled())
		{
			log.debug("ContentHosting.getSiteId" + resourceName + ":" + r);
		}
		return r;
	}

	public List getSiteContent(String context)
	{
		boolean debug = log.isDebugEnabled();
		String siteCollection = contentHostingService.getSiteCollection(context);
		List siteContent = contentHostingService.getAllResources(siteCollection);
		List l = new ArrayList();
		for (Iterator i = siteContent.iterator(); i.hasNext();)
		{
			ContentResource resource = (ContentResource) i.next();
			l.add(resource.getReference());
		}
		if (debug)
		{
			log.debug("ContentHosting.getSiteContent" + context + ":" + l.size());
		}
		return l;
	}

	public Iterator getSiteContentIterator(String context)
	{
		boolean debug = log.isDebugEnabled();

		String siteCollection = contentHostingService.getSiteCollection(context);
		if (debug)
		{
			log.debug("Getting content for site info " + siteCollection);
		}
		List siteContent = null;
		if ("/".equals(siteCollection))
		{
			siteContent = new ArrayList();
		}
		else
		{
			siteContent = contentHostingService.getAllResources(siteCollection);
		}
		final Iterator scIterator = siteContent.iterator();
		return new Iterator()
		{

			public boolean hasNext()
			{
				return scIterator.hasNext();
			}

			public Object next()
			{
				ContentResource resource = (ContentResource) scIterator.next();
				return resource.getReference();
			}

			public void remove()
			{
				throw new UnsupportedOperationException("Remove is not implimented ");
			}

		};
	}

	/**
	 * @return Returns the readerSizeLimit.
	 */
	public int getReaderSizeLimit()
	{
		return readerSizeLimit;
	}

	/**
	 * @param readerSizeLimit
	 *        The readerSizeLimit to set.
	 */
	public void setReaderSizeLimit(int readerSizeLimit)
	{
		this.readerSizeLimit = readerSizeLimit;
	}

	/**
	 * @return Returns the defaultDigester.
	 */
	public ContentDigester getDefaultDigester()
	{
		return defaultDigester;
	}

	/**
	 * @param defaultDigester
	 *        The defaultDigester to set.
	 */
	public void setDefaultDigester(ContentDigester defaultDigester)
	{
		this.defaultDigester = defaultDigester;
	}

	private boolean isForIndexDelete(String ref)
	{
		// nasty hack to not index dropbox without loading an entity from the DB
		if ( ref.length() > "/content".length() && contentHostingService.isInDropbox(ref.substring("/content".length())) ) {
				return false;
		}

		return true;
	}
	
	public boolean isForIndex(String ref)
	{
		ContentResource contentResource;
		try
		{
			// nasty hack to not index dropbox without loading an entity from the DB
			if ( ref.length() > "/content".length() && contentHostingService.isInDropbox(ref.substring("/content".length())) ) {
					return false;
			}
			
			Reference reference = entityManager.newReference(ref);
			

			contentResource = contentHostingService.getResource(reference.getId());
			if (contentResource == null || contentResource.isCollection() )
			{
				return false;
			}
		}
		catch (IdUnusedException idun)
		{
			if (log.isDebugEnabled())
			{
				log.debug("Resource Not present in CHS " + ref);
			}

			return false; // a collection or unknown resource that cant be
			// indexed
		}
		catch (Exception e)
		{
			if (log.isDebugEnabled())
			{
				log.debug("Failed To resolve Resource", e);
			}
			throw new RuntimeException("Failed to resolve resource ", e);
		}
		return true;
	}

	public boolean canRead(String ref)
	{
		try
		{
			Reference reference = entityManager.newReference(ref);
			contentHostingService.checkResource(reference.getId());
			return true;
		}
		catch (Exception ex)
		{
			if (log.isDebugEnabled())
			{
				log.debug("Current user cannot read ref: " + ref, ex);
			}

			return false;
		}
	}

	public Map getCustomProperties(String ref)
	{
		try
		{
			Reference reference = entityManager.newReference(ref);
			ContentResource contentResource;
			contentResource = contentHostingService.getResource(reference.getId());

			Map<String, String> cp = new HashMap<String, String>();
			
			
			
			for (String propname : customProperties)
			{
				String[] propKey = propname.split("\\.", 2);
				if (propKey.length == 2)
				{
					String prop = contentResource.getProperties().getProperty(propKey[1]);
					if (prop != null)
					{
						cp.put(propKey[0], prop);

					}
				}
			}
			return cp;
		}
		catch (PermissionException e)
		{
		}
		catch (IdUnusedException e)
		{
		}
		catch (TypeException e)
		{
		}
		return null;
	}

	public String getCustomRDF(String ref)
	{
		return null;
	}

	/**
	 * @return Returns the digesterSizeLimit.
	 */
	public int getDigesterSizeLimit()
	{
		return digesterSizeLimit;
	}

	/**
	 * @param digesterSizeLimit
	 *        The digesterSizeLimit to set.
	 */
	public void setDigesterSizeLimit(int digesterSizeLimit)
	{
		this.digesterSizeLimit = digesterSizeLimit;
	}

	private Reference getReference(String reference)
	{
		try
		{
			return entityManager.newReference(reference);
		}
		catch (Exception ex)
		{
			if (log.isDebugEnabled())
			{
				log.debug("Failed To resolve Resource", ex);
			}

		}
		return null;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.sakaiproject.search.api.EntityContentProducer#getId(java.lang.String)
	 */
	public String getId(String reference)
	{
		boolean debug = log.isDebugEnabled();
		try
		{
			return getReference(reference).getId();
		}
		catch (Exception ex)
		{
			if (debug)
			{
				log.debug("Failed To resolve Resource", ex);
			}

			return "";
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.sakaiproject.search.api.EntityContentProducer#getSubType(java.lang.String)
	 */
	public String getSubType(String reference)
	{
		boolean debug = log.isDebugEnabled();
		try
		{
			String r = getReference(reference).getSubType();
			if (debug)
			{
				log.debug("ContentHosting.getSubType" + reference + ":" + r);
			}
			return r;
		}
		catch (Exception ex)
		{
			return "";
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.sakaiproject.search.api.EntityContentProducer#getType(java.lang.String)
	 */
	public String getType(String reference)
	{
		boolean debug = log.isDebugEnabled();
		try
		{
			String r = getReference(reference).getType();
			if (debug)
			{
				log.debug("ContentHosting.getType" + reference + ":" + r);
			}
			return r;
		}
		catch (Exception ex)
		{
			return "";
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.sakaiproject.search.api.EntityContentProducer#getType(java.lang.String)
	 */
	public String getContainer(String reference)
	{
		boolean debug = log.isDebugEnabled();
		try
		{
			String r = getReference(reference).getContainer();
			if (debug)
			{
				log.debug("ContentHosting.getContainer" + reference + ":" + r);
			}
			return r;
		}
		catch (Exception ex)
		{
			return "";
		}
	}

	/**
	 * @return the customProperties
	 */
	public List<String> getCustomProperties()
	{
		return customProperties;
	}

	/**
	 * @param customProperties the customProperties to set
	 */
	public void setCustomProperties(List<String> customProperties)
	{
		this.customProperties = customProperties;
	}

	/**
	 * @return the contentHostingService
	 */
	public ContentHostingService getContentHostingService()
	{
		return contentHostingService;
	}

	/**
	 * @param contentHostingService the contentHostingService to set
	 */
	public void setContentHostingService(ContentHostingService contentHostingService)
	{
		this.contentHostingService = contentHostingService;
	}

	/**
	 * @return the entityManager
	 */
	public EntityManager getEntityManager()
	{
		return entityManager;
	}

	/**
	 * @param entityManager the entityManager to set
	 */
	public void setEntityManager(EntityManager entityManager)
	{
		this.entityManager = entityManager;
	}

	/**
	 * @return the searchIndexBuilder
	 */
	public SearchIndexBuilder getSearchIndexBuilder()
	{
		return searchIndexBuilder;
	}

	/**
	 * @param searchIndexBuilder the searchIndexBuilder to set
	 */
	public void setSearchIndexBuilder(SearchIndexBuilder searchIndexBuilder)
	{
		this.searchIndexBuilder = searchIndexBuilder;
	}

	/**
	 * @return the searchService
	 */
	public SearchService getSearchService()
	{
		return searchService;
	}

	/**
	 * @param searchService the searchService to set
	 */
	public void setSearchService(SearchService searchService)
	{
		this.searchService = searchService;
	}

	/**
	 * @return the serverConfigurationService
	 */
	public ServerConfigurationService getServerConfigurationService()
	{
		return serverConfigurationService;
	}

	/**
	 * @param serverConfigurationService the serverConfigurationService to set
	 */
	public void setServerConfigurationService(
			ServerConfigurationService serverConfigurationService)
	{
		this.serverConfigurationService = serverConfigurationService;
	}

	/**
	 * @return the siteService
	 */
	public SiteService getSiteService()
	{
		return siteService;
	}

	/**
	 * @param siteService the siteService to set
	 */
	public void setSiteService(SiteService siteService)
	{
		this.siteService = siteService;
	}

}
