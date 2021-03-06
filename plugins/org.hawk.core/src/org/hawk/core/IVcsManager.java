/*******************************************************************************
 * Copyright (c) 2011-2015 The University of York.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 * 	   Nikolas Matragkas, James Williams, Dimitris Kolovos - initial API and implementation
 *     Konstantinos Barmpis - adaption for use in Hawk
 ******************************************************************************/
package org.hawk.core;

import java.io.File;
import java.util.Collection;
import java.util.Set;

public interface IVcsManager {

	String getCurrentRevision() throws Exception;

	String getFirstRevision() throws Exception;

	Collection<VcsCommitItem> getDelta(String startRevision) throws Exception;

	VcsRepositoryDelta getDelta(String startRevision, String endRevision)
			throws Exception;

	/**
	 * Places the contents of the resource located at <code>path</code> in a
	 * local file. Hawk will provide a suggested temporary file location through
	 * <code>optionalTemp</code>, but the implementation may use another one
	 * (e.g. the file might already be available on disk somewhere else). The
	 * implementation must return the file that should be read in the end.
	 * Implementations should be careful to preserve relative paths between the
	 * files in the same repository when implementing this method.
	 *
	 * Returns <code>null</code> if the file could not be found.
	 */
	File importFiles(String path, File optionalTemp);

	// kostas
	boolean isActive();

	// kostas
	void init(String vcsloc, IModelIndexer hawk) throws Exception;

	// kostas
	void run() throws Exception;

	// kostas
	void shutdown();

	// kostas
	/**
	 * 
	 * @return returns the canonical and normalised representation of the
	 *         location of this VCSManager, always including a trailing slash
	 */
	String getLocation();

	/**
	 * Returns the current username (if any is used), or <code>null</code>.
	 */
	String getUsername();

	/**
	 * Returns the current password (if any is used), or <code>null</code>.
	 */
	String getPassword();

	/**
	 * Changes the username and password in one go. Both must be passed at the
	 * same time to be able to support remote instances.
	 */
	void setCredentials(String username, String password,
			ICredentialsStore credStore);

	// kostas
	String getType();

	// kostas
	String getHumanReadableName();

	/**
	 * Returns <code>true</code> if the implementation supports authentication.
	 */
	boolean isAuthSupported();

	/**
	 * Returns <code>true</code> if the implementation accepts filesystem paths
	 * as locations. It should be OK for an implementation to return
	 * <code>true</code> for this and {@link #isURLLocationAccepted()} at the
	 * same time.
	 */
	boolean isPathLocationAccepted();

	/**
	 * Returns <code>true</code> if the implementation accepts URL-based paths
	 * as locations. It should be OK for an implementation to return
	 * <code>true</code> for this and {@link #isPathLocationAccepted()} at the
	 * same time.
	 */
	boolean isURLLocationAccepted();

	/**
	 * Returns a set of prefixes that should be stripped from any inter-resource
	 * references in the files contained within that repository in order to turn
	 * them into relative paths within the repository.
	 */
	Set<String> getPrefixesToBeStripped();

	// kostas
	boolean isFrozen();

	// kostas
	void setFrozen(boolean f);
}
