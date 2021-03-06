/*******************************************************************************
 * Copyright (c) 2011-2015 The University of York.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     Konstantinos Barmpis - initial API and implementation
 ******************************************************************************/
package org.hawk.core.model;

import java.util.Set;

public interface IHawkPackage extends IHawkObject {

	String getName();

	IHawkClass getClassifier(String string) throws Exception;

	String getNsURI();

	Set<IHawkClassifier> getClasses();

	IHawkMetaModelResource getResource();

	//HawkMetaModelResource getResource();

}
