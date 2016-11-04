/*******************************************************************************
 * Copyright (c) 2016 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/

package org.eclipse.jdt.internal.junit5.runner;

import java.util.Optional;
import java.util.Set;

import org.eclipse.jdt.internal.junit.runner.ITestIdentifier;
import org.eclipse.jdt.internal.junit.runner.ITestReference;
import org.eclipse.jdt.internal.junit.runner.IVisitsTestTrees;
import org.eclipse.jdt.internal.junit.runner.RemoteTestRunner;
import org.eclipse.jdt.internal.junit.runner.TestExecution;
import org.eclipse.jdt.internal.junit.runner.TestIdMap;
import org.junit.platform.launcher.Launcher;
import org.junit.platform.launcher.LauncherDiscoveryRequest;
import org.junit.platform.launcher.TestIdentifier;
import org.junit.platform.launcher.TestPlan;

public class JUnit5TestReference implements ITestReference {

	private LauncherDiscoveryRequest fRequest;

	private Launcher fLauncher;

	private TestPlan fTestPlan;

	public JUnit5TestReference(LauncherDiscoveryRequest request, Launcher launcher) {
		fRequest= request;
		fLauncher= launcher;
		fTestPlan= fLauncher.discover(fRequest);
	}

	@Override
	public int countTestCases() {
		return (int) fTestPlan.countTestIdentifiers(TestIdentifier::isTest);
	}

	@Override
	public void sendTree(IVisitsTestTrees notified) {
		for (TestIdentifier root : fTestPlan.getRoots()) {
			for (TestIdentifier child : fTestPlan.getChildren(root)) {
				sendTree(notified, child);
			}
		}
	}

	void sendTree(IVisitsTestTrees notified, TestIdentifier testIdentifier) {
		JUnit5Identifier identifier= new JUnit5Identifier(testIdentifier);
		String parentId= getParentId(testIdentifier, fTestPlan);
		if (testIdentifier.isTest()) {
			notified.visitTreeEntry(identifier, false, 1, false, false, parentId);
		} else {
			Set<TestIdentifier> children= fTestPlan.getChildren(testIdentifier);
			notified.visitTreeEntry(identifier, true, children.size(), isTestFactoryMethod(testIdentifier), false, parentId);
			for (TestIdentifier child : children) {
				sendTree(notified, child);
			}
		}
	}

	// TODO - JUnit5: replace with new JUnit API -
	// https://github.com/junit-team/junit5/issues/508
	private boolean isTestFactoryMethod(TestIdentifier testIdentifier) {
		String uniqueId= testIdentifier.getUniqueId();
		int index= uniqueId.lastIndexOf("/["); //$NON-NLS-1$
		if (index != -1) {
			return uniqueId.substring(index + 2).startsWith("test-factory"); //$NON-NLS-1$
		}
		return false;
	}

	/**
	 * @param testIdentifier the test identifier whose parent id is required
	 * @param testPlan the test plan containing the test
	 * @return the parent id from {@link TestIdMap} if the parent is present, otherwise
	 *         <code>"-1"</code>
	 */
	static String getParentId(TestIdentifier testIdentifier, TestPlan testPlan) {
		Optional<TestIdentifier> parentOp= testPlan.getParent(testIdentifier);
		String parentTestId;
		if (parentOp.isPresent()) {
			JUnit5Identifier parentTestIdentifier= new JUnit5Identifier(parentOp.get());
			parentTestId= RemoteTestRunner.fgTestRunServer.getTestId(parentTestIdentifier);
		} else {
			// should not happen
			parentTestId= "-1"; //$NON-NLS-1$ 
		}
		return parentTestId;
	}

	@Override
	public void run(TestExecution execution) {
		JUnit5TestListener listener= new JUnit5TestListener(execution.getListener(), fTestPlan);
		fLauncher.registerTestExecutionListeners(listener);

		fLauncher.execute(fRequest);
	}

	@Override
	public ITestIdentifier getIdentifier() { // not used
		return new JUnit5Identifier(fTestPlan.getRoots().iterator().next());
	}

	@Override
	public boolean equals(Object obj) {
		if (!(obj instanceof JUnit5TestReference))
			return false;

		JUnit5TestReference ref= (JUnit5TestReference) obj;
		return (ref.fTestPlan.equals(fTestPlan));
	}

	@Override
	public int hashCode() {
		return fTestPlan.hashCode();
	}

	@Override
	public String toString() {
		return fTestPlan.toString();
	}

}
