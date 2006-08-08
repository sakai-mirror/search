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

package org.sakaiproject.search.component.service.impl;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import javax.sql.DataSource;

import org.apache.commons.id.IdentifierGenerator;
import org.apache.commons.id.uuid.UUID;
import org.apache.commons.id.uuid.VersionFourGenerator;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.hibernate.HibernateException;
import org.sakaiproject.component.api.ComponentManager;
import org.sakaiproject.component.cover.ServerConfigurationService;
import org.sakaiproject.entity.api.EntityManager;
import org.sakaiproject.event.api.EventTrackingService;
import org.sakaiproject.search.api.SearchIndexBuilder;
import org.sakaiproject.search.api.SearchIndexBuilderWorker;
import org.sakaiproject.search.api.SearchService;
import org.sakaiproject.search.dao.SearchIndexBuilderWorkerDao;
import org.sakaiproject.search.model.SearchBuilderItem;
import org.sakaiproject.search.model.SearchWriterLock;
import org.sakaiproject.search.model.impl.SearchWriterLockImpl;
import org.sakaiproject.tool.api.SessionManager;
import org.sakaiproject.user.api.User;
import org.sakaiproject.user.api.UserDirectoryService;

public class SearchIndexBuilderWorkerImpl implements Runnable,
		SearchIndexBuilderWorker
{

	/**
	 * The lock we use to ensure single search index writer
	 */
	public static final String LOCKKEY = "searchlockkey";

	protected static final Object GLOBAL_CONTEXT = null;

	private static final String NO_NODE = "none";

	private static final String NODE_LOCK = "nodelockkey";
	
	private static Log log = LogFactory
			.getLog(SearchIndexBuilderWorkerImpl.class);

	private final int numThreads = 2;

	/**
	 * The maximum sleep time for the wait/notify semaphore
	 */
	public long sleepTime = 5L * 60000L;

	/**
	 * The currently running index Builder thread
	 */
	private Thread indexBuilderThread[] = new Thread[numThreads];

	/**
	 * sync object
	 */
	private Object threadStartLock = new Object();

	/**
	 * dependency: the search index builder that is accepting new items
	 */
	private SearchIndexBuilderImpl searchIndexBuilder = null;

	/**
	 * dependency: the current search service, used to get the location of the
	 * index
	 */
	private SearchService searchService = null;

	private DataSource dataSource = null;

	private IdentifierGenerator idgenerator = new VersionFourGenerator();

	/**
	 * Semaphore
	 */
	private Object sem = new Object();

	/**
	 * The number of items to process in a batch, default = 100
	 */
	private int indexBatchSize = 100;

	private boolean enabled = false;

	private SessionManager sessionManager;

	private UserDirectoryService userDirectoryService;

	private EntityManager entityManager;

	private EventTrackingService eventTrackingService;

	private boolean runThreads = true;

	private ThreadLocal nodeIDHolder = new ThreadLocal();

	private SearchIndexBuilderWorkerDao searchIndexBuilderWorkerDao = null;

	private long lastLock = System.currentTimeMillis();

	/**
	 * Activity acts as a counter that indicates the number of events recieved.
	 * The Workers may consult this to determine if they should run.
	 */
	private int activity = 0;

	private long lastEvent = System.currentTimeMillis();

	private long lastIndex;

	private long startDocIndex;

	private String nowIndexing;

	private String lastIndexing;

	private static HashMap nodeIDList = new HashMap();;

	private static String lockedTo = null;

	private static String SELECT_LOCK_SQL = "select id, nodename, "
			+ "lockkey, expires from searchwriterlock where lockkey = ?";

	private static String UPDATE_LOCK_SQL = "update searchwriterlock set "
			+ "nodename = ?, expires = ? where id = ? "
			+ "and nodename = ? and lockkey = ? ";

	private static String INSERT_LOCK_SQL = "insert into searchwriterlock "
			+ "( id,nodename,lockkey, expires ) values ( ?, ?, ?, ? )";

	private static String COUNT_WORK_SQL = " select count(*) "
			+ "from searchbuilderitem where searchstate = ? and searchaction <> ? ";

	private static String CLEAR_LOCK_SQL = "update searchwriterlock "
			+ "set nodename = ?, expires = ? where nodename = ? and lockkey = ? ";

	private static String SELECT_NODE_LOCK_SQL = "select id, nodename, "
			+ "lockkey, expires from searchwriterlock where lockkey like '"
			+ NODE_LOCK + "%'";

	private static String UPDATE_NODE_LOCK_SQL = "update searchwriterlock set "
			+ "expires = ? where nodename = ? and lockkey = ? ";

	private static final String SELECT_EXPIRED_NODES_SQL = "select id from searchwriterlock "
			+ "where lockkey like '" + NODE_LOCK + "%' and expires < ? ";

	private static final String DELETE_LOCKNODE_SQL = "delete from searchwriterlock "
			+ "where id = ? ";

	public void init()
	{
		ComponentManager cm = org.sakaiproject.component.cover.ComponentManager
				.getInstance();
		eventTrackingService = (EventTrackingService) load(cm,
				EventTrackingService.class.getName());
		entityManager = (EntityManager) load(cm, EntityManager.class.getName());
		userDirectoryService = (UserDirectoryService) load(cm,
				UserDirectoryService.class.getName());
		searchIndexBuilder = (SearchIndexBuilderImpl) load(cm,
				SearchIndexBuilder.class.getName());
		searchService = (SearchService) load(cm, SearchService.class.getName());

		sessionManager = (SessionManager) load(cm, SessionManager.class
				.getName());

		enabled = "true".equals(ServerConfigurationService.getString(
				"search.experimental", "false"));
		try
		{
			if (searchIndexBuilder == null)
			{
				log.error("Search Index Worker needs SearchIndexBuilder ");
			}
			if (searchService == null)
			{
				log.error("Search Index Worker needs SearchService ");
			}
			if (searchIndexBuilderWorkerDao == null)
			{
				log
						.error("Search Index Worker needs SearchIndexBuilderWorkerDao ");
			}
			if (eventTrackingService == null)
			{
				log.error("Search Index Worker needs EventTrackingService ");
			}
			if (entityManager == null)
			{
				log.error("Search Index Worker needs EntityManager ");
			}
			if (userDirectoryService == null)
			{
				log.error("Search Index Worker needs UserDirectortyService ");
			}
			if (sessionManager == null)
			{
				log.error("Search Index Worker needs SessionManager ");
			}
			log.debug("init start");
			for (int i = 0; i < indexBuilderThread.length; i++)
			{
				indexBuilderThread[i] = new Thread(this);
				indexBuilderThread[i].setName(String.valueOf(i) + "::"
						+ this.getClass().getName());
				indexBuilderThread[i].start();
			}
		}
		catch (Throwable t)
		{
			log.error("Failed to init ", t);
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
	 * Main run target of the worker thread {@inheritDoc}
	 */
	public void run()
	{
		if (!enabled) return;
		String threadName = Thread.currentThread().getName();
		String tn = threadName.substring(0, 1);
		log.debug("Index Builder Run " + tn + "_" + threadName);
		int threadno = Integer.parseInt(tn);

		String nodeID = getNodeID();

		org.sakaiproject.component.cover.ComponentManager.waitTillConfigured();

		try
		{

			while (runThreads)
			{
				log.debug("Run Processing Thread");
				boolean locked = false;
				org.sakaiproject.tool.api.Session s = null;
				if (s == null)
				{
					s = sessionManager.startSession();
					User u = userDirectoryService.getUser("admin");
					s.setUserId(u.getId());
				}

				while (runThreads)
				{
					sessionManager.setCurrentSession(s);
					try
					{
						int totalDocs = searchIndexBuilder.getPendingDocuments();
						long lastEvent = getLastEventTime();
						int activity = getActivity();
						long now = System.currentTimeMillis();
						long interval = now-lastEvent;
					
						// if activity == totalDocs and interval > 10
						boolean process = false;
						if ( totalDocs < 20 && interval > 20000 ) {
							process = true;
						} else if ( totalDocs >= 20 && totalDocs < 50 && interval > 10000   ) {
							process = true;
						} else if ( totalDocs >= 50 && totalDocs < 90 && interval > 5000 ) {
							process = true;
						} else if ( totalDocs > 90 ) {
							process = true;
						}
						if ( process ) {
							resetActivity();
						} 
					

						// should this node consider taking the lock ?
						long lastLockInterval = (System.currentTimeMillis() - lastLock);
						long lastLockMetric = lastLockInterval*totalDocs;
						
						// if we have 1000 docs, then indexing should happen after 10 seconds break
						// 1000*10000 10000000
						// 500 docs/ 20 seconds
						//
						
						// make certain that we are alive
						
						if (lastLockMetric > 10000000L || lastLockInterval > 60000L)
						{
						if (process && getLockTransaction(2L*60L*1000L))
						{

								log.debug("===" + nodeID
										+ "=============PROCESSING ");
								if (lockedTo != null && lockedTo.equals(nodeID))
								{
									log
											.error("+++++++++++++++Local Lock Collision+++++++++++++");
								}
								lockedTo = nodeID;

								lastLock = System.currentTimeMillis();
							
								searchIndexBuilderWorkerDao
										.processToDoListTransaction(this);

								lastLock = System.currentTimeMillis();

								if (lockedTo.equals(nodeID))
								{
									lockedTo = null;
								}
								else
								{
									log
											.error("+++++++++++++++++++++++++++Lost Local Lock+++++++++++");
								}
								log.debug("===" + nodeID
										+ "=============COMPLETED ");
							}
							else
							{
								break;
							}
						}
						else
						{
							// make certain the node updates hearbeat
							updateNodeLock(2L*60L*1000L);
							searchService.reload();
							log.debug("Not taking Lock, too much activity");
							break;
						}
					}
					finally
					{
						clearLockTransaction();
					}
				}
				if (!runThreads)
				{
					break;
				}
				try
				{
					log.debug("Sleeping Processing Thread");
					synchronized (sem)
					{
						log.debug("++++++WAITING " + nodeID);
						sem.wait(sleepTime);

						log.debug("+++++ALIVE " + nodeID);
					}
					log.debug("Wakey Wakey Processing Thread");

					if (org.sakaiproject.component.cover.ComponentManager
							.hasBeenClosed())
					{
						runThreads = false;
						break;
					}
				}
				catch (InterruptedException e)
				{
					log.debug(" Exit From sleep " + e.getMessage());
					break;
				}
			}
		}
		catch (Throwable t)
		{
			log.warn("Failed in IndexBuilder ", t);
		}
		finally
		{

			log.debug("IndexBuilder run exit " + threadName);
			indexBuilderThread[threadno] = null;
		}
	}

	private String getNodeID()
	{
		String nodeID = (String) nodeIDHolder.get();
		if (nodeID == null)
		{
			UUID uuid = (UUID) idgenerator.nextIdentifier();
			nodeID = uuid.toString();
			nodeIDHolder.set(nodeID);
			if ( nodeIDList .get(nodeID) == null ) {
				nodeIDList.put(nodeID,nodeID);
			} else {
				log.error("============NODE ID "+nodeID+" has already been issued, there must be a clash");
			}
		}
		return nodeID;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.sakaiproject.search.component.service.impl.SearchIndexBuilderWorkerAPI#updateNodeLock(java.sql.Connection)
	 */
	public void updateNodeLock(long lifeLeft) throws SQLException
	{

		Connection connection = null;
		String nodeID = getNodeID();
		
		
		
		
		PreparedStatement updateNodeLock = null;
		PreparedStatement deleteExpiredNodeLock = null;
		PreparedStatement selectExpiredNodeLock = null;
		PreparedStatement insertLock = null;
		ResultSet resultSet = null;
		String threadID = Thread.currentThread().getName();
		boolean savedautocommit = false;
		Timestamp now = new Timestamp(System.currentTimeMillis());
		// a node can expire, after 2 minutes, to indicate to an admin that it
		// is dead
		// the admin can then force the
		Timestamp nodeExpired = new Timestamp(now.getTime()
				+ lifeLeft);
		try
		{
			connection = dataSource.getConnection();
			boolean savedautocommen = connection.getAutoCommit();
			connection.setAutoCommit(false);

			updateNodeLock = connection.prepareStatement(UPDATE_NODE_LOCK_SQL);
			deleteExpiredNodeLock = connection
					.prepareStatement(DELETE_LOCKNODE_SQL);
			selectExpiredNodeLock = connection
					.prepareStatement(SELECT_EXPIRED_NODES_SQL);
			insertLock = connection.prepareStatement(INSERT_LOCK_SQL);
			int retries = 5;
			boolean updated = false;
			while (!updated && retries > 0)
			{
				try
				{
					
					try {
						insertLock.clearParameters();
						insertLock.setString(1, "Node:" + nodeID); // id
						insertLock.setString(2, nodeID); // nodename
						insertLock.setString(3, NODE_LOCK + nodeID); // lockkey
						insertLock.setTimestamp(4, nodeExpired); // expires
						log.debug(threadID+" Doing "+INSERT_LOCK_SQL+":{"+"Node:" + nodeID+"}{"+nodeID+"}{"+NODE_LOCK + nodeID+"}{"+nodeExpired+"}");
						insertLock.executeUpdate();
					} catch ( SQLException ex ) {
						updateNodeLock.clearParameters();
						updateNodeLock.setTimestamp(1, nodeExpired); // expires
						updateNodeLock.setString(2, nodeID); // nodename
						updateNodeLock.setString(3, NODE_LOCK + nodeID); // lockkey
						log.debug(threadID+" Doing "+UPDATE_NODE_LOCK_SQL+":{"+nodeExpired+"}{"+nodeID+"}{"+NODE_LOCK + nodeID+"}");
						if (updateNodeLock.executeUpdate() != 1)
						{
							log.warn("Failed to update node heartbeat "+nodeID);
						}						
					}
					log.debug(threadID+" Doing Commit ");
					connection.commit();
					updated = true;
				}
				catch (SQLException e)
				{
					log.warn("Retrying ", e);
					try
					{
						connection.rollback();
					}
					catch (Exception ex)
					{

					}
					retries--;
					try
					{
						Thread.sleep(100);
					}
					catch (InterruptedException ie)
					{
					}
				}
			}
			if (!updated)
			{
				log.error("Failed to update node lock, will try next time ");
			}
			else 
			{
				log.debug("Updated Node Lock on "+nodeID+ " to Expire at"+nodeExpired);
			}
			
			

			retries = 5;
			updated = false;
			while (!updated && retries > 0)
			{
				try
				{
					selectExpiredNodeLock.clearParameters();
					selectExpiredNodeLock.setTimestamp(1, now);
					log.debug(threadID+" Doing "+SELECT_EXPIRED_NODES_SQL+":{"+now+"}");

					resultSet = selectExpiredNodeLock.executeQuery();
					while (resultSet.next())
					{
						String id = resultSet.getString(1);
						deleteExpiredNodeLock.clearParameters();
						deleteExpiredNodeLock.setString(1, id);
						deleteExpiredNodeLock.execute();
						connection.commit();
					}
					log.debug(threadID+" Doing Commit");
					connection.commit();
					resultSet.close();
					updated = true;
				}
				catch (SQLException e)
				{

					log.info("Retrying Delete Due to  "+e.getMessage());
					log.debug("Detailed Traceback  ",e);
					try
					{
						resultSet.close();
					}
					catch (Exception ex)
					{

					}
					try
					{
						connection.rollback();
					}
					catch (Exception ex)
					{

					}
					retries--;
					try
					{
						Thread.sleep(100);
					}
					catch (InterruptedException ie)
					{
					}
				}
			}
			if (!updated)
			{
				log.warn("Failed to clear old nodes, will try next time ");
			}

		}
		catch (Exception ex)
		{
			log.error("Failed to register node ", ex);
			connection.rollback();
		}
		finally
		{
			if (resultSet != null)
			{
				try
				{
					resultSet.close();
				}
				catch (SQLException e)
				{
				}
			}
			if (insertLock != null)
			{
				try
				{
					insertLock.close();
				}
				catch (SQLException e)
				{
				}
			}
			if (updateNodeLock != null)
			{
				try
				{
					updateNodeLock.close();
				}
				catch (SQLException e)
				{
				}
			}
			if (selectExpiredNodeLock != null)
			{
				try
				{
					selectExpiredNodeLock.close();
				}
				catch (SQLException e)
				{
				}
			}
			if (deleteExpiredNodeLock != null)
			{
				try
				{
					deleteExpiredNodeLock.close();
				}
				catch (SQLException e)
				{
				}
			}
			if (connection != null)
			{
				try
				{
					connection.setAutoCommit(savedautocommit);
					connection.close();
				}
				catch (SQLException e)
				{
				}
				connection = null;
			}

		}

	}
	

	
	public boolean getLockTransaction(long nodeLifetime) {
		return getLockTransaction(nodeLifetime,false);
	}

	/**
	 * @return
	 * @throws HibernateException
	 */
	public boolean getLockTransaction(long nodeLifetime, boolean checklock)
	{
		String nodeID = getNodeID();
		Connection connection = null;
		boolean locked = false;
		boolean autoCommit = false;
		PreparedStatement selectLock = null;
		PreparedStatement updateLock = null;
		PreparedStatement insertLock = null;
		PreparedStatement countWork = null;

		ResultSet resultSet = null;
		Timestamp now = new Timestamp(System.currentTimeMillis());
		Timestamp expiryDate = new Timestamp(now.getTime()
				+ (10L * 60L * 1000L));

		try
		{

			// I need to go direct to JDBC since its just too awful to
			// try and do this in Hibernate.

			updateNodeLock(nodeLifetime);

			connection = dataSource.getConnection();
			autoCommit = connection.getAutoCommit();
			if (autoCommit)
			{
				connection.setAutoCommit(false);
			}

			selectLock = connection.prepareStatement(SELECT_LOCK_SQL);
			updateLock = connection.prepareStatement(UPDATE_LOCK_SQL);
			insertLock = connection.prepareStatement(INSERT_LOCK_SQL);
			countWork = connection.prepareStatement(COUNT_WORK_SQL);

			SearchWriterLock swl = null;
			selectLock.clearParameters();
			selectLock.setString(1, LOCKKEY);
			resultSet = selectLock.executeQuery();
			if (resultSet.next())
			{
				swl = new SearchWriterLockImpl();
				swl.setId(resultSet.getString(1));
				swl.setNodename(resultSet.getString(2));
				swl.setLockkey(resultSet.getString(3));
				swl.setExpires(resultSet.getTimestamp(4));
				log.debug("GOT Lock Record " + swl.getId() + "::"
						+ swl.getNodename() + "::" + swl.getExpires());

			}

			resultSet.close();
			resultSet = null;

			boolean takelock = false;
			if (swl == null)
			{
				log.debug("_-------------NO Lock Record");
				takelock = true;
			}
			else if ("none".equals(swl.getNodename()))
			{
				takelock = true;
				log.debug(nodeID + "_-------------no lock");
			}
			else if (nodeID.equals(swl.getNodename()))
			{
				takelock = true;
				log.debug(nodeID + "_------------matched threadid ");
			}
			else if (swl.getExpires() == null || swl.getExpires().before(now))
			{
				takelock = true;
				log.debug(nodeID + "_------------thread dead ");
			}

			if (takelock)
			{
				// any work ?
				countWork.clearParameters();
				countWork.setInt(1, SearchBuilderItem.STATE_PENDING.intValue());
				countWork
						.setInt(2, SearchBuilderItem.ACTION_UNKNOWN.intValue());
				resultSet = countWork.executeQuery();
				int nitems = 0;
				if (resultSet.next())
				{
					nitems = resultSet.getInt(1);
				}
				resultSet.close();
				resultSet = null;
				if (nitems > 0 || checklock )
				{
					try
					{
						if (swl == null)
						{
							insertLock.clearParameters();
							insertLock.setString(1, nodeID);
							insertLock.setString(2, nodeID);
							insertLock.setString(3, LOCKKEY);
							insertLock.setTimestamp(4, expiryDate);

							if (insertLock.executeUpdate() == 1)
							{
								log.debug("INSERT Lock Record " + nodeID + "::"
										+ nodeID + "::" + expiryDate);

								locked = true;
							}

						}
						else
						{
							updateLock.clearParameters();
							updateLock.setString(1, nodeID);
							updateLock.setTimestamp(2, expiryDate);
							updateLock.setString(3, swl.getId());
							updateLock.setString(4, swl.getNodename());
							updateLock.setString(5, swl.getLockkey());
							if (updateLock.executeUpdate() == 1)
							{
								log.debug("UPDATED Lock Record " + swl.getId()
										+ "::" + nodeID + "::" + expiryDate);
								locked = true;
							}

						}
					}
					catch (SQLException sqlex)
					{
						locked = false;
						log.debug("Failed to get lock, but this is Ok ", sqlex);
					}

				}

			}
			connection.commit();

		}
		catch (Exception ex)
		{
			if (connection != null)
			{
				try
				{
					connection.rollback();
				}
				catch (SQLException e)
				{
				}
			}
			log.error("Failed to get lock " + ex.getMessage());
			locked = false;
		}
		finally
		{
			if (resultSet != null)
			{
				try
				{
					resultSet.close();
				}
				catch (SQLException e)
				{
				}
			}
			if (selectLock != null)
			{
				try
				{
					selectLock.close();
				}
				catch (SQLException e)
				{
				}
			}
			if (updateLock != null)
			{
				try
				{
					updateLock.close();
				}
				catch (SQLException e)
				{
				}
			}
			if (insertLock != null)
			{
				try
				{
					insertLock.close();
				}
				catch (SQLException e)
				{
				}
			}
			if (countWork != null)
			{
				try
				{
					countWork.close();
				}
				catch (SQLException e)
				{
				}
			}

			if (connection != null)
			{
				try
				{
					connection.setAutoCommit(autoCommit);
				}
				catch (SQLException e)
				{
				}
				try
				{
					connection.close();
					log.debug("Connection Closed ");
				}
				catch (SQLException e)
				{
					log.error("Error Closing Connection ", e);
				}
				connection = null;
			}
		}
		return locked;

	}

	/**
	 * Count the number of pending documents waiting to be indexed on this
	 * cluster node. All nodes will potentially perform the index in a cluster,
	 * however only one must be doing the index, hence this method attampts to
	 * grab a lock on the writer. If sucessfull it then gets the real number of
	 * pending documents. There is a timeout, such that if the witer has not
	 * been seen for 10 minutes, it is assumed that something has gon wrong with
	 * it, and a new writer is elected on a first grab basis. Every time the
	 * elected writer comes back, it updates its record to say its still active.
	 * We could do some round robin timeout, or allow deployers to select a pool
	 * of index writers in Sakai properties. {@inheritDoc}
	 */

	private void clearLockTransaction()
	{
		String nodeID = getNodeID();

		Connection connection = null;
		PreparedStatement clearLock = null;
		try
		{
			connection = dataSource.getConnection();


			clearLock = connection.prepareStatement(CLEAR_LOCK_SQL);
			clearLock.clearParameters();
			clearLock.setString(1, NO_NODE);
			clearLock
					.setTimestamp(2, new Timestamp(System.currentTimeMillis()));
			clearLock.setString(3, nodeID);
			clearLock.setString(4, LOCKKEY);
			if (clearLock.executeUpdate() == 1)
			{
				log.debug("UNLOCK - OK    ?::" + nodeID + "::now");

			}
			else
			{
				log.debug("UNLOCK - Failed?::" + nodeID + "::now");
			}
			connection.commit();

		}
		catch (Exception ex)
		{
			try
			{
				connection.rollback();
			}
			catch (SQLException e)
			{
			}
			log.error("Failed to clear lock" + ex.getMessage());
		}
		finally
		{
			if ( clearLock != null ) {
				try {
					clearLock.close();
				} 
				catch (SQLException e) 
				{
					log.error("Error Closing Prepared Statement ",e);
				}
			}
			if (connection != null)
			{
				try
				{
					connection.close();
					log.debug("Connection Closed");
				}
				catch (SQLException e)
				{
					log.error("Error Closing Connection", e);
				}
			}
		}

	}

	public boolean isRunning()
	{
		if (org.sakaiproject.component.cover.ComponentManager.hasBeenClosed())
		{
			runThreads = false;
		}
		return runThreads;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.sakaiproject.search.component.service.impl.SearchIndexBuilderWorkerAPI#checkRunning()
	 */
	public void checkRunning()
	{
		if (!enabled) return;
		runThreads = true;
		synchronized (threadStartLock)
		{
			for (int i = 0; i < indexBuilderThread.length; i++)
			{
				if (indexBuilderThread[i] == null)
				{
					indexBuilderThread[i] = new Thread(this);
					indexBuilderThread[i].setName(String.valueOf(i) + "::"
							+ this.getClass().getName());
					indexBuilderThread[i].start();
				}
			}
		}
		synchronized (sem)
		{
			log.debug("_________NOTIFY");
			sem.notify();
			log.debug("_________NOTIFY COMPLETE");
		}

	}

	public void destroy()
	{
		if (!enabled) return;

		log.debug("Destroy SearchIndexBuilderWorker ");
		runThreads = false;

		synchronized (sem)
		{
			sem.notifyAll();
		}
	}

	/**
	 * @return Returns the sleepTime.
	 */
	public long getSleepTime()
	{
		return sleepTime;
	}

	/**
	 * @param sleepTime
	 *        The sleepTime to set.
	 */
	public void setSleepTime(long sleepTime)
	{
		this.sleepTime = sleepTime;
	}

	/**
	 * @return Returns the dataSource.
	 */
	public DataSource getDataSource()
	{
		return dataSource;
	}

	/**
	 * @param dataSource
	 *        The dataSource to set.
	 */
	public void setDataSource(DataSource dataSource)
	{
		this.dataSource = dataSource;
	}

	/**
	 * @return Returns the searchIndexBuilderWorkerDao.
	 */
	public SearchIndexBuilderWorkerDao getSearchIndexBuilderWorkerDao()
	{
		return searchIndexBuilderWorkerDao;
	}

	/**
	 * @param searchIndexBuilderWorkerDao
	 *        The searchIndexBuilderWorkerDao to set.
	 */
	public void setSearchIndexBuilderWorkerDao(
			SearchIndexBuilderWorkerDao searchIndexBuilderWorkerDao)
	{
		this.searchIndexBuilderWorkerDao = searchIndexBuilderWorkerDao;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.sakaiproject.search.component.service.impl.SearchIndexBuilderWorkerAPI#getCurrentLock()
	 */
	public SearchWriterLock getCurrentLock()
	{
		String nodeID = getNodeID();
		Connection connection = null;
		PreparedStatement selectLock = null;
		ResultSet resultSet = null;

		try
		{

			// I need to go direct to JDBC since its just too awful to
			// try and do this in Hibernate.

			connection = dataSource.getConnection();

			selectLock = connection.prepareStatement(SELECT_LOCK_SQL);

			SearchWriterLock swl = null;
			selectLock.clearParameters();
			selectLock.setString(1, LOCKKEY);
			resultSet = selectLock.executeQuery();
			if (resultSet.next())
			{
				swl = new SearchWriterLockImpl();
				swl.setId(resultSet.getString(1));
				swl.setNodename(resultSet.getString(2));
				swl.setLockkey(resultSet.getString(3));
				swl.setExpires(resultSet.getTimestamp(4));
				log.debug("GOT Lock Record " + swl.getId() + "::"
						+ swl.getNodename() + "::" + swl.getExpires());

			}

			resultSet.close();
			resultSet = null;
			if (swl == null)
			{
				swl = new SearchWriterLockImpl();
				swl.setNodename(NO_NODE);
				swl.setLockkey(LOCKKEY);
				swl.setExpires(new Timestamp(0));

			}
			return swl;

		}
		catch (Exception ex)
		{
			log.error("Failed to get lock " + ex.getMessage());
			SearchWriterLock swl = new SearchWriterLockImpl();
			swl.setNodename(NO_NODE);
			swl.setLockkey(LOCKKEY);
			swl.setExpires(new Timestamp(0));

			return swl;
		}
		finally
		{
			if (resultSet != null)
			{
				try
				{
					resultSet.close();
				}
				catch (SQLException e)
				{
				}
			}
			if (selectLock != null)
			{
				try
				{
					selectLock.close();
				}
				catch (SQLException e)
				{
				}
			}

			if (connection != null)
			{
				try
				{
					connection.close();
					log.debug("Connection Closed ");
				}
				catch (SQLException e)
				{
					log.error("Error Closing Connection ", e);
				}
				connection = null;
			}
		}

	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.sakaiproject.search.component.service.impl.SearchIndexBuilderWorkerAPI#getNodeStatus()
	 */
	public List getNodeStatus()
	{
		String nodeID = getNodeID();
		Connection connection = null;
		PreparedStatement selectLock = null;
		ResultSet resultSet = null;
		ArrayList locks = new ArrayList();

		try
		{

			// I need to go direct to JDBC since its just too awful to
			// try and do this in Hibernate.

			connection = dataSource.getConnection();

			selectLock = connection.prepareStatement(SELECT_NODE_LOCK_SQL);

			selectLock.clearParameters();
			resultSet = selectLock.executeQuery();
			while (resultSet.next())
			{
				SearchWriterLock swl = new SearchWriterLockImpl();
				swl.setId(resultSet.getString(1));
				swl.setNodename(resultSet.getString(2));
				swl.setLockkey(resultSet.getString(3));
				swl.setExpires(resultSet.getTimestamp(4));
				log.debug("GOT Lock Record " + swl.getId() + "::"
						+ swl.getNodename() + "::" + swl.getExpires());
				locks.add(swl);
			}

			resultSet.close();
			resultSet = null;
			return locks;

		}
		catch (Exception ex)
		{
			log.error("Failed to load nodes ", ex);
			return locks;
		}
		finally
		{
			if (resultSet != null)
			{
				try
				{
					resultSet.close();
				}
				catch (SQLException e)
				{
				}
			}
			if (selectLock != null)
			{
				try
				{
					selectLock.close();
				}
				catch (SQLException e)
				{
				}
			}

			if (connection != null)
			{
				try
				{
					connection.close();
					log.debug("Connection Closed ");
				}
				catch (SQLException e)
				{
					log.error("Error Closing Connection ", e);
				}
				connection = null;
			}
		}

	}

	public boolean removeWorkerLock()
	{
		Connection connection = null;
		PreparedStatement selectLock = null;
		PreparedStatement selectNodeLock = null;
		PreparedStatement clearLock = null;
		ResultSet resultSet = null;
		ArrayList locks = new ArrayList();

		try
		{

			// I need to go direct to JDBC since its just too awful to
			// try and do this in Hibernate.

			connection = dataSource.getConnection();

			selectNodeLock = connection.prepareStatement(SELECT_NODE_LOCK_SQL);
			selectLock = connection.prepareStatement(SELECT_LOCK_SQL);
			clearLock = connection.prepareStatement(CLEAR_LOCK_SQL);

			SearchWriterLock swl = null;
			selectLock.clearParameters();
			selectLock.setString(1, LOCKKEY);
			resultSet = selectLock.executeQuery();
			if (resultSet.next())
			{
				swl = new SearchWriterLockImpl();
				swl.setId(resultSet.getString(1));
				swl.setNodename(resultSet.getString(2));
				swl.setLockkey(resultSet.getString(3));
				swl.setExpires(resultSet.getTimestamp(4));
			}
			else
			{
				return true;
			}

			resultSet.close();
			resultSet = null;

			selectNodeLock.clearParameters();
			resultSet = selectLock.executeQuery();

			while (resultSet.next())
			{
				SearchWriterLock node = new SearchWriterLockImpl();
				node.setId(resultSet.getString(1));
				node.setNodename(resultSet.getString(2));
				node.setLockkey(resultSet.getString(3));
				node.setExpires(resultSet.getTimestamp(4));
				if (swl.getNodename().equals(node.getNodename()))
				{
					log.info("Cant remove Lock to node " + node.getNodename()
							+ " node exists ");
					return false;
				}
			}

			resultSet.close();
			resultSet = null;

			clearLock.clearParameters();
			clearLock.setString(1, NO_NODE);
			clearLock
					.setTimestamp(2, new Timestamp(System.currentTimeMillis()));
			clearLock.setString(3, swl.getNodename());
			clearLock.setString(4, LOCKKEY);
			if (clearLock.executeUpdate() == 1)
			{
				log.warn("NODE UNLOCKED BY USER " + swl.getNodename());

			}
			else
			{
				log.info("NODE NOT UNLOCKED BY USER " + swl.getNodename());
				return false;
			}

			return true;

		}
		catch (Exception ex)
		{
			log.error("Failed to unlock ", ex);
			return false;
		}
		finally
		{
			if (resultSet != null)
			{
				try
				{
					resultSet.close();
				}
				catch (SQLException e)
				{
				}
			}
			if (selectLock != null)
			{
				try
				{
					selectLock.close();
				}
				catch (SQLException e)
				{
				}
			}
			if (selectNodeLock != null)
			{
				try
				{
					selectNodeLock.close();
				}
				catch (SQLException e)
				{
				}
			}
			if (clearLock != null)
			{
				try
				{
					clearLock.close();
				}
				catch (SQLException e)
				{
				}
			}

			if (connection != null)
			{
				try
				{
					connection.close();
					log.debug("Connection Closed ");
				}
				catch (SQLException e)
				{
					log.error("Error Closing Connection ", e);
				}
				connection = null;
			}
		}
	}

	/**
	 * @return Returns the activity.
	 */
	public int getActivity()
	{
		return activity;
	}
	public long getLastEventTime() {
		return lastEvent ;
	}

	/**
	 * @param activity The activity to set.
	 */
	public void resetActivity()
	{
		this.activity = 0;
		
	}
	public void incrementActivity() {
		activity++;
		lastEvent = System.currentTimeMillis();
	}

	public void setLastIndex(long l)
	{
		this.lastIndex = l;
		
	}

	public void setStartDocIndex(long startDocIndex)
	{
		this.startDocIndex = startDocIndex;		
	}

	public void setNowIndexing(String reference)
	{
		this.lastIndexing = this.nowIndexing;
		this.nowIndexing = reference;
	}

	/**
	 * @return Returns the lastIndex.
	 */
	public long getLastIndex()
	{
		return lastIndex;
	}

	/**
	 * @return Returns the nowIndexing.
	 */
	public String getNowIndexing()
	{
		return nowIndexing;
	}

	/**
	 * @return Returns the startDocIndex.
	 */
	public long getStartDocIndex()
	{
		return startDocIndex;
	}

   	public String getLastDocument()
	{
		return lastIndexing;
	}

        public String getLastElapsed()
	{
		long l = lastIndex;
		long h = l/3600000L;
		l = l-(3600000L*h);
		long m = l/600000L;;
		l = l-(60000L*m);
		long s = l/1000;
		l = l-(1000L*s);
		return ""+h+"h"+m+"m"+s+"."+l+"s";
	}

        public String getCurrentDocument()
	{
		return nowIndexing;
	}

        public String getCurrentElapsed()
	{
		long l = System.currentTimeMillis()-startDocIndex;
		long h = l/3600000L;
		l = l-(3600000L*h);
		long m = l/60000L;
		l = l-(60000L*m);
		long s = l/1000L;
		l = l-(1000L*s);
		return ""+h+"h"+m+"m"+s+"."+l+"s";
	}


}
