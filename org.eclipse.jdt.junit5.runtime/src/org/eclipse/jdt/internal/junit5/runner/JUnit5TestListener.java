/*******************************************************************************
 * Copyright (c) 2016, 2017 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.internal.junit5.runner;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.List;
import java.util.Optional;

import org.eclipse.jdt.internal.junit.runner.FailedComparison;
import org.eclipse.jdt.internal.junit.runner.IListensToTestExecutions;
import org.eclipse.jdt.internal.junit.runner.ITestIdentifier;
import org.eclipse.jdt.internal.junit.runner.MessageIds;
import org.eclipse.jdt.internal.junit.runner.RemoteTestRunner;
import org.eclipse.jdt.internal.junit.runner.TestReferenceFailure;
import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.engine.TestExecutionResult.Status;
import org.junit.platform.launcher.TestExecutionListener;
import org.junit.platform.launcher.TestIdentifier;
import org.junit.platform.launcher.TestPlan;
import org.opentest4j.AssertionFailedError;
import org.opentest4j.MultipleFailuresError;
import org.opentest4j.ValueWrapper;

public class JUnit5TestListener implements TestExecutionListener {

	private final IListensToTestExecutions fNotified;

	private TestPlan fTestPlan;

	public JUnit5TestListener(IListensToTestExecutions notified) {
		fNotified= notified;
	}

	@Override
	public void testPlanExecutionStarted(TestPlan testPlan) {
		fTestPlan= testPlan;
	}

	@Override
	public void testPlanExecutionFinished(TestPlan testPlan) {
		fTestPlan= null;
	}

	@Override
	public void executionStarted(TestIdentifier testIdentifier) {
		if (testIdentifier.isTest()) {
			fNotified.notifyTestStarted(getIdentifier(testIdentifier, false, false));
		}
	}

	@Override
	public void executionFinished(TestIdentifier testIdentifier, TestExecutionResult testExecutionResult) {
		Status result= testExecutionResult.getStatus();
		if (testIdentifier.isTest()) {
			if (result != Status.SUCCESSFUL) {
				boolean assumptionFailed= result == Status.ABORTED;

				String trace;
				FailedComparison comparison;
				String status;

				Optional<Throwable> throwableOp= testExecutionResult.getThrowable();
				if (throwableOp.isPresent()) {
					Throwable exception= throwableOp.get();
					trace= getTrace(exception);
					comparison= getFailedComparison(exception);
					status= (assumptionFailed || exception instanceof AssertionError) ? MessageIds.TEST_FAILED : MessageIds.TEST_ERROR;
				} else {
					trace= ""; //$NON-NLS-1$
					comparison= null;
					status= MessageIds.TEST_FAILED;
				}

				ITestIdentifier identifier= getIdentifier(testIdentifier, false, assumptionFailed);
				fNotified.notifyTestFailed(new TestReferenceFailure(identifier, status, trace, comparison));
			}

			fNotified.notifyTestEnded(getIdentifier(testIdentifier, false, false));

		} else { // container
			if (result != Status.SUCCESSFUL) {
				Optional<Throwable> throwableOp= testExecutionResult.getThrowable();
				String trace= ""; //$NON-NLS-1$
				if (throwableOp.isPresent()) {
					trace= getTrace(throwableOp.get());
				}
				ITestIdentifier identifier= getIdentifier(testIdentifier, false, false);
				fNotified.notifyTestFailed(new TestReferenceFailure(identifier, MessageIds.TEST_ERROR, trace));
			}
		}
	}

	private String getTrace(Throwable exception) {
		StringWriter stringWriter= new StringWriter();
		exception.printStackTrace(new PrintWriter(stringWriter));
		return stringWriter.getBuffer().toString();
	}

	private FailedComparison getFailedComparison(Throwable exception) {
		if (exception instanceof AssertionFailedError) {
			AssertionFailedError assertionFailedError= (AssertionFailedError) exception;
			ValueWrapper expected= assertionFailedError.getExpected();
			ValueWrapper actual= assertionFailedError.getActual();
			if (expected == null || actual == null) {
				return null;
			}
			return new FailedComparison(expected.getStringRepresentation(), actual.getStringRepresentation());
		} else if (exception instanceof MultipleFailuresError) {
			String expectedStr= ""; //$NON-NLS-1$
			String actualStr= ""; //$NON-NLS-1$
			String delimiter= "\n\n"; //$NON-NLS-1$
			List<AssertionError> failures= ((MultipleFailuresError) exception).getFailures();
			for (AssertionError assertionError : failures) {
				if (assertionError instanceof AssertionFailedError) {
					AssertionFailedError assertionFailedError= (AssertionFailedError) assertionError;
					ValueWrapper expected= assertionFailedError.getExpected();
					ValueWrapper actual= assertionFailedError.getActual();
					if (expected == null || actual == null) {
						return null;
					}
					expectedStr+= expected.getStringRepresentation() + delimiter;
					actualStr+= actual.getStringRepresentation() + delimiter;
				} else {
					return null;
				}
			}
			return new FailedComparison(expectedStr, actualStr);
		}
		return null;
	}

	@Override
	public void executionSkipped(TestIdentifier testIdentifier, String reason) {
		if (testIdentifier.isContainer() && fTestPlan != null) {
			fTestPlan.getDescendants(testIdentifier).stream().filter(t -> t.isTest()).forEachOrdered(t -> notifySkipped(t));
		} else {
			notifySkipped(testIdentifier);
		}
	}

	private void notifySkipped(TestIdentifier testIdentifier) {
		// Send message to listeners which would be stale otherwise
		ITestIdentifier identifier= getIdentifier(testIdentifier, true, false);
		fNotified.notifyTestStarted(identifier);
		fNotified.notifyTestEnded(identifier);
	}


	@Override
	public void dynamicTestRegistered(TestIdentifier testIdentifier) {
		if (testIdentifier.isTest() && fTestPlan != null) {
			JUnit5Identifier dynamicTestIdentifier= new JUnit5Identifier(testIdentifier);
			RemoteTestRunner.fgTestRunServer.visitTreeEntry(dynamicTestIdentifier, false, 1, true, JUnit5TestReference.getParentId(testIdentifier, fTestPlan));
		}
	}

	private ITestIdentifier getIdentifier(TestIdentifier testIdentifier, boolean ignored, boolean assumptionFailed) {
		if (ignored) {
			return new IgnoredTestIdentifier(testIdentifier);
		}
		if (assumptionFailed) {
			return new AssumptionFailedTestIdentifier(testIdentifier);
		}
		return new JUnit5Identifier(testIdentifier);
	}

	private class IgnoredTestIdentifier extends JUnit5Identifier {
		public IgnoredTestIdentifier(TestIdentifier description) {
			super(description);
		}

		@Override
		public String getName() {
			String name= super.getName();
			if (name != null)
				return MessageIds.IGNORED_TEST_PREFIX + name;
			return null;
		}
	}

	private class AssumptionFailedTestIdentifier extends JUnit5Identifier {
		public AssumptionFailedTestIdentifier(TestIdentifier description) {
			super(description);
		}

		@Override
		public String getName() {
			String name= super.getName();
			if (name != null)
				return MessageIds.ASSUMPTION_FAILED_TEST_PREFIX + name;
			return null;
		}
	}
}
