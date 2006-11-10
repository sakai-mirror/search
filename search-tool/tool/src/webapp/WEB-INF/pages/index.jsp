<%@ page language="java" contentType="text/html;charset=UTF-8"  %><!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN" "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd"> 
<%
	org.sakaiproject.search.tool.SearchBeanFactory searchBeanFactory = 
	  (org.sakaiproject.search.tool.SearchBeanFactory)
		request.getAttribute(org.sakaiproject.search.tool.SearchBeanFactory.SEARCH_BEAN_FACTORY_ATTR);
	org.sakaiproject.search.tool.SearchBean searchBean = searchBeanFactory.newSearchBean(request);
	
	String errorMessageFormat = "<div class=\"alertMessage\" >{0}</div>";
	String searchItemFormat = 
	          "<div class=\"searchItem\" > " 
	        + "<span class=\"searchTool\">{0}:</span> "
	        + "<a href=\"{1}\" target=\"searchresult\" class=\"searchTopLink\" >{2}</a>"
			+ "<div class=\"searchItemBody\" >{3}</div> "
			+ "<a href=\"{1}\" target=\"searchresult\" class=\"searchBottonLink\" >{1}</a> "
			+ "</div>";

	String pagerFormat = "<a href=\"{0}\" class=\"searchPage\" >{1}</a></li>";
	String singlePageFormat = " ";
	String searchTerms = searchBean.getSearch();
	String rssURL = "";
	String rssLink = "";
	if ( searchTerms != null && searchTerms.length() > 0 ) {
		rssURL = searchBean.getToolUrl()+"/rss20?search="+java.net.URLEncoder.encode(searchBean.getSearch());
		rssLink = "<link rel=\"alternate\" title=\"Sakai RSS Search for: "+searchTerms+" \" " 
 			+ " href=\""+rssURL+"\" type=\"application/rss+xml\" /> ";
    
	}
	String searchHeaderFormat = "<div class=\"searchHeader\">Found {0} to {1} of {2} documents ({3} seconds) <a href=\""+rssURL+"\" target=\"rss\" ><img src=\"/sakai-search-tool/images/rss.gif\" alt=\"RSS\" border=\"0\" /></a></div>";
	String termsFormat = "<span style=\"font-size:{1}em;\" ><a href=\"?panel=Main&search={0}\" >{0}</a></span> ";

%>
<html>
  <head>
      <meta http-equiv="Content-Type" content="text/html; charset=UTF-8" />
      <meta http-equiv="Content-Style-Type" content="text/css" />
     <title>Search: <%= searchBean.getSearchTitle() %></title>
      <%= request.getAttribute("sakai.html.head") %>
      <%= rssLink %>  
    </head>
    <body 
    onload="setMainFrameHeightNoScroll('<%= request.getAttribute("sakai.tool.placement.id") %>');parent.updCourier(doubleDeep,ignoreCourier); callAllLoaders(); "
    >
<%@include file="header.jsp"%>
    	<div class="portletBody">    		
    	
	  <% if ( searchBean.hasAdmin() ) { %>
    <div class="navIntraTool">
	  <span class="rwiki_pageLinks">
	    <a href="<%= searchBean.getToolUrl() %>/admin/index">Admin</a>
	  </span>
    </div>
	  <% } %>
    <%
    if ( searchBean.isEnabled() ) 
    {
    %>
	<div class="navPanel">
		<div class="searchBox">
			<form action="?#" method="get" class="inlineForm"  >  
				<label for="search">
					Search:  
				</label>
				<input type="hidden" name="panel" value="Main" />
				<input type="text" id="search"  name="search" size="42" maxlength="1024" value="<%= searchBean.getSearch() %>"/>
				<input type="submit" name="sb" value="Go" />
			</form>
		</div>
	</div>	
	
	
	<%= searchBean.getHeader(searchHeaderFormat) %>

    <%
    	if ( searchBean.hasResults() ) 
    	{
    %>
    <div class="searchPageContainer" >
    	<%= searchBean.getPager(pagerFormat, singlePageFormat) %>
    </div>
    <div class="searchTabsContainer" >
	   <span id="results" class="tabHeadOn" >        	
		  <p class="tabhead" title="Results" ><a href="#" onClick="selectTabs('tagsTab','tabOn','tabOff','resultsTab','tabOff','tabOn','tags','tabHeadOn','tabHeadOff','results','tabHeadOff','tabHeadOn'); return false;" >Results</a></p>
       </span>
	   <span id="tags" class="tabHeadOff" >        	
		  <p class="tabhead" title="Tags" ><a href="#" onClick="selectTabs('tagsTab','tabOff','tabOn','resultsTab','tabOn','tabOff','tags','tabHeadOff','tabHeadOn','results','tabHeadOn','tabHeadOff'); return false;" >Tags</a></p>
    	</span>
    </div>
    <div class="searchResultsContainer" >
		  <div id="resultsTab" class="tabOn" >
    <%= searchBean.getSearchResults(searchItemFormat,errorMessageFormat) %>
          </div>
		  <div id="tagsTab" class="tabOff" >
    	     <%= searchBean.getTerms(termsFormat,100,10,3,true) %>
    	  </div>
    </div>
    <div class="searchPageContainer" >
    	<%= searchBean.getPager(pagerFormat, singlePageFormat) %>
    </div>
    <%
    	}
    }
    else
    {
    %>
    <p>
    The search tool is not enabled, please ask your administrator to set search.experimental = true in sakai.properties
    </p>
    <%
    }
    %>

<%@include file="footer.jsp"%>
</div>
</body>
</html>
