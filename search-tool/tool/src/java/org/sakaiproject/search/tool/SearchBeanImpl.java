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

package org.sakaiproject.search.tool;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.text.Collator;
import java.text.MessageFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;

import javax.servlet.http.HttpServletRequest;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.sakaiproject.authz.cover.SecurityService;
import org.sakaiproject.component.cover.ServerConfigurationService;
import org.sakaiproject.exception.IdUnusedException;
import org.sakaiproject.search.api.SearchList;
import org.sakaiproject.search.api.SearchResult;
import org.sakaiproject.search.api.SearchService;
import org.sakaiproject.search.api.TermFrequency;
import org.sakaiproject.search.tool.api.SearchBean;
import org.sakaiproject.search.tool.model.SearchOutputItem;
import org.sakaiproject.search.tool.model.SearchPage;
import org.sakaiproject.search.tool.model.SearchTerm;
import org.sakaiproject.site.api.Site;
import org.sakaiproject.site.api.SiteService;
import org.sakaiproject.tool.api.Placement;
import org.sakaiproject.tool.api.ToolManager;
import org.sakaiproject.util.FormattedText;
import org.sakaiproject.util.StringUtil;

/**
 * Implementation of the search bean backing bean
 * 
 * @author ieb
 */
public class SearchBeanImpl implements SearchBean
{

	private static final Log log = LogFactory.getLog(SearchBeanImpl.class);

	/**
	 * The searhc string parameter name
	 */
	private static final String SEARCH_PARAM = "search";

	/**
	 * The results page
	 */
	private static final String SEARCH_PAGE = "page";

	/**
	 * The search criteria
	 */
	private String search;

	/**
	 * The Search Service to use
	 */
	private SearchService searchService;

	private SiteService siteService;

	/**
	 * Time taken
	 */
	private double timeTaken = 0;

	/**
	 * The number of results per page
	 */
	private int pagesize = 10;

	/**
	 * The number of list links
	 */
	private int nlistPages = 5;

	/**
	 * The default request page
	 */
	private int requestPage = 0;

	/**
	 * The current search list
	 */
	private SearchList searchResults;

	private String placementId;

	private String toolId;
	
	private ToolManager toolManager;

	private String siteId;

	private String sortName = "normal";

	private String filterName = "normal";

	private String errorMessage;

	private List<TermFrequency> termsVectors;

	private List<TermHolder> termList;

	private Site currentSite;

	private int nTerms = 100;

	private List<SearchTerm> finalTermList;

	private int topTerms = 10;

	private boolean relativeTerms = true;

	private float divisorTerms = 3;

	private String requestURL;

	// Empty constructor to aid in testing.
	 
	public SearchBeanImpl(String siteId, SearchService ss, String search,ToolManager tm) {
		super();
		this.siteId = siteId;
		this.searchService = ss;
		this.search = search;
		this.toolManager = tm;
	}
	
	/**
	 * Creates a searchBean
	 * 
	 * @param request
	 *        The HTTP request
	 * @param searchService
	 *        The search service to use
	 * @param siteService
	 *        the site service
	 * @param portalService
	 *        the portal service
	 * @throws IdUnusedException
	 *         if there is no current worksite
	 */
	public SearchBeanImpl(HttpServletRequest request, SearchService searchService,
			SiteService siteService, ToolManager toolManager) throws IdUnusedException
	{
		this.search = request.getParameter(SEARCH_PARAM);
		this.searchService = searchService;
		this.siteService = siteService;
		this.toolManager = toolManager;
		this.placementId = this.toolManager.getCurrentPlacement().getId();
		this.toolId = this.toolManager.getCurrentTool().getId();
		this.siteId = this.toolManager.getCurrentPlacement().getContext();
		try
		{
			this.requestPage = Integer.parseInt(request.getParameter(SEARCH_PAGE));
		}
		catch (Exception ex)
		{

		}
		currentSite = this.siteService.getSite(this.siteId);
		String siteCheck = currentSite.getReference();
		requestURL = request.getRequestURL().toString();

	}

	public SearchBeanImpl(HttpServletRequest request, String sortName, String filterName,
			SearchService searchService, SiteService siteService, ToolManager toolManager)
			throws IdUnusedException
	{
		this.search = request.getParameter(SEARCH_PARAM);
		this.searchService = searchService;
		this.siteService = siteService;
		this.sortName = sortName;
		this.filterName = filterName;
		this.toolManager = toolManager;
		this.placementId = this.toolManager.getCurrentPlacement().getId();
		this.toolId = this.toolManager.getCurrentTool().getId();
		this.siteId = this.toolManager.getCurrentPlacement().getContext();
		try
		{
			this.requestPage = Integer.parseInt(request.getParameter(SEARCH_PAGE));
		}
		catch (Exception ex)
		{

		}
		currentSite = this.siteService.getSite(this.siteId);
		String siteCheck = currentSite.getReference();
		requestURL = request.getRequestURL().toString();
	}

	/**
	 * {@inheritDoc}
	 * @deprecated
	 */
	public String getSearchResults(String searchItemFormat, String errorFeedbackFormat)
	{
		StringBuffer sb = new StringBuffer();
		List searchResults = search();
		if (errorMessage != null)
		{
			sb.append(MessageFormat.format(errorFeedbackFormat,
					new Object[] { FormattedText.escapeHtml(errorMessage, false) }));
		}
		if (searchResults != null)
		{
			for (Iterator i = searchResults.iterator(); i.hasNext();)
			{

				SearchResult sr = (SearchResult) i.next();
				sb.append(MessageFormat.format(searchItemFormat, new Object[] {
						FormattedText.escapeHtml(sr.getTool(), false),
						FormattedText.escapeHtml(sr.getUrl(), false),
						FormattedText.escapeHtml(sr.getTitle(), false),
						sr.getSearchResult(), new Double(sr.getScore()),
						String.valueOf(sr.getIndex() + 1) }));
			}
		}
		return sb.toString();

	}

	private void loadTermVectors()
	{
		StringBuffer sb = new StringBuffer();
		List searchResults = search();
		if (searchResults != null)
		{
			termsVectors = new ArrayList<TermFrequency>();
			termList = null;
			for (Iterator i = searchResults.iterator(); i.hasNext();)
			{

				SearchResult sr = (SearchResult) i.next();
				try
				{
					TermFrequency tf = sr.getTerms();
					if (tf != null)
					{
						termsVectors.add(sr.getTerms());
					}
				}
				catch (IOException e)
				{
					log.warn("Failed to get term vector ", e);
				}
			}
		}
	}

	/**
	 * @deprecated
	 */
	public String getTerms(String format)
	{
		List<SearchTerm> l = getTerms();
		StringBuilder sb = new StringBuilder();
		for (Iterator li = l.iterator(); li.hasNext();)
		{
			SearchTerm t = (SearchTerm) li.next();
			sb.append(MessageFormat.format(format, new Object[] { t.getName(),
					t.getWeight() }));
		}
		return sb.toString();
	}

	protected class TermHolder
	{
		public int position;

		protected String term;

		protected int frequency;

	}

	private void mergeTerms()
	{
		if (termsVectors == null)
		{
			loadTermVectors();
		}
		if (termsVectors == null)
		{
			return;
		}
		HashMap<String, TermHolder> hm = new HashMap<String, TermHolder>();
		for (Iterator i = termsVectors.iterator(); i.hasNext();)
		{
			TermFrequency tf = (TermFrequency) i.next();
			String[] terms = tf.getTerms();
			int[] freq = tf.getFrequencies();
			for (int ti = 0; ti < terms.length; ti++)
			{
				TermHolder h = (TermHolder) hm.get(terms[ti]);
				if (h == null)
				{
					h = new TermHolder();
					h.term = terms[ti];
					h.frequency = freq[ti];
					hm.put(terms[ti], h);
				}
				else
				{
					h.frequency += freq[ti];
				}
			}
		}
		termList = new ArrayList<TermHolder>();
		termList.addAll(hm.values());
		Collections.sort(termList, new Comparator<TermHolder>()
		{

			public int compare(TermHolder a, TermHolder b)
			{

				return b.frequency - a.frequency;
			}

		});

	}

	/**
	 * {@inheritDoc}
	 * @deprecated
	 */
	public String getPager(String pagerFormat, String singlePageFormat)
			throws UnsupportedEncodingException
	{
		SearchList sr = (SearchList) search();
		if (sr == null) return "";
		int npages = (sr.getFullSize()-1) / pagesize;
		int cpage = requestPage - (nlistPages / 2);
		if (cpage < 0)
		{
			cpage = 0;
		}
		StringBuffer sb = new StringBuffer();

		int lastPage = Math.min(cpage + nlistPages, npages);
		boolean first = true;
		if (cpage == lastPage)
		{
			sb.append(singlePageFormat);
		}
		else
		{
			while (cpage <= lastPage)
			{
				String searchURL = "?search=" + URLEncoder.encode(search, "UTF-8")
						+ "&page=" + String.valueOf(cpage);
				String cssInd = "1";
				if (first)
				{
					cssInd = "0";
					first = false;
				}
				else if (cpage == (lastPage))
				{
					cssInd = "2";
				}

				sb.append(MessageFormat.format(pagerFormat, new Object[] {
						FormattedText.escapeHtml(searchURL, false),
						String.valueOf(cpage + 1), cssInd }));
				cpage++;
			}
		}

		return sb.toString();
	}

	public boolean isEnabled()
	{
		return ("true".equals(ServerConfigurationService.getString("search.enable",
				"false")));

	}

	/**
	 * {@inheritDoc}
	 * @deprecated
	 */

	public String getHeader(String headerFormat)
	{
		SearchList sr = (SearchList) search();
		if (sr == null) return "";
		int total = sr.getFullSize();
		int start = 0;
		int end = 0;
		if (total > 0)
		{
			start = sr.getStart();
			end = Math.min(start + sr.size(), total);
			start++;
		}
		return MessageFormat.format(headerFormat, new Object[] { new Integer(start),
				new Integer(end), new Integer(total), new Double(timeTaken) });
	}

	/**
	 * Gets the current search request
	 * 
	 * @return current search request
	 */
	public String getSearch()
	{
		if (search == null) return "";
		return FormattedText.escapeHtml(search, false);
	}

	/**
	 * The time taken to perform the search only, not including rendering
	 * 
	 * @return
	 */
	public String getTimeTaken()
	{
		int tt = (int) timeTaken;
		return String.valueOf(tt);
	}

	
	/* assemble the list of search sites */
	
	protected List getSearchSites (String[] toolPropertySiteIds) {
		List<String> l = new ArrayList();
		
		l.add(this.siteId);
		
		if (toolPropertySiteIds == null) return l;
		
		//String[] searchSiteIds = extractSiteIdsFromToolProperty(extractPropertiesFromTool());
		String[] searchSiteIds = toolPropertySiteIds;

		// add searchSiteIds to l
		for(int i = 0;i<searchSiteIds.length;i++){
			String ss = searchSiteIds[i];
			if (searchSiteIds[i].length() > 0) l.add(searchSiteIds[i]);
		}

		return l;
	}

	protected String[] getToolPropertySiteIds() {
		Properties props = extractPropertiesFromTool();
		String[] searchSiteIds = extractSiteIdsFromProperties(props);
		return searchSiteIds;
	}
	
	/* get any site ids that are in the tool property and normalize the string.
	 * 
	 */
	protected String[] extractSiteIdsFromProperties(Properties props) {
	//	Properties props = extractPropertiesFromTool();
		
		String targetSiteId = StringUtil.trimToNull(props.getProperty("search_site_ids"));
		if (targetSiteId == null) return new String[] {""};
		String[] searchSiteIds = StringUtil.split(targetSiteId, ",");
		for(int i = 0;i<searchSiteIds.length;i++){
			searchSiteIds[i] = StringUtil.trimToZero(searchSiteIds[i]);
		}
		return searchSiteIds;
	}

	protected Properties extractPropertiesFromTool() {
		Placement placement = toolManager.getCurrentPlacement();
		Properties props = placement.getPlacementConfig();
		if(props.isEmpty())
			props = placement.getConfig();
		return props;
	}
	/**
	 * Perform the search
	 * 
	 * @return a list of page names that match the search criteria
	 */
	public SearchList search()
	{

		if (searchResults == null && errorMessage == null)
		{
			if (search != null && search.trim().length() > 0)
			{

				/*				
				List l = new ArrayList();
				l.add(this.siteId);
				*/
				List l = getSearchSites(getToolPropertySiteIds());
				long start = System.currentTimeMillis();
				int searchStart = requestPage * pagesize;
				int searchEnd = searchStart + pagesize;
				try
				{
					searchResults = searchService.search(search, l, searchStart,
							searchEnd, filterName, sortName);
				}
				catch (Exception ex)
				{

					errorMessage = ex.getMessage();
					log.warn("Search Error encoutered, generated by a user action "
							+ ex.getClass().getName() + ":" + ex.getMessage());
					log.debug("Search Error Traceback ", ex);

				}
				long end = System.currentTimeMillis();
				timeTaken = 0.001 * (end - start);
			}
		}
		return searchResults;
	}

	/**
	 * @return Returns the nlistPages.
	 */
	public int getNlistPages()
	{
		return nlistPages;
	}

	/**
	 * @param nlistPages
	 *        The nlistPages to set.
	 */
	public void setNlistPages(int nlistPages)
	{
		this.nlistPages = nlistPages;
	}

	/**
	 * @return Returns the pagesize.
	 */
	public int getPagesize()
	{
		return pagesize;
	}

	/**
	 * @param pagesize
	 *        The pagesize to set.
	 */
	public void setPagesize(int pagesize)
	{
		this.pagesize = pagesize;
	}

	/**
	 * @return Returns the requestPage.
	 */
	public int getRequestPage()
	{
		return requestPage;
	}

	/**
	 * @param requestPage
	 *        The requestPage to set.
	 */
	public void setRequestPage(int requestPage)
	{
		this.requestPage = requestPage;
	}

	/**
	 * The Total number of results
	 * 
	 * @return
	 */
	public int getNresults()
	{
		return searchResults.getFullSize();
	}

	/**
	 * {@inheritDoc}
	 */
	public String getSearchTitle()
	{
		return Messages.getString("search_title") + " " + getSearch();
	}

	/**
	 * {@inheritDoc}
	 */
	public boolean hasAdmin()
	{
		boolean superUser = SecurityService.isSuperUser();
		return (superUser)
				|| ("true".equals(ServerConfigurationService.getString(
						"search.allow.maintain.admin", "false")) && siteService
						.allowUpdateSite(siteId));
	}

	/**
	 * {@inheritDoc}
	 */
	public String getToolUrl()
	{

		return ServerConfigurationService.getString("portalPath") + "/tool/"
				+ placementId;
	}

	public boolean hasResults()
	{
		SearchList sr = (SearchList) search();
		if (sr == null)
		{
			return false;
		}
		else
		{
			return (sr.size() > 0);
		}
	}
	public boolean foundNoResults() {
		if ( search == null || search.trim().length() == 0  ) {
			return false;
		}
		return !hasResults();
	}

	public String getOpenSearchUrl()
	{
		return ServerConfigurationService.getPortalUrl() + "/tool/" + placementId
				+ "/opensearch";
	}
	
	
	public String getSherlockIconUrl()
	{
		return FormattedText.escapeHtml(getBaseUrl() + SherlockSearchBeanImpl.UPDATE_IMAGE,false);
	}

	public String getSherlockUpdateUrl()
	{
		return FormattedText.escapeHtml(getBaseUrl() + SherlockSearchBeanImpl.UPDATE_URL,false);
	}

	public String getBaseUrl()
	{
		return ServerConfigurationService.getPortalUrl() + "/tool/" + placementId;
	}
	
	public String getPortalBaseUrl()
	{
		return ServerConfigurationService.getPortalUrl() + "/directtool/" + placementId;
	}

	public String getSiteTitle()
	{
		return FormattedText.escapeHtml(currentSite.getTitle(), false);
	}

	public String getSystemName()
	{
		return FormattedText.escapeHtml(ServerConfigurationService.getString(
				"ui.service", "Sakai"), false);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.sakaiproject.search.tool.SearchBean#getPages()
	 */
	public List<SearchPage> getPages()
	{
		List<SearchPage> pages = new ArrayList<SearchPage>();
		try
		{
			SearchList sr = (SearchList) search();
			if (sr == null) return pages;
			int npages = (sr.getFullSize()-1) / pagesize;
			int cpage = requestPage - (nlistPages / 2);
			if (cpage < 0)
			{
				cpage = 0;
			}
			int lastPage = Math.min(cpage + nlistPages, npages);
			boolean first = true;
			if (cpage == lastPage)
			{
				return pages;
			}
			else
			{
				while (cpage <= lastPage)
				{
					final String searchURL = "?search="
							+ URLEncoder.encode(search, "UTF-8") + "&page="
							+ String.valueOf(cpage);

					final String name = String.valueOf(cpage + 1);
					String cssInd = "1";
					if (first)
					{
						cssInd = "0";
						first = false;
					}
					else if (cpage == (lastPage))
					{
						cssInd = "2";
					}
					final String cssI = cssInd;
					pages.add(new SearchPage()
					{

						public String getName()
						{
							return FormattedText.escapeHtml(name, false);
						}

						public String getUrl()
						{
							return FormattedText.escapeHtml(searchURL, false);
						}

						public String getCssIndex()
						{
							return cssI;
						}

					});
					cpage++;
				}
			}

		}
		catch (Exception ex)
		{
		}
		return pages;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.sakaiproject.search.tool.SearchBean#getResults()
	 */
	public List<SearchOutputItem> getResults()
	{
		List<SearchOutputItem> l = new ArrayList<SearchOutputItem>();
		SearchList sl = search();
		for (Iterator i = sl.iterator(); i.hasNext();)
		{
			final SearchResult sr = (SearchResult) i.next();
			l.add(new SearchOutputItem()
			{

				public String getSearchResult()
				{
					try
					{
						return sr.getSearchResult();
					}
					catch (Exception ex)
					{
						return "";
					}
				}

				public String getTitle()
				{
					try
					{
						return FormattedText.escapeHtml(sr.getTitle(), false);
					}
					catch (Exception ex)
					{
						return "";
					}

				}

				public String getTool()
				{
					try
					{
						return FormattedText.escapeHtml(sr.getTool(), false);
					}
					catch (Exception ex)
					{
						return "";
					}

				}

				public String getUrl()
				{
					try
					{
						return FormattedText.escapeHtml(sr.getUrl(), false);
					}
					catch (Exception ex)
					{
						return "";
					}

				}

			});
		}
		return l;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.sakaiproject.search.tool.SearchBean#getSearchFound()
	 */
	public String getSearchFound()
	{
		SearchList sr = (SearchList) search();
		if (sr == null) return "";
		int total = sr.getFullSize();
		int start = 0;
		int end = 0;
		if (total > 0)
		{
			start = sr.getStart();
			end = Math.min(start + sr.size(), total);
			start++;
		}
		return MessageFormat.format(Messages.getString("jsp_found_line"), new Object[] {
				new Integer(start), new Integer(end), new Integer(total),
				new Double(timeTaken) });

	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.sakaiproject.search.tool.SearchBean#getTerms()
	 */
	public List<SearchTerm> getTerms()
	{
		if (termList == null)
		{
			mergeTerms();
			finalTermList = null;
		}
		if (termList == null)
		{
			return new ArrayList<SearchTerm>();
		}
		if (finalTermList != null)
		{
			return finalTermList;
		}
		finalTermList = new ArrayList<SearchTerm>();
		List<TermHolder> l = termList.subList(0, Math.min(nTerms, termList.size()));
		int j = 0;
		for (Iterator li = l.iterator(); li.hasNext();)
		{
			TermHolder t = (TermHolder) li.next();
			t.position = j;
			j++;
		}

		Collections.sort(l, new Comparator<TermHolder>()
		{
			Collator c = Collator.getInstance();

			public int compare(TermHolder a, TermHolder b)
			{
				return c.compare(a.term, b.term);
			}

		});
		int factor = 1;
		j = l.size();
		for (Iterator li = l.iterator(); li.hasNext();)
		{
			TermHolder t = (TermHolder) li.next();
			factor = Math.max(t.frequency, factor);
		}

		for (Iterator li = l.iterator(); li.hasNext();)
		{
			final TermHolder t = (TermHolder) li.next();
			float f = (topTerms * t.frequency) / factor;
			if (relativeTerms)
			{
				f = (topTerms * (l.size() - t.position)) / l.size();
			}
			f = f / divisorTerms;
			j--;
			final String weight = String.valueOf(f);
			finalTermList.add(new SearchTerm()
			{

				public String getName()
				{
					return FormattedText.escapeHtml(t.term, false);
				}

				public String getUrl()
				{
					try
					{
						return FormattedText
								.escapeHtml("?panel=Main&search=" + URLEncoder.encode(t.term,"UTF-8"), false);
					}
					catch (UnsupportedEncodingException e)
					{
						return FormattedText
						.escapeHtml("?panel=Main&search=" + URLEncoder.encode(t.term), false);

					}
				}

				public String getWeight()
				{
					return weight;
				}
			});
		}
		return finalTermList;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.sakaiproject.search.tool.SearchBean#hasError()
	 */
	public boolean hasError()
	{
		return (errorMessage != null);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.sakaiproject.search.tool.SearchBean#getErrorMessage()
	 */
	public String getErrorMessage()
	{
		return FormattedText.escapeHtml(errorMessage, false);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.sakaiproject.search.tool.api.SearchBean#getRssUrl()
	 */
	public String getRssURL()
	{
		if (hasResults())
		{

			try
			{
				return FormattedText.escapeHtml(getToolUrl() + "/rss20?search="
						+ URLEncoder.encode(search, "UTF-8"), false);
			}
			catch (UnsupportedEncodingException e)
			{
				return FormattedText.escapeHtml(getToolUrl() + "/rss20?search="
						+ URLEncoder.encode(search), false);
			}
		}
		else
		{
			return null;
		}
	}

	/* (non-Javadoc)
	 * @see org.sakaiproject.search.tool.api.SearchBean#getDateNow()
	 */
	public String getDateNow()
	{
		SimpleDateFormat format = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z");
		return format.format(new Date(System.currentTimeMillis()));
	}

	/* (non-Javadoc)
	 * @see org.sakaiproject.search.tool.api.SearchBean#getRequestUrl()
	 */
	public String getRequestUrl()
	{
		return FormattedText.escapeHtml(requestURL, false);
	}

}
