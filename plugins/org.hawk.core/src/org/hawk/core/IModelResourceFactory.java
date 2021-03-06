/*******************************************************************************
 * Copyright (c) 2011-2017 The University of York, Aston University.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     Konstantinos Barmpis - initial API and implementation
 *     Antonio Garcia-Dominguez - add IFileImporter to parse
 ******************************************************************************/
package org.hawk.core;

import java.io.File;
import java.util.Collection;

import org.hawk.core.model.IHawkModelResource;

/**
 * Interface for a factory of {@link IHawkModelResource} instances from files.
 *
 * @author Kostas Barmpis, Antonio Garcia-Dominguez
 */
public interface IModelResourceFactory {

	/**
	 * Returns a string that uniquely identifies this factory.
	 */
	String getType();

	/**
	 * Returns a human friendly description of this factory.
	 */
	String getHumanReadableName();

	/**
	 * Parses a single model file.
	 *
	 * @param importer
	 *            Importer used to save the changed file from the
	 *            {@link IVcsManager}. This is normally only needed if parsing
	 *            the model file requires additional metadata in other files
	 *            that might not have changed (e.g. Modelio and the
	 *            <code>mmversion.dat</code> file).
	 * @param changedFile
	 *            Model file that underwent changes.
	 */
	IHawkModelResource parse(IFileImporter importer, File changedFile) throws Exception;

	/**
	 * Releases all resources associated to this factory.
	 */
	void shutdown();

	/**
	 * Quickly checks if this file can be parsed by this factory.
	 *
	 * @return <code>true</code> if it can, <code>false</code> otherwise.
	 */
	boolean canParse(File f);

	/**
	 * Reports which file extensions can be parsed by this factory.
	 *
	 * @return A collection of strings (e.g. ".xmi", ".model") with the
	 *         supported extensions, including the extension separator.
	 */
	Collection<String> getModelExtensions();

}
