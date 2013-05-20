/*
 * JavaflowEnhanceMojo.java
 * 
 * Copyright 2013 Stephen J. Scheck
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 * 
 */

package meme.singularsyntax.mojo;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.apache.commons.javaflow.bytecode.transformation.ResourceTransformer;
import org.apache.commons.javaflow.bytecode.transformation.asm.AsmClassTransformer;
import org.apache.commons.javaflow.utils.RewritingUtils;
import org.apache.maven.artifact.DependencyResolutionRequiredException;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.MavenProject;

/**
 * Maven goal that enhances Java class files with Javaflow instrumentation.
 * 
 * http://commons.apache.org/sandbox/javaflow/
 *
 * Usage: Create the files
 * 
 *            ${project}/src/main/javaflow/classes
 *            ${project}/src/test/javaflow/classes
 * 
 *        that list, one per line, the fully-qualified Java package path to the
 *        main and test class files that should be bytecode-enhanced for
 *        Javaflow, for example:
 * 
 *            meme/singularsyntax/ojos/MojoMan.class
 *            meme/singularsyntax/ojos/PojoPan.class
 *            meme/singularsyntax/ojos/RojoRon.class
 *            meme/singularsyntax/ojos/RojoRon$SomeInner.class
 *            meme/singularsyntax/ojos/RojoRon$AnotherInner.class
 * 
 *        Note that inner classes compile to separate class files and must be
 *        included individually. It is only necessary to include those inner
 *        classes which call Javaflow API methods or are in the call graph
 *        between other classes which do.
 * 
 *        When the goal executes, the indicated class files are enhanced with
 *        Javaflow bytecode. Backups of the original classes are made in the
 * 
 *            ${project.build.directory}/javaflow/orig-classes
 *            ${project.build.directory}/javaflow/orig-test-classes
 * 
 *        directories: The goal can be executed with the following command:
 * 
 *            mvn javaflow:enhance
 * 
 *        To bind the goal to a project's build, add the following to pom.xml:
 * 
 *            <build>
 *              <plugins>
 *                <plugin>
 *                  <groupId>meme.singularsyntax.java</groupId>
 *                  <artifactId>javaflow-maven-plugin</artifactId>
 *                  <version>1.0-SNAPSHOT</version>
 *                  <executions>
 *                    <execution>
 *                      <phase>process-classes</phase>
 *                      <goals>
 *                        <goal>enhance</goal>
 *                      </goals>
 *                    </execution>
 *                  </executions>
 *                </plugin>
 *              </plugins>
 *            </build>
 *
 * @goal enhance
 * 
 * @phase process-classes
 * 
 * @requiresDependencyResolution compile
 * 
 * @author sscheck
 * 
 */
public class JavaflowEnhanceMojo extends AbstractMojo
{
	private static final String CLASSFILE_REWRITE_TEMPLATE = "%s.JAVAFLOW_MOJO_ENHANCED";

	/**
	 * @parameter expression="${project}"
	 * @required
	 * @readonly
	 */
	private MavenProject project;

	/**
     * classList
     *
     * @parameter default-value="src/main/javaflow/classes"
     */
    private File classList;

    /**
     * testClassList
     *
     * @parameter default-value="src/test/javaflow/classes"
     */
    private File testClassList;

    /**
     * originalClasses
     *
     * @parameter default-value="${project.build.directory}/javaflow/orig-classes"
     */
    private File originalClasses;

    /**
     * originalTestClasses
     *
     * @parameter default-value="${project.build.directory}/javaflow/orig-test-classes"
     */
    private File originalTestClasses;

	@Override
	public void execute() throws MojoExecutionException {

		prepareClasspath();

		if (classList.exists() && classList.isFile()) {
			String outputDir = project.getBuild().getOutputDirectory();
			enhanceClassFiles(outputDir, originalClasses, getClassFiles(outputDir, classList));
		}

		if (testClassList.exists() && testClassList.isFile()) {
			String outputDir = project.getBuild().getTestOutputDirectory();
			enhanceClassFiles(outputDir, originalTestClasses, getClassFiles(outputDir, testClassList));
		}
	}

	private List<String> getClassFiles(String outputDir, File classList) throws MojoExecutionException {
		BufferedReader in = null;
		String fileName = null;
		List<String> classFiles = new ArrayList<String>();

		try {
			in = new BufferedReader(new FileReader(classList));
			while ((fileName = in.readLine()) != null) {
				// validation tests on the complete path and file
				File classFile = new File(outputDir, fileName);

				if (! classFile.exists())
					throw new MojoExecutionException(String.format("Class file %s does not exist", classFile.getName()));

				if (! classFile.isFile())
					throw new MojoExecutionException(String.format("%s is not a file", classFile.getName()));

				if (! classFile.getName().endsWith(".class"))
					throw new MojoExecutionException(String.format("%s is not a Java class file", classFile.getName()));

				// add the relative path only
				classFiles.add(fileName);
			}

		} catch (FileNotFoundException e) {
			throw new MojoExecutionException(e.getMessage());
		} catch (IOException e) {
			throw new MojoExecutionException(e.getMessage());
		}

		return (classFiles);
	}

	private void enhanceClassFiles(String outputDir, File backupDir, List<String> classFileNames) throws MojoExecutionException {

		Log log = getLog();
		ResourceTransformer transformer = new AsmClassTransformer();

		for (String classFileName : classFileNames) {
			try {
				File source = new File(outputDir, classFileName);
				File destination = new File(String.format(CLASSFILE_REWRITE_TEMPLATE, source.getAbsolutePath()));
				File backupClassFile = new File(backupDir, classFileName);

				if (backupClassFile.exists() && (source.lastModified() <= backupClassFile.lastModified())) {
					log.info(source + " is up to date");
					continue;
				}

				log.info(String.format("Enhancing class file bytecode for Javaflow: %s", source));
				RewritingUtils.rewriteClassFile(source, transformer, destination);

				if (backupClassFile.exists()) {
					log.debug(String.format("Backup for original class file %s already exists - removing it", backupClassFile));
					backupClassFile.delete();
				}

				log.debug(String.format("Renaming original class file from %s to %s", source, backupClassFile));
				FileUtils.moveFile(source, backupClassFile);

				log.debug(String.format("Renaming rewritten class file from %s to %s", destination, source));
				FileUtils.moveFile(destination, source);

				backupClassFile.setLastModified(source.lastModified());

			} catch (IOException e) {
				throw new MojoExecutionException(e.getMessage());
			}
		}
	}

	@SuppressWarnings("unchecked")
	private void prepareClasspath() throws MojoExecutionException {
		List<String> runtimeClasspathElements = null;
		URLClassLoader classLoader = null;

		try {
			runtimeClasspathElements = project.getCompileClasspathElements();
			URL[] runtimeUrls = new URL[runtimeClasspathElements.size()];

			for (int ii = 0; ii < runtimeClasspathElements.size(); ii++) {
				String element = runtimeClasspathElements.get(ii);
				File elementFile = new File(element);
				runtimeUrls[ii] = elementFile.toURI().toURL();
			}

			classLoader = new URLClassLoader(runtimeUrls, Thread.currentThread().getContextClassLoader());
			Thread.currentThread().setContextClassLoader(classLoader);

		} catch (DependencyResolutionRequiredException e) {
			throw new MojoExecutionException(e.getMessage());
		} catch (MalformedURLException e) {
			throw new MojoExecutionException(e.getMessage());
		}
	}
}
