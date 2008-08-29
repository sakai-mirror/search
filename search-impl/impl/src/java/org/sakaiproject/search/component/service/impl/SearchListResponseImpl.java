/**********************************************************************************
 * $URL$
 * $Id$
 ***********************************************************************************
 *
 * Copyright (c) 2003, 2004, 2005, 2006, 2007 Sakai Foundation
 *
 * Licensed under the Educational Community License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.osedu.org/licenses/ECL-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 **********************************************************************************/

package org.sakaiproject.search.component.service.impl;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Stack;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.search.Query;
import org.sakaiproject.search.api.SearchIndexBuilder;
import org.sakaiproject.search.api.SearchList;
import org.sakaiproject.search.api.SearchResult;
import org.sakaiproject.search.api.SearchService;
import org.sakaiproject.search.filter.SearchItemFilter;
import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.InputSource;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.AttributesImpl;
import org.xml.sax.helpers.XMLReaderFactory;

/**
 * @author ieb
 */
public class SearchListResponseImpl implements SearchList, ContentHandler
{

	private static Log log = LogFactory.getLog(SearchListResponseImpl.class);

	private Query query;

	private int start = 0;

	private int end = 500;

	private Analyzer analyzer;

	private SearchItemFilter filter;

	private SearchIndexBuilder searchIndexBuilder;

	private SearchService searchService;

	private List resultsList;

	private Stack stack;

	private Object errorMessage;

	private int size;

	private int fullsize;

	public SearchListResponseImpl(String response, Query query, int start,
			int end, Analyzer analyzer, SearchItemFilter filter,
			 SearchIndexBuilder searchIndexBuilder,
			SearchService searchService) throws SAXException, IOException
	{

		this.query = query;
		this.start = start;
		this.end = end;
		this.analyzer = analyzer;
		this.filter = filter;
		this.searchIndexBuilder = searchIndexBuilder;
		this.searchService = searchService;

		if (log.isDebugEnabled()) {
			log.debug("search response: ["+response+"]");
		}
		
		XMLReader xr = XMLReaderFactory.createXMLReader();
		xr.setContentHandler(this);
		InputSource is = new InputSource(new StringReader(response));
		xr.parse(is);

		if (errorMessage != null)
		{
			log
					.error("Failed to perform remote request, remote exception was: \n"
							+ errorMessage);
			throw new IOException("Failed to perform remote request ");
		}

	}

	/**
	 * @{inheritDoc}
	 */
	public Iterator iterator(final int startAt)
	{
		return new Iterator()
		{
			int counter = Math.max(startAt, start) - start;

			public boolean hasNext()
			{
				return counter < resultsList.size();
			}

			public Object next()
			{

				int thisHit = counter;
				counter++;
				if (log.isDebugEnabled())
				{
					log.debug("Iterator Getting item " + thisHit);
				}
				return filter.filter((SearchResult) resultsList.get(thisHit));
			}

			public void remove()
			{
				throw new UnsupportedOperationException("Not Implemented");
			}

		};
	}

	public int size()
	{
		return size;
	}

	public int getFullSize()
	{
		return fullsize;
	}

	public boolean isEmpty()
	{
		return (size() == 0);
	}

	public boolean contains(Object arg0)
	{
		throw new UnsupportedOperationException("Not Implemented");
	}

	public Iterator iterator()
	{
		return iterator(0);
	}

	public Object[] toArray()
	{
		Object[] o = new Object[size()];
		for (int i = 0; i < o.length; i++)
		{

			o[i] = filter.filter((SearchResult) resultsList.get(i));
		}
		return o;
	}

	public Object[] toArray(Object[] arg0)
	{
		if (arg0 instanceof SearchResult[])
		{
			return toArray();
		}
		return null;
	}

	public boolean add(Object arg0)
	{
		throw new UnsupportedOperationException("Not Implemented");
	}

	public boolean remove(Object arg0)
	{
		throw new UnsupportedOperationException("Not Implemented");
	}

	public boolean containsAll(Collection arg0)
	{
		throw new UnsupportedOperationException("Not Implemented");
	}

	public boolean addAll(Collection arg0)
	{
		throw new UnsupportedOperationException("Not Implemented");
	}

	public boolean addAll(int arg0, Collection arg1)
	{
		throw new UnsupportedOperationException("Not Implemented");
	}

	public boolean removeAll(Collection arg0)
	{
		throw new UnsupportedOperationException("Not Implemented");
	}

	public boolean retainAll(Collection arg0)
	{
		throw new UnsupportedOperationException("Not Implemented");
	}

	public void clear()
	{
		throw new UnsupportedOperationException("Not Implemented");
	}

	public Object get(int arg0)
	{
		return filter.filter((SearchResult) resultsList.get(arg0));
	}

	public Object set(int arg0, Object arg1)
	{
		throw new UnsupportedOperationException("Not Implemented");
	}

	public void add(int arg0, Object arg1)
	{
		throw new UnsupportedOperationException("Not Implemented");

	}

	public Object remove(int arg0)
	{
		throw new UnsupportedOperationException("Not Implemented");
	}

	public int indexOf(Object arg0)
	{
		throw new UnsupportedOperationException("Not Implemented");
	}

	public int lastIndexOf(Object arg0)
	{
		throw new UnsupportedOperationException("Not Implemented");
	}

	public ListIterator listIterator()
	{
		throw new UnsupportedOperationException("Not Implemented");
	}

	public ListIterator listIterator(int arg0)
	{
		throw new UnsupportedOperationException("Not Implemented");
	}

	public List subList(int arg0, int arg1)
	{
		throw new UnsupportedOperationException("Not Implemented");
	}

	public int getStart()
	{
		return start;
	}

	public void characters(char[] ch, int start, int length)
			throws SAXException
	{

		StackElement se = (StackElement) stack.peek();
		se.append(ch, start, length);
	}

	public void endDocument() throws SAXException
	{
	}

	public void endElement(String uri, String localName, String qName)
			throws SAXException
	{
		if (log.isDebugEnabled())
		{
			log.debug("End Element uri:" + uri + " ln:" + localName + " qn:"
					+ qName);
		}
		StackElement se = (StackElement) stack.pop();
		if ("error".equals(localName))
		{
			errorMessage = se.getContent();
			log.error("Error Message found from remote search " + errorMessage);
		}
		else if ("result".equals(localName))
		{

			SearchResult sr;
			try
			{
				sr = new SearchResultResponseImpl(se.getAttributes(), query,
						analyzer, searchIndexBuilder,
						searchService);
			}
			catch (IOException e)
			{
				throw new SAXException(e.getMessage(), e);
			}
			resultsList.add(sr);
			if (log.isDebugEnabled())
			{
				log.debug("Added Search Result " + resultsList.size());
			}
		}
		else if ("results".equals(localName))
		{
			fullsize = Integer
					.parseInt(se.getAttributes().getValue("fullsize"));
			start = Integer.parseInt(se.getAttributes().getValue("start"));
			size = Integer.parseInt(se.getAttributes().getValue("size"));

		}
	}

	public void endPrefixMapping(String prefix) throws SAXException
	{
	}

	public void ignorableWhitespace(char[] ch, int start, int length)
			throws SAXException
	{
	}

	public void processingInstruction(String target, String data)
			throws SAXException
	{
	}

	public void setDocumentLocator(Locator locator)
	{
	}

	public void skippedEntity(String name) throws SAXException
	{
	}

	public void startDocument() throws SAXException
	{
		resultsList = new ArrayList();
		stack = new Stack();

	}

	public void startElement(String uri, String localName, String qName,
			Attributes atts) throws SAXException
	{
		if (log.isDebugEnabled())
		{
			log.debug("Start Element uri:" + uri + " ln:" + localName + " qn:"
					+ qName);
		}
		StackElement se = new StackElement(uri, localName, qName, atts);
		stack.push(se);
	}

	public void startPrefixMapping(String prefix, String uri)
			throws SAXException
	{
	}

	public class StackElement
	{

		private String uri;

		private String localName;

		private String name;

		private Attributes atts;

		private StringBuilder content;

		public StackElement(String uri, String localName, String name,
				Attributes atts)
		{
			this.uri = uri;
			this.localName = localName;
			this.name = name;
			this.atts = new AttributesImpl(atts);
			this.content = new StringBuilder();
		}

		public Attributes getAttributes()
		{
			return atts;
		}

		public String getContent()
		{
			return content.toString();
		}

		public void append(char[] ch, int start, int length)
		{
			content.append(ch, start, length);

		}

	}

}
