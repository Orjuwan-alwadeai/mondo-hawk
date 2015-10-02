/*******************************************************************************
 * Copyright (c) 2011-2015 The University of York.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     Konstantinos Barmpis - initial API and implementation
 *     Antonio Garcia-Dominguez - use explicit HManager instances, add support for
 *                                remote locations
 ******************************************************************************/
package org.hawk.osgiserver;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;

import org.eclipse.core.runtime.IConfigurationElement;
import org.eclipse.core.runtime.preferences.IEclipsePreferences;
import org.hawk.core.IAbstractConsole;
import org.hawk.core.IHawk;
import org.hawk.core.IHawkFactory;
import org.hawk.core.IMetaModelResourceFactory;
import org.hawk.core.IMetaModelUpdater;
import org.hawk.core.IModelIndexer.ShutdownRequestType;
import org.hawk.core.IModelResourceFactory;
import org.hawk.core.IModelUpdater;
import org.hawk.core.IVcsManager;
import org.hawk.core.graph.IGraphChangeListener;
import org.hawk.core.graph.IGraphDatabase;
import org.hawk.core.query.IQueryEngine;
import org.hawk.core.util.HawkConfig;
import org.hawk.core.util.HawkProperties;
import org.hawk.core.util.HawksConfig;
import org.osgi.service.prefs.BackingStoreException;

import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.io.xml.DomDriver;

public class HModel {

	private static IAbstractConsole CONSOLE = new SLF4JConsole();

	public static IAbstractConsole getConsole() {
		if (CONSOLE == null)
			CONSOLE = new SLF4JConsole();
		return CONSOLE;
	}

	public static void setConsole(IAbstractConsole c) {
		CONSOLE = c;
	}

	/**
	 * Creates a new Hawk instance in a local folder, and saves its metadata
	 * into the {@link HManager}.
	 */
	public static HModel create(IHawkFactory hawkFactory, String name,
			File storageFolder, String location, String dbType,
			List<String> plugins, HManager manager, char[] apw)
			throws Exception {
		HModel hm = new HModel(manager, hawkFactory, name, storageFolder,
				location);
		if (dbType != null) {
			hm.hawk.setDbtype(dbType);
		}

		// TODO use plugins list to enable only these plugins
		IGraphDatabase db = null;
		final IAbstractConsole console = getConsole();
		try {
			// create the indexer with relevant database
			console.println("Creating Hawk indexer...");

			if (hawkFactory.instancesCreateGraph()) {
				console.println("Setting up hawk's back-end store:");
				db = manager.createGraph(hm.hawk);
				db.run(storageFolder, console);
				hm.hawk.getModelIndexer().setDB(db, true);
			}

			// hard coded metamodel updater?
			IMetaModelUpdater metaModelUpdater = manager.getMetaModelUpdater();
			console.println("Setting up hawk's metamodel updater:\n"
					+ metaModelUpdater.getName());
			hm.hawk.getModelIndexer().setMetaModelUpdater(metaModelUpdater);
			hm.hawk.getModelIndexer().setAdminPassword(apw);
			hm.hawk.getModelIndexer().init();

			manager.addHawk(hm);
			manager.saveHawkToMetadata(hm);
			console.println("Created Hawk indexer!");
			return hm;
		} catch (Exception e) {
			console.printerrln("Adding of indexer aborted, please try again.\n"
					+ "Shutting down and removing back-end (if it was created)");
			console.printerrln(e);

			try {
				if (db != null) {
					db.delete();
				}
			} catch (Exception e2) {
				throw e2;
			}

			console.printerrln("aborting finished.");
			throw e;
		}
	}

	/**
	 * Loads a previously existing Hawk instance from its {@link HawkConfig}.
	 */
	public static HModel load(HawkConfig config, HManager manager)
			throws Exception {

		try {

			final IHawkFactory hawkFactory = manager.createHawkFactory(config
					.getHawkFactory());

			HModel hm = new HModel(manager, hawkFactory, config.getName(),
					new File(config.getStorageFolder()), config.getLocation());

			// hard coded metamodel updater?
			IMetaModelUpdater metaModelUpdater = manager.getMetaModelUpdater();
			hm.hawk.getModelIndexer().setMetaModelUpdater(metaModelUpdater);

			return hm;
		} catch (Throwable e) {
			System.err
					.println("Exception in trying to add create Indexer from folder:");
			System.err.println(e.getMessage());
			e.printStackTrace();
			System.err.println("Adding of indexer aborted, please try again");
			return null;
		}

	}

	private List<String> allowedPlugins = new ArrayList<String>();
	private final IHawk hawk;
	private final IHawkFactory hawkFactory;
	private final HManager manager;
	private final String hawkLocation;

	/**
	 * Constructor for loading existing local Hawk instances and
	 * creating/loading custom {@link IHawk} implementations.
	 */
	public HModel(HManager manager, IHawkFactory hawkFactory, String name,
			File storageFolder, String location) throws Exception {
		this.hawkFactory = hawkFactory;
		this.hawk = hawkFactory.create(name, storageFolder, location,
				getConsole());
		this.manager = manager;
		this.hawkLocation = location;

		if (hawkFactory.instancesAreExtensible()) {
			final IAbstractConsole console = getConsole();
			// set up plugins
			// first get all of type (static callto HawkOSGIConfigManager)
			// check each one has the an ID that was selected
			// create VCS
			// call m.add
			console.println("adding metamodel resource factories:");
			for (IConfigurationElement mmparse : manager.getMmps()) {
				IMetaModelResourceFactory f = (IMetaModelResourceFactory) mmparse
						.createExecutableExtension("MetaModelParser");
				this.hawk.getModelIndexer().addMetaModelResourceFactory(f);
				console.println(f.getHumanReadableName());
			}
			console.println("adding model resource factories:");
			for (IConfigurationElement mparse : manager.getMps()) {
				IModelResourceFactory f = (IModelResourceFactory) mparse
						.createExecutableExtension("ModelParser");
				this.hawk.getModelIndexer().addModelResourceFactory(f);
				console.println(f.getHumanReadableName());
			}
			console.println("adding query engines:");
			for (IConfigurationElement ql : manager.getLanguages()) {
				IQueryEngine q = (IQueryEngine) ql
						.createExecutableExtension("query_language");
				this.hawk.getModelIndexer().addQueryEngine(q);
				console.println(q.getType());
			}
			console.println("adding model updaters:");
			for (IConfigurationElement updater : manager.getUps()) {
				IModelUpdater u = (IModelUpdater) updater
						.createExecutableExtension("ModelUpdater");
				this.hawk.getModelIndexer().addModelUpdater(u);
				console.println(u.getName());
			}
			console.println("adding graph change listeners:");
			for (IConfigurationElement listener : manager
					.getGraphChangeListeners()) {
				IGraphChangeListener l = (IGraphChangeListener) listener
						.createExecutableExtension("class");
				l.setModelIndexer(this.hawk.getModelIndexer());
				this.hawk.getModelIndexer().addGraphChangeListener(l);
				console.println(l.getName());
			}
		}
	}

	public void addDerivedAttribute(String metamodeluri, String typename,
			String attributename, String attributetype, Boolean isMany,
			Boolean isOrdered, Boolean isUnique, String derivationlanguage,
			String derivationlogic) throws Exception {
		hawk.getModelIndexer().addDerivedAttribute(metamodeluri, typename,
				attributename, attributetype, isMany, isOrdered, isUnique,
				derivationlanguage, derivationlogic);
	}

	private void loadEncryptedVCS(String loc, String type, String user,
			String pass) throws Exception {
		if (!this.getLocations().contains(loc)) {
			String decryptedUser = user;
			if (decryptedUser != null && !"".equals(decryptedUser)) {
				decryptedUser = hawk.getModelIndexer().decrypt(user);
			}

			String decryptedPass = pass;
			if (decryptedPass != null && !"".equals(decryptedPass)) {
				decryptedPass = hawk.getModelIndexer().decrypt(pass);
			}

			IVcsManager mo = manager.createVCSManager(type);
			mo.run(loc, decryptedUser, decryptedPass, getConsole());
			hawk.getModelIndexer().addVCSManager(mo, false);
		}
	}

	public void addIndexedAttribute(String metamodeluri, String typename,
			String attributename) throws Exception {
		hawk.getModelIndexer().addIndexedAttribute(metamodeluri, typename,
				attributename);
	}

	public void addVCS(String loc, String type, String user, String pass) {
		try {
			if (!this.getLocations().contains(loc)) {
				IVcsManager mo = manager.createVCSManager(type);
				mo.run(loc, user, pass, getConsole());
				hawk.getModelIndexer().addVCSManager(mo, true);
			}
		} catch (Exception e) {
			getConsole().printerrln(e);
		}
	}

	/**
	 * Registers a new graph change listener into the model indexer, if it
	 * wasn't already registered. Otherwise, it does nothing.
	 */
	public boolean addGraphChangeListener(IGraphChangeListener changeListener) {
		return hawk.getModelIndexer().addGraphChangeListener(changeListener);
	}

	/**
	 * Removes a new graph change listener from the model indexer, if it was
	 * already registered. Otherwise, it does nothing.
	 */
	public boolean removeGraphChangeListener(IGraphChangeListener changeListener) {
		return hawk.getModelIndexer().removeGraphChangeListener(changeListener);
	}

	/**
	 * Performs a context-aware query and returns its result. The result must be
	 * a Double, a String, an Integer, a ModelElement, the null reference or an
	 * Iterable of these things.
	 * 
	 * @throws NoSuchElementException
	 *             Unknown query language.
	 */
	public Object contextFullQuery(File query, String ql,
			Map<String, String> context) throws Exception {
		IQueryEngine q = hawk.getModelIndexer().getKnownQueryLanguages()
				.get(ql);
		if (q == null) {
			throw new NoSuchElementException();
		}

		return q.contextfullQuery(hawk.getModelIndexer().getGraph(), query,
				context);
	}

	/**
	 * Performs a context-aware query and returns its result. For the result
	 * types, see {@link #contextFullQuery(File, String, Map)}.
	 * 
	 * @throws NoSuchElementException
	 *             Unknown query language.
	 */
	public Object contextFullQuery(String query, String ql,
			Map<String, String> context) throws Exception {
		IQueryEngine q = hawk.getModelIndexer().getKnownQueryLanguages()
				.get(ql);
		if (q == null) {
			throw new NoSuchElementException();
		}

		return q.contextfullQuery(hawk.getModelIndexer().getGraph(), query,
				context);
	}

	public void delete() throws BackingStoreException {
		removeHawkFromMetadata(getHawkConfig());

		File f = hawk.getModelIndexer().getParentFolder();
		while (this.isRunning()) {
			try {
				// XXX removing an HModel does not delete the storage and does
				// not stop remote instances.
				hawk.getModelIndexer().shutdown(ShutdownRequestType.ONLY_LOCAL);
			} catch (Exception e) {
				e.printStackTrace();
			}
		}

		if (f.exists()) {
			System.err
					.println("hawk removed from ui but persistence remains at: "
							+ f);
		}
	}

	/**
	 * Returns a {@link HawkConfig} from which this instance can be reloaded.
	 */
	public HawkConfig getHawkConfig() {
		return new HawkConfig(getName(), getFolder(), hawkLocation, hawkFactory
				.getClass().getName());
	}

	public boolean exists() {
		return hawk != null && hawk.exists();
	}

	public List<String> getAllowedPlugins() {
		return allowedPlugins;
	}

	public Collection<String> getDerivedAttributes() {
		return hawk.getModelIndexer().getDerivedAttributes();
	}

	public String getFolder() {
		return hawk.getModelIndexer().getParentFolder().toString();
	}

	public IGraphDatabase getGraph() {
		return hawk.getModelIndexer().getGraph();
	}

	public Collection<String> getIndexedAttributes() {
		return hawk.getModelIndexer().getIndexedAttributes();
	}

	public Collection<String> getIndexes() {
		return hawk.getModelIndexer().getIndexes();
	}

	public Set<String> getKnownQueryLanguages() {
		return hawk.getModelIndexer().getKnownQueryLanguages().keySet();
	}

	public Collection<String> getLocations() {
		List<String> locations = new ArrayList<String>();
		for (IVcsManager o : getRunningVCSManagers()) {
			locations.add(o.getLocation());
		}
		return locations;
	}

	public Collection<IVcsManager> getRunningVCSManagers() {
		return hawk.getModelIndexer().getRunningVCSManagers();
	}

	public String getName() {
		return hawk.getModelIndexer().getName();
	}

	public ArrayList<String> getRegisteredMetamodels() {
		return new ArrayList<String>(hawk.getModelIndexer().getKnownMMUris());
	}

	public List<IVcsManager> getVCSInstances() {
		return manager.getVCSInstances();
	}

	public boolean isRunning() {
		return hawk.getModelIndexer().isRunning();
	}

	/**
	 * For the result types, see {@link #contextFullQuery(File, String, Map)}.
	 */
	public Object query(File query, String ql) throws Exception {
		IQueryEngine q = hawk.getModelIndexer().getKnownQueryLanguages()
				.get(ql);

		return q.contextlessQuery(hawk.getModelIndexer().getGraph(), query);
	}

	/**
	 * For the result types, see {@link #contextFullQuery(File, String, Map)}.
	 */
	public Object query(String query, String ql) throws Exception {
		IQueryEngine q = hawk.getModelIndexer().getKnownQueryLanguages()
				.get(ql);
		return q.contextlessQuery(hawk.getModelIndexer().getGraph(), query);
	}

	public boolean registerMeta(File... f) {
		try {
			hawk.getModelIndexer().registerMetamodel(f);
		} catch (Exception e) {
			return false;
		}
		return true;
	}

	public void removeHawkFromMetadata(HawkConfig config)
			throws BackingStoreException {
		IEclipsePreferences preferences = HManager.getPreferences();

		String xml = preferences.get("config", null);

		if (xml != null) {
			XStream stream = new XStream(new DomDriver());
			stream.processAnnotations(HawksConfig.class);
			stream.processAnnotations(HawkConfig.class);
			stream.setClassLoader(HawksConfig.class.getClassLoader());
			HawksConfig hc = (HawksConfig) stream.fromXML(xml);
			hc.removeLoc(config);
			xml = stream.toXML(hc);
			preferences.put("config", xml);
			preferences.flush();
		} else {
			getConsole()
					.printerrln(
							"removeHawkFromMetadata tried to load preferences but it could not.");
		}
	}

	public boolean start(HManager manager, char[] apw) {
		try {
			hawk.getModelIndexer().setAdminPassword(apw);
			loadIndexerMetadata();

			if (hawkFactory.instancesCreateGraph()) {
				// create the indexer with relevant database
				IGraphDatabase db = manager.createGraph(hawk);
				db.run(new File(this.getFolder()), getConsole());
				hawk.getModelIndexer().setDB(db, false);
			}

			hawk.getModelIndexer().init();
		} catch (Exception e) {
			getConsole().printerrln(e);
		}

		return hawk.getModelIndexer().isRunning();
	}

	public void stop(ShutdownRequestType requestType) {
		try {
			hawk.getModelIndexer().shutdown(requestType);
		} catch (Exception e) {
			getConsole().printerrln(e);
		}
	}

	@Override
	public String toString() {
		String ret = "";
		try {
			ret = getName()
					+ (this.isRunning() ? " (running) " : " (stopped) ") + " ["
					+ this.getFolder() + "] ";
		} catch (Exception e) {
			e.printStackTrace();
		}
		return ret;
	}

	public List<String> validateExpression(String derivationlanguage,
			String derivationlogic) {
		return hawk.getModelIndexer().validateExpression(derivationlanguage,
				derivationlogic);
	}

	private void loadIndexerMetadata() throws Exception {
		XStream stream = new XStream(new DomDriver());
		stream.processAnnotations(HawkProperties.class);
		stream.setClassLoader(HawkProperties.class.getClassLoader());
		String path = hawk.getModelIndexer().getParentFolder() + File.separator
				+ "properties.xml";

		HawkProperties hp = (HawkProperties) stream.fromXML(new File(path));
		hawk.setDbtype(hp.getDbType());
		for (String[] s : hp.getMonitoredVCS()) {
			loadEncryptedVCS(s[0], s[1], s[2], s[3]);
		}
	}

	public void removeDerivedAttribute(String metamodelUri, String typeName,
			String attributeName) {
		// TODO Auto-generated method stub

	}

	public void removeIndexedAttribute(String metamodelUri, String typename,
			String attributename) {
		// TODO Auto-generated method stub

	}

	public void removeRepository(String uri) throws Exception {
		// TODO Auto-generated method stub

	}

	/**
	 * Should throw an {@link IllegalArgumentException} if the configuration for
	 * the polling is not valid (base or max <= 0 or base > max).
	 */
	public void configurePolling(int base, int max) {
		// TODO Auto-generated method stub
	}

}
