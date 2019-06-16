fb-contrib
==========

[![Build Status](https://travis-ci.org/mebigfatguy/fb-contrib.svg?branch=master)](https://travis-ci.org/mebigfatguy/fb-contrib)

a FindBugs and SpotBugs plugin for doing static code analysis on java byte code.
For information see http://fb-contrib.sf.net



Available on [maven.org](http://search.maven.org/#search%7Cgav%7C1%7Cg%3A%22com.mebigfatguy.fb-contrib%22%20AND%20a%3A%22fb-contrib%22)

for FindBugs:

       GroupId: com.mebigfatguy.fb-contrib
    ArtifactId: fb-contrib
       Version: 7.4.6
       
For SpotBugs

       GroupId: com.mebigfatguy.sb-contrib
    ArtifactId: sb-contrib
       
       
Developer
* Dave Brosius


Contributors
* Bhaskar Maddala
* Chris Peterson
* Grzegorz Slowikowski
* Trevor Pounds
* Ronald Blaschke
* Zenichi Amano
* Philipp Wiesemann
* Kevin Lubick
* Philippe Arteau
* Thrawn
* Juan Martin Sotuyo Dodero
* Richard Fearn
* Mikkel Kjeldsen
* Jeremy Landis
* Peter Hermsdorf
* David Burström
* Venkata Gajavalli
* Rubén López


fb-contrib has two main branches, 'findbugs' and 'spotbugs'. Code is committed to findbugs, and then merged to spotbugs.
It is preferable therefore to create merge requests against the findbugs branch. Thanks!


## Setting up for Development - Ant
1. Download/install [Eclipse](https://www.eclipse.org/home/index.php), ideally 4.3 (Kepler) or newer.  The standard release (for Java) will work fine.
2. **Ant Dependencies** Download [yank, the dependency manager](http://search.maven.org/#search%7Cgav%7C1%7Cg%3A%22com.mebigfatguy.yank%22%20AND%20a%3A%22yank%22) and [bug-rank-check-style](https://bitbucket.org/klubick/bugrankcheckstyle/downloads).  Both jars (v1.2.0+ and v1.0.0+) should go in your ~/.ant/lib folder, which you will have to make if it doesn't exist.  Windows people, this goes under [Username]/.ant/lib.
Don't have more than one version of either jar in this folder, as it's not clear which one Ant will load, leading to annoying compatibility issues. This can be done using the ant target **ant infra_jars**
3. [Fork](https://help.github.com/articles/fork-a-repo) this git repo and clone it.  [GitHub for Windows](https://windows.github.com/) or [GitHub for Mac](https://mac.github.com/) are good clients if you don't already have one.
4. Open Eclipse.  File>Import and then choose "Existing projects into workspace", and find the fb-contrib folder you created in step 3.  Ignore any compile errors (for now).
5. Using git, clone the FindBugs repository using `git clone https://code.google.com/p/findbugs/`  You will only need the findbugs subfolder (the one that has README.txt in it).  You can delete the rest, if you wish.
6. Import this project into Eclipse as well.  You may wish to [mark these files as read-only](https://cloud.githubusercontent.com/assets/6819944/3866638/801ae098-1fdc-11e4-9fce-1fdecb81402f.gif), so you modify the "correct" files.
7. In the fb-contrib project, find the `user.properties.example` file.  Make a copy of it named user.properties (this will not be tracked by version control). Modify the findbugs.dir property to where ever you have the FindBugs distribution installed.  This is the [executable FindBugs](http://findbugs.sourceforge.net/downloads.html) folder, *not* the source folder.  The jar will be "installed" to (findbugs.dir)\plugin.
For example, If you are using FindBugs with Eclipse (and you extracted Eclipse to C:\\), you'll set this to something like `findbugs.dir=/eclipse/plugins/edu.umd.cs.findbugs.plugin.eclipse_3.0.0.20140706-2cfb468`
8. Finally, build fb-contrib by finding the [build.xml](https://github.com/mebigfatguy/fb-contrib/blob/717f757d69c098e1baf786d3e7c03efacf2bbfaf/build.xml) file in Eclipse, right-click it, and select Run As > Ant Build.  The dependencies needed should be downloaded to fb-contrib/lib and the fb-contrib-VERSION.jar should be built.

## Setting up for Development - Maven
1. Download/install [Maven](https://maven.apache.org), version 2.2.1 or newer.
2. Clone the Git repository, as per step 3 above.
3. Run `mvn clean install` in the fb-contrib directory.

## Usage - Maven

To include the fb-contrib detectors when checking your project with FindBugs, you can use the [FindBugs Maven plugin](https://gleclaire.github.io/findbugs-maven-plugin/usage.html).
The group ID for fb-contrib is com.mebigfatguy.fb-contrib, and the artifact ID is fb-contrib. Eg:

~~~~
<plugin>
    <groupId>org.codehaus.mojo</groupId>
    <artifactId>findbugs-maven-plugin</artifactId>
    <version>3.0.4</version>
    <configuration>
        <plugins>
            <plugin>
                <groupId>com.mebigfatguy.fb-contrib</groupId>
                <artifactId>fb-contrib</artifactId>
                <version>7.4.6</version>
            </plugin>
        </plugins>
    </configuration>
    <executions>
        <execution>
            <goals>
                <goal>check</goal>
            </goals>
        </execution>
    </executions>
</plugin>
~~~~

Or to include the fb-contrib detectors when checking your project with Spotbugs, you can use the [SpotBugs Maven plugin](https://github.com/spotbugs/spotbugs-maven-plugin) which is a fork of findbugs maven plugin to provide spotbugs integration.
The group ID for sb-contrib is com.mebigfatguy.sb-contrib, and the artifact ID is sb-contrib. Eg:

~~~~
<plugin>
    <groupId>com.github.spotbugs</groupId>
    <artifactId>spotbugs-maven-plugin</artifactId>
    <version>3.1.12</version>
    <configuration>
        <plugins>
            <plugin>
                <groupId>com.mebigfatguy.sb-contrib</groupId>
                <artifactId>sb-contrib</artifactId>
                <version>7.4.6</version>
            </plugin>
        </plugins>
    </configuration>
    <executions>
        <execution>
            <goals>
                <goal>check</goal>
            </goals>
        </execution>
    </executions>
</plugin>
~~~~


## Usage - Gradle

~~~~
apply plugin: 'findbugs'

dependencies {
    // We need to manually set this first, or the plugin is not loaded
    findbugs 'com.google.code.findbugs:findbugs:3.0.0'
    findbugs configurations.findbugsPlugins.dependencies

    // To keep everything tidy, we set these apart
    findbugsPlugins 'com.mebigfatguy.fb-contrib:fb-contrib:7.4.5'
}

task findbugs(type: FindBugs) {
   // Add all your config here ...

   pluginClasspath = project.configurations.findbugsPlugins
}
~~~~
## Contributing
Once you have the dev environment set up, feel free to make changes and pull requests.
Any edits are much appreciated, from finding typos, to adding examples in the [messages](https://github.com/mebigfatguy/fb-contrib/blob/master/etc/messages.xml), to creating new detectors, all help is welcome.

External guides for making detectors:
- https://www.ibm.com/developerworks/library/j-findbug2/
- http://kjlubick.github.io/blog/post/1?building-my-first-findbugs-detector

Misc references about bytecode:
- http://www.jroller.com/dbrosius/entry/the_jvm_class_file_format

For making detectors, it best to make several test cases, like those in the [sample directory](https://github.com/mebigfatguy/fb-contrib/tree/master/samples).  Even better is if you can comment where you expect bug markers to appear and why, like [this](https://github.com/mebigfatguy/fb-contrib/blob/717f757d69c098e1baf786d3e7c03efacf2bbfaf/samples/HES_Sample.java#L313).

In your pull request, give an overview of your changes along with the related commits.

> If you are not up for contributing code but notice a common problem with some third party library, or general purpose pattern, please add an issue too. We always like new ideas.


Often available on #fb-contrib on freenode.net for conversation.
