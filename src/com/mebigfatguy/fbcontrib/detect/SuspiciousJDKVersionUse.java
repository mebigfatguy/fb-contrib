/*
 * fb-contrib - Auxiliary detectors for Java programs
 * Copyright (C) 2005-2014 Dave Brosius
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */
package com.mebigfatguy.fbcontrib.detect;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.net.URLDecoder;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.apache.bcel.Constants;
import org.apache.bcel.Repository;
import org.apache.bcel.classfile.ClassParser;
import org.apache.bcel.classfile.JavaClass;
import org.apache.bcel.classfile.Method;

import edu.umd.cs.findbugs.BugInstance;
import edu.umd.cs.findbugs.BugReporter;
import edu.umd.cs.findbugs.BytecodeScanningDetector;
import edu.umd.cs.findbugs.ba.ClassContext;

public class SuspiciousJDKVersionUse extends BytecodeScanningDetector
{
	private static final Map<Integer, String> verRegEx = new HashMap<Integer, String>();
	static {
		verRegEx.put(Integer.valueOf(Constants.MAJOR_1_1), "(jdk|j2?re)1.1");
		verRegEx.put(Integer.valueOf(Constants.MAJOR_1_2), "(jdk|j2?re)1.2");
		verRegEx.put(Integer.valueOf(Constants.MAJOR_1_3), "(jdk|j2?re)1.3");
		verRegEx.put(Integer.valueOf(Constants.MAJOR_1_4), "(jdk|j2?re)1.4");
		verRegEx.put(Integer.valueOf(Constants.MAJOR_1_5), "(jdk|j2?re)1.5");
		verRegEx.put(Integer.valueOf(Constants.MAJOR_1_6), "(jdk|j2?re)1.6");
        verRegEx.put(Integer.valueOf(51), "(jdk|j2?re)1.7");
        verRegEx.put(Integer.valueOf(52), "(jdk|j2?re)1.8");
	}
	private static final Map<Integer, String> versionStrings = new HashMap<Integer, String>();
	static {
		versionStrings.put(Integer.valueOf(Constants.MAJOR_1_1), "JDK 1.1");
		versionStrings.put(Integer.valueOf(Constants.MAJOR_1_2), "JDK 1.2");
		versionStrings.put(Integer.valueOf(Constants.MAJOR_1_3), "JDK 1.3");
		versionStrings.put(Integer.valueOf(Constants.MAJOR_1_4), "JDK 1.4");
		versionStrings.put(Integer.valueOf(Constants.MAJOR_1_5), "JDK 1.5");
        versionStrings.put(Integer.valueOf(Constants.MAJOR_1_6), "JDK 1.6");
        versionStrings.put(Integer.valueOf(51), "JDK 1.7");
        versionStrings.put(Integer.valueOf(52), "JDK 1.8");
	}
	private static final Pattern jarPattern = Pattern.compile("jar:file:/*([^!]*)");
	private static final String SJVU_JDKHOME = "fb-contrib.sjvu.jdkhome";

	private final Map<String, File> versionPaths;
	private final Map<Integer, Map<String, Set<String>>> validMethodsByVersion;
	private final Map<String, String> superNames;
	private File jdksRoot = null;
	private ZipFile jdkZip;
	private Integer clsMajorVersion;
	private final BugReporter bugReporter;

	public SuspiciousJDKVersionUse(BugReporter bugReporter) {
		this.bugReporter = bugReporter;
		versionPaths = new HashMap<String, File>();
		validMethodsByVersion = new HashMap<Integer, Map<String, Set<String>>>();
		superNames = new HashMap<String, String>();
	}

	@Override
	public void visitClassContext(ClassContext classContext) {
		try {
			clsMajorVersion = Integer.valueOf(classContext.getJavaClass().getMajor());
			File rtJar = getRTJarFile();
			if (rtJar == null)
				rtJar = getRTJarFromProperty();

			if (rtJar != null) {
				jdkZip = new ZipFile(rtJar);
				super.visitClassContext(classContext);
			} else {
				String version = versionStrings.get(clsMajorVersion);
				ClassNotFoundException cnfe;
				if (version != null)
					cnfe = new ClassNotFoundException("The " + version + " rt.jar was not found. This file is needed for finding invalid methods with the SuspiciousJDKVersionUse detector. The system property 'fb-contrib.sjvu.jdkhome' can be used to specify the location of the appropriate JDK.");
				else
					cnfe = new ClassNotFoundException("The JDK's rt.jar for classes with class version " + clsMajorVersion + " was not found. This file is needed for finding invalid methods with the SuspiciousJDKVersionUse detector. The system property 'fb-contrib.sjvu.jdkhome' can be used to specify the location of the appropriate JDK.");
				cnfe.fillInStackTrace();
				bugReporter.reportMissingClass(cnfe);
			}
		} catch (IOException ioe) {
			//Hmm What to do
		} finally {
			clsMajorVersion = null;
			try {
				if (jdkZip != null)
					jdkZip.close();
			} catch (IOException ioe) {
			}
			jdkZip = null;
		}
	}

	@Override
	public void sawOpcode(int seen) {

		String clsName;
		try {
			if ((seen == INVOKEVIRTUAL) //Interfaces are more difficult, ignore for now
			||  (seen == INVOKESTATIC)
			||  (seen == INVOKESPECIAL)) {
				clsName = getClassConstantOperand();
				if ((clsName.startsWith("java/"))
				||  (clsName.startsWith("javax/"))) {
					Method m = findCalledMethod();
					if (m == null)
						return;

					Map<String, Set<String>> validMethods = validMethodsByVersion.get(clsMajorVersion);
					if (validMethods == null) {
						validMethods = new HashMap<String, Set<String>>();
						validMethodsByVersion.put(clsMajorVersion, validMethods);
					}

					if (!isValid(validMethods, clsName)) {
						bugReporter.reportBug(new BugInstance(this, "SJVU_SUSPICIOUS_JDK_VERSION_USE", HIGH_PRIORITY)
								   .addClass(this)
								   .addMethod(this)
								   .addSourceLine(this)
								   .addCalledMethod(this));
					}
				}
			}
		} catch (ClassNotFoundException cnfe) {
			bugReporter.reportMissingClass(cnfe);
		} catch (IOException ioe) {
			//Hmm what do do.
		}
	}

	private Method findCalledMethod() {
		try {
			JavaClass clss = Repository.lookupClass(getClassConstantOperand());
			Method[] methods = clss.getMethods();
			String calledMethod = getNameConstantOperand();
			String calledSignature = getSigConstantOperand();
			for (Method m : methods) {
				if (m.getName().equals(calledMethod) && m.getSignature().equals(calledSignature)) {
					return m;
				}
			}

			return null;
		} catch (ClassNotFoundException cnfe) {
			bugReporter.reportMissingClass(cnfe);
			return null;
		}
	}

	private boolean isValid(Map<String, Set<String>> validMethods, String clsName) throws IOException, ClassNotFoundException {
		InputStream is = null;

		try {
			Set<String> methodInfos = validMethods.get(clsName);
			if (methodInfos == null) {

				ZipEntry ze = jdkZip.getEntry(clsName + ".class");
				if (ze != null) {
					is = new BufferedInputStream(jdkZip.getInputStream(ze));
					ClassParser parser = new ClassParser(is, clsName);
					JavaClass calledClass = parser.parse();

					superNames.put(clsName, calledClass.getSuperclassName().replace('.', '/'));
					Method[] methods = calledClass.getMethods();

					methodInfos = new HashSet<String>(methods.length);
					validMethods.put(clsName, methodInfos);

					for (Method m : methods) {
						methodInfos.add(m.getName() + m.getSignature());
					}
				} else if (clsName.startsWith("java/")) {
					bugReporter.reportBug(new BugInstance(this, "SJVU_SUSPICIOUS_JDK_VERSION_USE", HIGH_PRIORITY)
							   .addClass(this)
							   .addMethod(this)
							   .addSourceLine(this)
							   .addClass(clsName));
				}
			}

			if (methodInfos != null) {
				String wantedMethod = getNameConstantOperand() + getSigConstantOperand();
				if (methodInfos.contains(wantedMethod))
					return true;
				else if ("java/lang/Object".equals(clsName))
					return false;
				else
					return isValid(validMethods, superNames.get(clsName));
			}

			return true;
		}
		finally {
			if (is != null) {
				try {
					is.close();
				} catch (IOException ioe) {
				}
			}
		}
	}

	private File getRTJarFile(){
		String versionStr = verRegEx.get(clsMajorVersion);
		if (versionStr == null)
			return null;

		File rtPath = versionPaths.get(versionStr);
		if (rtPath != null)
			return rtPath;

		if (jdksRoot == null) {
			URL jdkUrl = SuspiciousJDKVersionUse.class.getResource("/java/lang/Object.class");
			if (jdkUrl != null) {
				Matcher m = jarPattern.matcher(jdkUrl.toExternalForm());

				if (m.find()) {
					String path = m.group(1);
					jdksRoot = new File(path);
					Pattern verPat = Pattern.compile(versionStr);
					m = verPat.matcher(jdksRoot.getName());
					while ((jdksRoot.getParentFile() != null) && !m.find()) {
						jdksRoot = jdksRoot.getParentFile();
						m = verPat.matcher(jdksRoot.getName());
					}

					if (jdksRoot.getParentFile() == null)
						return null;

					try {
						String encoding = System.getProperty("file.encoding");
						jdksRoot = new File(URLDecoder.decode(jdksRoot.getParentFile().getPath(), encoding));
					} catch (UnsupportedEncodingException uee) {
						return null;
					}
				}
			}
		}

		if (jdksRoot != null) {
			File[] possibleJdks = jdksRoot.listFiles();
			for (File possibleJdk : possibleJdks) {
				Pattern verPat = Pattern.compile(versionStr);
				Matcher m = verPat.matcher(possibleJdk.getName());
				if (m.find()) {
					File wantedRtJar = new File(possibleJdk, "lib/rt.jar");
					if (!wantedRtJar.exists()) {
						wantedRtJar = new File(possibleJdk, "jre/lib/rt.jar");
						if (!wantedRtJar.exists())
							return null;
					}
					versionPaths.put(versionStr, wantedRtJar);
					return wantedRtJar;
				}
			}
		}

		return null;
	}

	private File getRTJarFromProperty() {
		String jdkHome = System.getProperty(SJVU_JDKHOME);
		if (jdkHome == null)
			return null;

		File rtJar = new File(jdkHome, "lib/rt.jar");
		if (rtJar.exists())
			return rtJar;
		rtJar = new File(jdkHome, "jre/lib/rt.jar");
		if (rtJar.exists())
			return rtJar;

		return null;
	}
}
