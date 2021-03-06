/*******************************************************************************
 * Copyright (c) 2015 The University of York.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     Antonio Garcia-Dominguez - initial API and implementation
 *     Orjuwan Al-Wadeai - Changes to Integrate Modelio Metamodel 3.6
 ******************************************************************************/
package org.hawk.modelio.exml.metamodel;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.hawk.core.model.IHawkAttribute;
import org.hawk.core.model.IHawkClassifier;
import org.hawk.core.model.IHawkPackage;
import org.hawk.core.model.IHawkReference;
import org.hawk.core.model.IHawkStructuralFeature;

public class ModelioPackage extends AbstractModelioObject implements IHawkPackage {

	protected final ModelioMetaModelResource resource;
	protected final ModelioPackage parent;
	protected final MPackage rawPackage;

	protected List<ModelioPackage> packages;
	protected Map<String, ModelioClass> classes;

	public ModelioPackage(ModelioMetaModelResource mr, MPackage pkg) {
		this.resource = mr;
		this.parent = null;
		this.rawPackage = pkg;
	}

	public ModelioPackage(ModelioMetaModelResource mr, ModelioPackage parent, MPackage pkg) {
		this.resource = mr;
		this.parent = parent;
		this.rawPackage = pkg;
	}

	@Override
	public boolean isRoot() {
		return parent == null;
	}

	@Override
	public String getUri() {
		return getNsURI() + "#" + getUriFragment();
	}

	@Override
	public String getUriFragment() {
		return rawPackage.getId();
	}

	@Override
	public boolean isFragmentUnique() {
		return true;
	}

	@Override
	public IHawkClassifier getType() {
		return resource.getMetaType();
	}

	@Override
	public boolean isSet(IHawkStructuralFeature hsf) {
		switch (hsf.getName()) {
		case "name":
			return true;
		default:
			return false;
		}
	}

	@Override
	public Object get(IHawkAttribute attr) {
		switch (attr.getName()) {
		case "name": return rawPackage.getName();
		default: return null;
		}
	}

	@Override
	public Object get(IHawkReference ref, boolean b) {
		return null;
	}

	@Override
	public String getName() {
		return rawPackage.getName();
	}

	@Override
	public ModelioClass getClassifier(String name) {
		return getModelioClasses().get(name);
	}

	protected String getUnprefixedNsURI() {
		return parent == null ? rawPackage.getName() : parent.getUnprefixedNsURI() + "/" + rawPackage.getName();
	}

	@Override
	public String getNsURI() {
		// v2: new design based on hawkParent/hawkChildren
		// v3: fix issues with eOpposites and unwanted diffs between regenerations
		// 3.6 Modelio Metamodel : use pkgId (modelio://provider.name/version)
		return "modelio://" + rawPackage.getId();
	}

	@Override
	public Set<IHawkClassifier> getClasses() {
		return new HashSet<IHawkClassifier>(getModelioClasses().values());
	}

	@Override
	public ModelioMetaModelResource getResource() {
		return resource;
	}

	@Override
	public String getExml() {
		return null; // exml is not used for Modelio metamodels anymore
	}

	public String getXml() {
		return rawPackage.getXml();
	}

	public void setXml(String xmlString) {
		rawPackage.setXml(xmlString);
	}
	public Collection<ModelioPackage> getPackages() {
		if (packages == null) {
			packages = new ArrayList<>();
			for (MPackage mpkg : rawPackage.getMPackages()) {
				packages.add(new ModelioPackage(resource, this, mpkg));
			}
		}
		return packages;
	}

	public Map<String, ModelioClass> getModelioClasses() {
		if (classes == null) {
			classes = new HashMap<>();
			for (MClass mc : rawPackage.getMClass()) {
				classes.put(mc.getName(), new ModelioClass(this, mc));
			}
		}
		return classes;
	}

	@Override
	public String toString() {
		return "ModelioPackage [uri=" + getNsURI() + "]";
	}

	public MPackage getRawPackage() {
		return rawPackage;
	}
	
	public String getVersion() {
		return rawPackage.getVersion();
	}
}
