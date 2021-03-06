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
package org.hawk.bpmn;

import java.util.HashMap;

import org.eclipse.emf.ecore.EAnnotation;
import org.hawk.core.model.IHawkAnnotation;



public class BPMNAnnotation implements IHawkAnnotation {

	EAnnotation ann;

	public BPMNAnnotation(EAnnotation a) {

		ann = a;

	}

	@Override
	public String getSource() {
		return ann.getSource();
	}

	@Override
	public HashMap<String, String> getDetails() {

		HashMap<String, String> m = new HashMap<String, String>();

		for (String s : ann.getDetails().keySet())
			m.put(s, ann.getDetails().get(s));

		return m;
	}

}
