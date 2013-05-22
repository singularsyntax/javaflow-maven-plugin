Javaflow Maven Plugin
=====================

Maven plugin that enhances Java class files with Javaflow instrumentation.
Javaflow is a library providing a continuations API for Java, accomplished
via bytecode enhancement which modifies the ordinary control flow of method
calls to accomodate the ability to suspend and resume code execution at
arbitrary points.

<http://commons.apache.org/sandbox/commons-javaflow/>
<http://en.wikipedia.org/wiki/Continuation>


INSTALLATION
------------

To install into a local Maven repository type the following:

    mvn install


USAGE
-----

To use Javaflow and this plugin, you need to perform some manual static
analysis of your code. Alternately, you can use the special ClassLoader
provided with Javaflow which does bytecode enhancement on the fly. See
the Javaflow documentation for instructions on the latter approach.

To manually apply bytecode enhancement, you need to identify all classes
(including inner classes, which compile to separate class files) which
invoke any of the execution flow control methods on the Javaflow Continuation
class. For any two classes A, which begin execution with one of the method
calls startWith(), startSuspendedWith(), or continueWith(), and B, which
suspend execution with one of the method calls again(), cancel(), exit(), or
suspend(), both classes and **any classes containing methods on the call graph
between A and B** must be selected for enhancement.

Once the required class files are identified, create the files

    ${project}/src/main/javaflow/classes
    ${project}/src/test/javaflow/classes

that list, one per line, the fully-qualified Java package path to the
main and test class files that should be bytecode-enhanced for
Javaflow, for example:

    meme/singularsyntax/ojos/MojoMan.class
    meme/singularsyntax/ojos/PojoPan.class
    meme/singularsyntax/ojos/RojoRon.class
    meme/singularsyntax/ojos/RojoRon$SomeInner.class
    meme/singularsyntax/ojos/RojoRon$AnotherInner.class

When the goal executes, the indicated class files are enhanced with
Javaflow bytecode. Backups of the original classes are made in the

    ${project.build.directory}/javaflow/orig-classes
    ${project.build.directory}/javaflow/orig-test-classes

directories: The goal can be executed with the following command:

    mvn javaflow:enhance

To bind the goal to a project's build, add the following to pom.xml:

    <build>
      <plugins>
        <plugin>
          <groupId>meme.singularsyntax.java</groupId>
          <artifactId>javaflow-maven-plugin</artifactId>
          <version>1.0-SNAPSHOT</version>
          <executions>
            <execution>
              <phase>process-classes</phase>
              <goals>
                <goal>enhance</goal>
              </goals>
            </execution>
          </executions>
        </plugin>
      </plugins>
    </build>


AUTHOR
------

Stephen J. Scheck <singularsyntax@gmail.com>
