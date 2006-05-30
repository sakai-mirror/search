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

import javax.servlet.RequestDispatcher;
import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.sakaiproject.tool.api.Tool;
import org.sakaiproject.tool.api.ToolSession;
import org.sakaiproject.tool.api.SessionManager;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.context.support.WebApplicationContextUtils;

/**
 * @author andrew
 */
// FIXME: Tool
public class ControllerServlet extends HttpServlet
{
	public static Log log = LogFactory.getLog(ControllerServlet.class);

	/**
	 * Required for serialization... also to stop eclipse from giving me a
	 * warning!
	 */
	private static final long serialVersionUID = 676743152200357708L;

	public static final String SAVED_REQUEST_URL = "org.sakaiproject.search.api.last-request-url";

	private static final String PANEL = "panel";

	private static final Object TITLE_PANEL = "Title";

	private WebApplicationContext wac;

	private SearchBeanFactory searchBeanFactory = null;

	private SessionManager sessionManager;

	public void init(ServletConfig servletConfig) throws ServletException
	{

		super.init(servletConfig);

		ServletContext sc = servletConfig.getServletContext();

		wac = WebApplicationContextUtils.getWebApplicationContext(sc);
		try
		{
			searchBeanFactory = (SearchBeanFactory) wac
					.getBean("search-searchBeanFactory");
		}
		catch (Exception ex)
		{
		}

	}

	protected void doGet(HttpServletRequest request,
			HttpServletResponse response) throws ServletException, IOException
	{
		execute(request, response);
	}

	protected void doPost(HttpServletRequest request,
			HttpServletResponse response) throws ServletException, IOException
	{
		execute(request, response);
	}

	public void execute(HttpServletRequest request, HttpServletResponse response)
			throws ServletException, IOException
	{

		if (wac == null)
		{
			wac = WebApplicationContextUtils
					.getRequiredWebApplicationContext(this.getServletContext());
			if (wac == null)
			{
				response.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE,
						"Cannot get WebApplicationContext");
				return;
			}

		}
		if (searchBeanFactory == null)
		{
			searchBeanFactory = (SearchBeanFactory) wac
					.getBean("search-searchBeanFactory");
		}
		if (sessionManager == null)
		{
			sessionManager = (SessionManager) wac.getBean(SessionManager.class
					.getName());
		}

		request.setAttribute(Tool.NATIVE_URL, Tool.NATIVE_URL);

		addLocalHeaders(request);

		String targetURL = persistState(request);
		if (targetURL != null && targetURL.trim().length() > 0)
		{
			response.sendRedirect(targetURL);
			return;
		}
		if (TITLE_PANEL.equals(request.getParameter(PANEL)))
		{

			String targetPage = "/WEB-INF/pages/title.jsp";
			RequestDispatcher rd = request.getRequestDispatcher(targetPage);
			rd.forward(request, response);

		}
		else
		{
			String path = request.getPathInfo();
			if (path == null || path.length() == 0)
			{
				path = "/index";
			}
			if (!path.startsWith("/"))
			{
				path = "/" + path;
			}
			String targetPage = "/WEB-INF/pages" + path + ".jsp";
			request.setAttribute(SearchBeanFactory.SEARCH_BEAN_FACTORY_ATTR,
					searchBeanFactory);
			RequestDispatcher rd = request.getRequestDispatcher(targetPage);
			rd.forward(request, response);

		}

		request.removeAttribute(Tool.NATIVE_URL);
	}

	public void addLocalHeaders(HttpServletRequest request)
	{
		String sakaiHeader = (String) request.getAttribute("sakai.html.head");
		String skin = "default/"; // this could be changed in the future to make search skin awaire
		String localStylesheet = "<link href=\"/sakai-search-tool/styles/"+skin+"searchStyle.css\" type=\"text/css\" rel=\"stylesheet\" media=\"all\" />";
		request.setAttribute("sakai.html.head", localStylesheet + sakaiHeader);
	}

	/**
	 * returns the request state for the tool. If the state is restored, we set
	 * the request attribute RWikiServlet.REQUEST_STATE_RESTORED to Boolean.TRUE
	 * and a Thread local named RWikiServlet.REQUEST_STATE_RESTORED to
	 * Boolean.TRUE. These MUST be checked by anything that modifies state, to
	 * ensure that a reinitialisation of Tool state does not result in a repost
	 * of data.
	 * 
	 * @param request
	 * @return
	 */
	private String persistState(HttpServletRequest request)
	{
		ToolSession ts = sessionManager.getCurrentToolSession();
		if (isPageToolDefault(request))
		{
			log.debug("Incomming URL is " + request.getRequestURL().toString()
					+ "?" + request.getQueryString());
			log.debug("Restore " + ts.getAttribute(SAVED_REQUEST_URL));
			return (String) ts.getAttribute(SAVED_REQUEST_URL);
		}
		if (isPageRestorable(request))
		{
			ts.setAttribute(SAVED_REQUEST_URL, request.getRequestURL()
					.toString()
					+ "?" + request.getQueryString());
			log.debug("Saved " + ts.getAttribute(SAVED_REQUEST_URL));
		}
		return null;
	}

	/**
	 * Check to see if the reques represents the Tool default page. This is not
	 * the same as the view Home. It is the same as first entry into a Tool or
	 * when the page is refreshed
	 * 
	 * @param request
	 * @return true if the page is the Tool default page
	 */
	private boolean isPageToolDefault(HttpServletRequest request)
	{
		if (TITLE_PANEL.equals(request.getParameter(PANEL))) return false;
		String pathInfo = request.getPathInfo();
		String queryString = request.getQueryString();
		String method = request.getMethod();
		return ("GET".equalsIgnoreCase(method)
				&& (pathInfo == null || request.getPathInfo().length() == 0) && (queryString == null || queryString
				.length() == 0));
	}

	/**
	 * Check to see if the request represents a page that can act as a restor
	 * point.
	 * 
	 * @param request
	 * @return true if it is possible to restore to this point.
	 */
	private boolean isPageRestorable(HttpServletRequest request)
	{
		if (TITLE_PANEL.equals(request.getParameter(PANEL))) return false;

		if ("GET".equalsIgnoreCase(request.getMethod())) return true;

		return false;
	}

}
