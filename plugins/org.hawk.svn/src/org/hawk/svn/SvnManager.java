/*******************************************************************************
 * Copyright (c) 2011-2015 The University of York.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     Konstantinos Barmpis - initial API and implementation
 *     Ossmeter team (https://opensourceprojects.eu/p/ossmeter) - SVN delta computation algorithm
 *     Antonio Garcia-Dominguez - do not blacklist .zip files, allow any valid URL, minor clean up
 ******************************************************************************/
package org.hawk.svn;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.swing.JFrame;
import javax.swing.JOptionPane;

import org.hawk.core.IConsole;
import org.hawk.core.ICredentialsStore;
import org.hawk.core.ICredentialsStore.Credentials;
import org.hawk.core.IModelIndexer;
import org.hawk.core.IVcsManager;
import org.hawk.core.VcsChangeType;
import org.hawk.core.VcsCommit;
import org.hawk.core.VcsCommitItem;
import org.hawk.core.VcsRepositoryDelta;
import org.hawk.core.runtime.ModelIndexerImpl;
import org.hawk.core.security.FileBasedCredentialsStore;
import org.hawk.core.util.DefaultConsole;
import org.tmatesoft.svn.core.SVNLogEntry;
import org.tmatesoft.svn.core.SVNLogEntryPath;
import org.tmatesoft.svn.core.SVNProperties;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.core.wc.SVNRevision;

public class SvnManager implements IVcsManager {

	private IConsole console;
	private boolean isActive = false;

	private String repositoryURL;
	private String username;
	private String password;
	private IModelIndexer indexer;
	private boolean isFrozen = false;

	/*
	 * TODO we can't blacklist .zip as we need support for zipped Modelio
	 * projects - should we use a different extension (".mzip", perhaps?),or a
	 * whitelist (".modelio.zip"?)?
	 */
	private static final Set<String> EXTENSION_BLACKLIST = new HashSet<>(
			Arrays.asList(".png", ".jpg", ".bmp", ".jar", ".gz", ".tar"));

	public SvnManager() {
	}

	public static void main(String[] _a) throws Exception {
		System.err.println("testing");
		final JFrame parent = new JFrame();
		parent.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
		String pass = JOptionPane.showInputDialog(parent, "pw plz", "hi there");
		parent.dispose();

		System.err.println("testing2");
		SvnManager m = new SvnManager();
		System.err.println("testing3");

		final String vcsloc = "https://cssvn.york.ac.uk/repos/sosym/kostas/Hawk/org.hawk.emf/src/org/hawk/emf/model/examples/single/0";
		FileBasedCredentialsStore credStore = new FileBasedCredentialsStore(new File("security.xml"),
				"admin".toCharArray());
		credStore.put(vcsloc, new Credentials("kb634", pass));
		m.init(vcsloc, new ModelIndexerImpl(null, null, credStore, new DefaultConsole()));
		m.run();
		System.err.println("testing4");
		m.test();
		System.err.println("testing5-end");
	}

	private void test() {
		try {
			console = new DefaultConsole();
			System.err.println("------------");
			System.err.println(getDelta("0"));
			shutdown();
			System.err.println("------------");
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	@Override
	public void init(String vcsloc, IModelIndexer indexer) throws Exception {
		console = indexer.getConsole();
		this.repositoryURL = vcsloc;
		this.indexer = indexer;
	}

	@Override
	public void run() throws Exception {
		try {
			final ICredentialsStore credStore = indexer.getCredentialsStore();
			if (username != null) {
				// The credentials were provided by a previous setCredentials
				// call: retry the change to the credentials store.
				setCredentials(username, password, credStore);
			} else {
				final Credentials credentials = credStore.get(repositoryURL);
				if (credentials != null) {
					this.username = credentials.getUsername();
					this.password = credentials.getPassword();
				} else {
					/*
					 * If we use null for the default username/password, SVNKit
					 * will try to use the GNOME keyring in Linux, and that will
					 * lock up our Eclipse instance in some cases.
					 */
					console.printerrln("No username/password recorded for the repository " + repositoryURL);
					this.username = "";
					this.password = "";
				}
			}

			getFirstRevision();

			isActive = true;
		} catch (Exception e) {
			console.printerrln("exception in svnmanager run():");
			console.printerrln(e);
		}

	}

	protected static SVNRepository getSVNRepository(String url, String username, String password) {
		SvnUtil.setupLibrary();
		SVNRepository svnRepository = SvnUtil.connectToSVNInstance(url, username, password);
		return svnRepository;
	}

	@Override
	public VcsRepositoryDelta getDelta(String startRevision, String endRevision) throws Exception {
		SVNRepository svnRepository = getSVNRepository(repositoryURL, username, password);

		VcsRepositoryDelta delta = new VcsRepositoryDelta();
		delta.setManager(this);

		final String rootURL = svnRepository.getRepositoryRoot(false).toDecodedString();
		final String overLappedURL = makeRelative(rootURL, repositoryURL);

		if (!startRevision.equals(endRevision)) {
			Collection<?> c = svnRepository.log(new String[] { "" }, null, Long.valueOf(startRevision),
					Long.valueOf(endRevision), true, true);

			for (Object o : c) {
				SVNLogEntry svnLogEntry = (SVNLogEntry) o;
				VcsCommit commit = new VcsCommit();

				commit.setAuthor(svnLogEntry.getAuthor());
				commit.setMessage(svnLogEntry.getMessage());
				commit.setRevision(svnLogEntry.getRevision() + "");
				commit.setDelta(delta);
				commit.setJavaDate(svnLogEntry.getDate());
				delta.getCommits().add(commit);

				Map<String, SVNLogEntryPath> changedPaths = svnLogEntry.getChangedPaths();
				for (final String path : changedPaths.keySet()) {
					SVNLogEntryPath svnLogEntryPath = changedPaths.get(path);

					final int lastDotIndex = path.lastIndexOf(".");
					if (lastDotIndex <= 0) {
						// No extension or file starts by "." (hidden files in
						// Unix systems): skip
						continue;
					}
					final String ext = path.substring(lastDotIndex, path.length());
					if (EXTENSION_BLACKLIST.contains(ext)) {
						// Blacklisted extension: skip
						continue;
					}

					if (path.contains(overLappedURL)) {
						VcsCommitItem commitItem = new VcsCommitItem();
						commit.getItems().add(commitItem);
						commitItem.setCommit(commit);

						commitItem.setPath(path);

						if (svnLogEntryPath.getType() == 'A') {
							commitItem.setChangeType(VcsChangeType.ADDED);
						} else if (svnLogEntryPath.getType() == 'M') {
							commitItem.setChangeType(VcsChangeType.UPDATED);
						} else if (svnLogEntryPath.getType() == 'D') {
							commitItem.setChangeType(VcsChangeType.DELETED);
						} else if (svnLogEntryPath.getType() == 'R') {
							commitItem.setChangeType(VcsChangeType.REPLACED);
						} else {
							console.printerrln("Found unrecognised svn log entry type: " + svnLogEntryPath.getType());
							commitItem.setChangeType(VcsChangeType.UNKNOWN);
						}
					}
				}
			}
		}

		for (VcsCommitItem c : delta.getCompactedCommitItems()) {
			console.println(c.getPath());
		}

		return delta;
	}

	@Override
	public String getCurrentRevision() throws Exception {
		return getSVNRepository(repositoryURL, username, password).getLatestRevision() + "";
	}

	/**
	 * Cache the log?
	 */
	@Override
	public String getFirstRevision() throws Exception {
		SVNRepository svnRepository = getSVNRepository(repositoryURL, username, password);
		Collection<?> c = svnRepository.log(new String[] { "" }, null, 0, Long.valueOf(getCurrentRevision()), true,
				true);

		for (Object o : c) {
			return String.valueOf(((SVNLogEntry) o).getRevision());
		}
		return null;
	}

	private String makeRelative(String base, String extension) {
		StringBuilder result = new StringBuilder();
		List<String> baseSegments = Arrays.asList(base.split("/"));
		String[] extensionSegments = extension.split("/");
		for (String ext : extensionSegments) {
			if (!baseSegments.contains(ext)) {
				result.append(extension.substring(extension.indexOf(ext)));
				break;
			}
		}
		return result.toString();
	}

	@Override
	public File importFiles(String path, File temp) {
		SVNRepository svnRepository = getSVNRepository(repositoryURL, username, password);

		try {
			OutputStream o = new FileOutputStream(temp);
			svnRepository.getFile(path, SVNRevision.HEAD.getNumber(), new SVNProperties(), o);
			o.flush();
			o.close();
			return temp;
		} catch (Exception e) {
			console.printerrln(e);
			return null;
		}

	}

	@Override
	public boolean isActive() {
		return isActive;
	}

	@Override
	public void shutdown() {
		repositoryURL = null;
		console = null;
	}

	@Override
	public String getLocation() {
		return repositoryURL;
	}

	@Override
	public void setCredentials(String username, String password, ICredentialsStore credStore) {
		if (username != null && password != null && repositoryURL != null
				&& (!username.equals(this.username) || !password.equals(this.password))) {
			try {
				credStore.put(repositoryURL, new Credentials(username, password));
			} catch (Exception e) {
				console.printerrln("Could not save new username/password");
				console.printerrln(e);
			}
		}
		this.username = username;
		this.password = password;
	}

	@Override
	public String getType() {
		return getClass().getName();
	}

	@Override
	public String getHumanReadableName() {
		return "SVN Monitor";
	}

	@Override
	public Collection<VcsCommitItem> getDelta(String startRevision) throws Exception {
		if (Integer.parseInt(startRevision) < 0)
			return getDelta(getFirstRevision(), getCurrentRevision()).getCompactedCommitItems();
		else
			return getDelta(startRevision, getCurrentRevision()).getCompactedCommitItems();
	}

	@Override
	public boolean isAuthSupported() {
		return true;
	}

	@Override
	public boolean isPathLocationAccepted() {
		return false;
	}

	@Override
	public boolean isURLLocationAccepted() {
		return true;
	}

	@Override
	public Set<String> getPrefixesToBeStripped() {
		return Collections.emptySet();
	}

	@Override
	public String getUsername() {
		return username;
	}

	@Override
	public String getPassword() {
		return password;
	}

	@Override
	public boolean isFrozen() {
		return isFrozen;
	}

	@Override
	public void setFrozen(boolean f) {
		isFrozen = f;
	}
}
