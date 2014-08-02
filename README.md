fb-contrib
==========

a FindBugs plugin for doing static code analysis on java byte code. 
For information see http://fb-contrib.sf.net



Available on maven.org

       GroupId: com.mebigfatguy.fb-contrib
    ArtifactId: fb-contrib
       Version: 6.0.0


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


The master branch is the main branch of development currently targeting the new FindBugs 3 platform.


##Setting up for Development##
1. Download/install Eclipse, ideally 4.3 (Kepler) or newer.  The standard release (for Java) will work fine.
2. Download yank, the dependency manager. [Download](http://search.maven.org/#search%7Cgav%7C1%7Cg%3A%22com.mebigfatguy.yank%22%20AND%20a%3A%22yank%22).  `yank.jar` should go in your [ant](http://ant.apache.org/)/lib folder.  If you don't have Ant installed, that's okay, Eclipse has its own version located where ever you extracted Eclipse, e.g. `C:\eclipse\plugins\org.apache.ant_1.8.4.v201303080030\lib`
3. Clone this git repo.  [GitHub for Windows](https://windows.github.com/) or [GitHub for Mac](https://mac.github.com/) are good clients if you don't already have one.
4.  Open Eclipse.  File>Import and then choose "Existing projects into workspace", and find the fb-contrib folder you created in step 3.  Ignore any compile errors (for now).
5. Using git, clone the FindBugs repository using `git clone https://code.google.com/p/findbugs/`  You will only need the findbugs subfolder (the one that has README.txt in it).  You can delete the rest, if you wish.
6. Import this project into Eclipse as well.  You may wish to mark these files as read-only, so you modify the "correct" files.
7. In the fb-contrib project, find the `build.properties` file and modify the [findbugs.dir](https://github.com/mebigfatguy/fb-contrib/blob/717f757d69c098e1baf786d3e7c03efacf2bbfaf/build.properties#L13) property to where ever you have findbugs.  The jar will be "installed" to (findbugs.dir)\plugin.  If you are using FindBugs with Eclipse (and you extracted Eclipse to C:\\), you'll set this to something like `findbugs.dir=/eclipse/plugins/edu.umd.cs.findbugs.plugin.eclipse_3.0.0.20140706-2cfb468`
8. We don't want git to nag us about changes to the build.properties file, so run the following command in a shell: `git update-index --skip-worktree build.properties`
9. Finally, build fb-contrib by finding the [build.xml](https://github.com/mebigfatguy/fb-contrib/blob/717f757d69c098e1baf786d3e7c03efacf2bbfaf/build.xml) file in Eclipse, right-click it, and select Run As > Ant Build.  The dependencies needed should be downloaded to fb-contrib/lib and the fb-contrib-VERSION.jar should be built. 


##Contributing##
Once you have the dev environment set up, feel free to make changes and pull requests.
Any edits are much appreciated, from finding typos, to adding examples in the [messages](https://github.com/mebigfatguy/fb-contrib/blob/master/etc/messages.xml), to creating new detectors, all help is welcome.

External guides for making detectors:
- https://www.ibm.com/developerworks/library/j-findbug2/
- http://kjlubick.github.io/#/blog/post/1?building-my-first-findbugs-detector

For making detectors, it best to make several test cases, like those in the [sample directory](https://github.com/mebigfatguy/fb-contrib/tree/master/samples).  Even better is if you can comment where you expect bug markers to appear and why, like [this](https://github.com/mebigfatguy/fb-contrib/blob/717f757d69c098e1baf786d3e7c03efacf2bbfaf/samples/HES_Sample.java#L313).

In your pull request, give an overview of your changes along with the related commits.