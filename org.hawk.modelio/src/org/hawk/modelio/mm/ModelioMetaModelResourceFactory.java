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
package org.hawk.modelio.mm;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.EPackage.Registry;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
import org.eclipse.emf.ecore.xmi.impl.EcoreResourceFactoryImpl;
import org.eclipse.emf.ecore.xmi.impl.XMIResourceFactoryImpl;
import org.hawk.core.IMetaModelResourceFactory;
import org.hawk.core.model.IHawkMetaModelResource;
import org.hawk.core.model.IHawkPackage;
import org.hawk.modelio.ModelioPackage;

public class ModelioMetaModelResourceFactory implements IMetaModelResourceFactory {

	private static final String TYPE = "org.hawk.modelio.mm.ModelioMetaModelResourceFactory";

	ResourceSet resourceSet = null;

	public ModelioMetaModelResourceFactory() {

		if (resourceSet == null) {
			resourceSet = new ResourceSetImpl();
			resourceSet.getResourceFactoryRegistry().getExtensionToFactoryMap()
					.put("ecore", new EcoreResourceFactoryImpl());
			resourceSet.getResourceFactoryRegistry().getExtensionToFactoryMap()
					.put("*", new XMIResourceFactoryImpl());
		}
	}

	@Override
	public String getType() {
		return TYPE;
	}

	@Override
	public void shutdown() {
		resourceSet = null;
	}

	@Override
	public IHawkMetaModelResource parse(File f) throws Exception {
		ModelioMetaModelResource ret;

		Resource r = resourceSet.createResource(URI.createFileURI(f
				.getAbsolutePath()));
		r.load(null);

		RegisterMeta.registerPackages(r);
		ret = new ModelioMetaModelResource(r, this);
		return ret;
	}

	@Override
	public Set<String> getMetaModelExtensions() {
		return Collections.emptySet();
	}

	@Override
	public IHawkMetaModelResource createMetamodelWithSinglePackage(String s,
			IHawkPackage p) {

		Resource r = resourceSet.createResource(URI.createURI(s));

		r.getContents().add(((ModelioPackage) p).getEObject());

		return new ModelioMetaModelResource(r, this);

	}

	@Override
	public IHawkMetaModelResource parseFromString(String name, String contents) throws Exception {

		if (name != null && contents != null) {
			Resource r = resourceSet.createResource(URI.createURI(name));
			InputStream input = new ByteArrayInputStream(contents.getBytes("UTF-8"));
			r.load(input, null);

			RegisterMeta.registerPackages(r);
			return new ModelioMetaModelResource(r, this);
		} else
			return null;
	}

	@Override
	public void removeMetamodel(String property) {
		boolean found = false;
		Resource rem = null;

		for (Resource r : resourceSet.getResources())
			if (r.getURI().toString().contains(property)) {
				rem = r;
				found = true;
				break;
			}

		if (found)
			try {
				rem.delete(null);
				//EPackage.Registry.INSTANCE.remove(property);
			} catch (Exception e) {
				e.printStackTrace();
			}

		System.err.println(found ? "removed: " + property : property
				+ " not present in this EMF parser");

	}

	@Override
	public boolean canParse(File f) {
		return false;
	}

	@Override
	public Set<IHawkMetaModelResource> getStaticMetamodels() {
		Set<IHawkMetaModelResource> set = new HashSet<>();

		Registry globalRegistry = EPackage.Registry.INSTANCE;
		HashSet<String> keys = new HashSet<>();
		keys.addAll(globalRegistry.keySet());

		File f = new File("../org.hawk.modelio/models/UML.ecore");
		File fr = new File("../org.hawk.modelio/models/Types.ecore");

		Resource r = resourceSet.createResource(URI.createFileURI(f.getAbsolutePath()));
		Resource rr = resourceSet.createResource(URI.createFileURI(fr.getAbsolutePath()));
		try {
			r.load(null);
			rr.load(null);
		} catch (IOException e) {
			System.err.print("WARNING: static metamodel of UML was not found, no static metamodels inserted for this plugin, please insert the relevant metamodels manually");
		}

		RegisterMeta.registerPackages(r);
		RegisterMeta.registerPackages(rr);

		ModelioMetaModelResource ret = new ModelioMetaModelResource(r, this);
		ModelioMetaModelResource ret2 = new ModelioMetaModelResource(rr, this);

		set.add(ret);
		set.add(ret2);

		return set;
	}

	@Override
	public String getHumanReadableName() {
		return "Modelio Meta model parser for Hawk";
	}
}
