/*******************************************************************************
 * Copyright (c) 2015 The University of York.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     Antonio Garcia-Dominguez - initial API and implementation
 ******************************************************************************/
package org.hawk.modelio.exml.ecoregen;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EAttribute;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EDataType;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.EReference;
import org.eclipse.emf.ecore.ETypedElement;
import org.eclipse.emf.ecore.EcoreFactory;
import org.eclipse.emf.ecore.EcorePackage;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.xmi.impl.XMIResourceImpl;
import org.hawk.core.model.IHawkAttribute;
import org.hawk.core.model.IHawkClass;
import org.hawk.core.model.IHawkClassifier;
import org.hawk.core.model.IHawkReference;
import org.hawk.modelio.exml.metamodel.ModelioAttribute;
import org.hawk.modelio.exml.metamodel.ModelioClass;
import org.hawk.modelio.exml.metamodel.ModelioMetaModelResource;
import org.hawk.modelio.exml.metamodel.ModelioPackage;
import org.modelio.metamodel.MMetamodel;
import org.modelio.metamodel.MPackage;

/**
 * Generates an <code>.ecore</code> file that mimics the Modelio metamodel, in
 * order to make it possible to load Modelio instances through
 * <code>.hawkmodel</code> and <code>.localhawkmodel</code> files.
 *
 * Also generates the appropriate plugin.xml entries to have these metamodels
 * included automatically in the default EMF EPackage registry.
 */
public class EcoreGenerator {

	private final class Tuple<L, R> {
		private final L left;
		private final R right;

		private Tuple(L key, R value) {
			this.left = key;
			this.right = value;
		}

		public L getLeft() {
			return left;
		}

		public R getRight() {
			return right;
		}
	}

	private final EcoreFactory factory;
	private final List<EPackage> packages = new ArrayList<>();
	private final Map<String, Tuple<EClass, ModelioClass>> mClasses = new HashMap<>();
	private int nPackage = 0;

	public static void main(String[] args) {
		try {
			final EcoreGenerator generator = new EcoreGenerator();
			final File f = new File("model/modelio-v2.ecore");
			final Resource r = generator.generate(f);
			System.out.println("plugin.xml fragment:\n\n" + generator.generatePluginXml(r));
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private String generatePluginXml(Resource r) {
		final StringBuffer sbuf = new StringBuffer();
		for (EPackage pkg : packages) {
			/*
			 * <resource location="model/modelio.ecore#/"
			 * uri="org.hawk.modelio.exml.ecoregen.resource3">
			 */
			sbuf.append("    <resource uri=\"");
			sbuf.append(pkg.getNsURI());
			sbuf.append("\" location=\"");
			sbuf.append(r.getURI().toString() + "#" + r.getURIFragment(pkg));
			sbuf.append("\"/>\n");
		}
		return sbuf.toString();
	}

	public EcoreGenerator() {
		factory = EcorePackage.eINSTANCE.getEcoreFactory();
	}

	public Resource generate(File file) throws Exception {
		Resource r = new XMIResourceImpl(URI.createFileURI(file.getPath()));

		final ModelioMetaModelResource mr = new ModelioMetaModelResource(null);
		final MMetamodel metamodel = new MMetamodel();

		// Do a first pass to create the structure
		for (MPackage mp : metamodel.getMPackages()) {
			final ModelioPackage wrapped = new ModelioPackage(mr, mp);
			addPackageContents(r, wrapped);
		}

		// On the second pass, create features and references
		for (Tuple<EClass, ModelioClass> entry : mClasses.values()) {
			final EcorePackage ecorePkg = EcorePackage.eINSTANCE;
			final EClass ec = entry.getLeft();
			final ModelioClass mc = entry.getRight();

			for (IHawkClass superMClass : mc.getSuperTypes()) {
				EClass eSuper = mClasses.get(superMClass.getName()).getLeft();
				ec.getESuperTypes().add(eSuper);
			}

			for (IHawkAttribute mattr : mc.getOwnAttributesMap().values()) {
				EDataType edt = ecorePkg.getEString();
				switch (((ModelioAttribute)mattr).getRawAttribute().getMDataType().getJavaEquivalent()) {
				case "Short":
					edt = ecorePkg.getEShort();
					break;
				case "Long":
					edt = ecorePkg.getELong();
					break;
				case "Integer":
					edt = ecorePkg.getEInt();
					break;
				case "Float":
					edt = ecorePkg.getEFloat();
					break;
				case "Double":
					edt = ecorePkg.getEDouble();
					break;
				case "Character":
					edt = ecorePkg.getEChar();
					break;
				case "Byte":
					edt = ecorePkg.getEByte();
					break;
				case "Boolean":
					edt = ecorePkg.getEBoolean();
					break;
				}

				final EAttribute eattr = factory.createEAttribute();
				eattr.setName(mattr.getName());
				eattr.setEType(edt);
				eattr.setOrdered(mattr.isOrdered());
				eattr.setUnique(mattr.isUnique());
				eattr.setUpperBound(mattr.isMany() ? EAttribute.UNBOUNDED_MULTIPLICITY : 1);
				ec.getEStructuralFeatures().add(eattr);
			}

			for (IHawkReference mdep : mc.getOwnReferencesMap().values()) {
				final EClass targetEClass = mClasses.get(mdep.getType().getName()).getLeft();

				final EReference eref = factory.createEReference();
				eref.setName(mdep.getName());
				eref.setOrdered(mdep.isOrdered());
				eref.setUnique(mdep.isUnique());
				eref.setUpperBound(mdep.isMany() ? EReference.UNBOUNDED_MULTIPLICITY : 1);
				eref.setEType(targetEClass);
				eref.setContainment(mdep.isContainment());
				ec.getEStructuralFeatures().add(eref);
			}

			if (mc.getSuperTypes().isEmpty()) {
				final EReference eRefChildren = factory.createEReference();
				eRefChildren.setName(ModelioClass.REF_CHILDREN);
				eRefChildren.setOrdered(true);
				eRefChildren.setUnique(true);
				eRefChildren.setUpperBound(ETypedElement.UNBOUNDED_MULTIPLICITY);
				eRefChildren.setEType(ec);
				eRefChildren.setContainment(true);

				final EReference eRefParent = (EReference) ec.getEStructuralFeature(ModelioClass.REF_PARENT);
				eRefChildren.setEOpposite(eRefParent);
				eRefParent.setEOpposite(eRefChildren);

				ec.getEStructuralFeatures().add(eRefChildren);
			}
		}

		r.save(null);
		return r;
	}

	private void addPackageContents(Resource r, final ModelioPackage pkg) throws Exception {
		EPackage ep = factory.createEPackage();
		ep.setName(pkg.getName());
		ep.setNsPrefix("m" + nPackage++);
		ep.setNsURI(pkg.getNsURI());
		r.getContents().add(ep);
		this.packages.add(ep);

		for (final IHawkClassifier mc : pkg.getClasses()) {
			final EClass ec = factory.createEClass();
			ec.setName(mc.getName());
			ep.getEClassifiers().add(ec);
			if (mClasses.put(mc.getName(), new Tuple<>(ec, (ModelioClass) mc)) != null) {
				throw new Exception("More than one class named " + mc.getName() + ": aborting");
			}
		}
		for (ModelioPackage subpkg : pkg.getPackages()) {
			addPackageContents(r, subpkg);
		}
	}
}
