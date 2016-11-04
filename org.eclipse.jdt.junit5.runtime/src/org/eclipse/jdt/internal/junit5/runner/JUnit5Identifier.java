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

import java.text.MessageFormat;
import java.util.Optional;

import org.junit.platform.engine.TestSource;
import org.junit.platform.engine.support.descriptor.ClassSource;
import org.junit.platform.engine.support.descriptor.MethodSource;
import org.junit.platform.launcher.TestIdentifier;

import org.eclipse.jdt.internal.junit.runner.ITestIdentifier;
import org.eclipse.jdt.internal.junit.runner.MessageIds;

public class JUnit5Identifier implements ITestIdentifier {

	private TestIdentifier fTestIdentifier;

	public JUnit5Identifier(TestIdentifier testIdentifier) {
		fTestIdentifier= testIdentifier;
	}

	@Override
	public String getName() {
		Optional<TestSource> source= fTestIdentifier.getSource();
		if (source.isPresent()) {
			TestSource testSource= source.get();
			if (testSource instanceof ClassSource) {
				return ((ClassSource) testSource).getJavaClass().getName();
			} else if (testSource instanceof MethodSource) {
				MethodSource methodSource= (MethodSource) testSource;
				String nameEntry= MessageFormat.format(MessageIds.TEST_IDENTIFIER_MESSAGE_FORMAT, methodSource.getMethodName(), methodSource.getClassName());
				String parameterTypes= methodSource.getMethodParameterTypes();
				if (!parameterTypes.isEmpty()) {
					return nameEntry + ":" + parameterTypes; //$NON-NLS-1$					
				}
				return nameEntry;
			}
		}
		return fTestIdentifier.getDisplayName();
	}

	@Override
	public String getDisplayName() {
		return fTestIdentifier.getDisplayName();
	}

	@Override
	public int hashCode() {
		return fTestIdentifier.hashCode();
	}

	@Override
	public boolean equals(Object obj) {
		if (!(obj instanceof JUnit5Identifier))
			return false;

		JUnit5Identifier id= (JUnit5Identifier) obj;
		return fTestIdentifier.equals(id.fTestIdentifier);
	}
}
