How To Build
============

This document explains how to build the Number Place Breaker .apk file.

Short Description
-----------------

If you are experienced with Git and Android, OpenCV, and SAT4J development, the following information may be enough:

* The contents of the NumberPlaceBreaker directory is an Eclipse project.
* The project's target is set to Android 4.0.3 (API 15).
* You need to put SAT4J Core library files (*.jar files) in the project's "libs" directory.
* You also need to add OpenCV Android SDK project in your workspace and put it into the Java Build Path.

Otherwise, please keep reading.

Detailed Description
--------------------

Rest of this document is a longer detailed version of the description.

Read below for a detailed step-by-step instructions.  Follow the steps in order will create a suitable environment for building Number Place Breaker.

Prerequisits
------------

The following is a set of basic prerequisits:

* A stable internet connection is required for downloading files.
* You need an Eclipse installed and properly set up for Android development.
* You need git tools, both Eclipse plug-in for git (EGit) and a standalone (i.e. command-line) git tool.

Please refer to appropriate documents (e.g., those available on the Internet) if you are unsure how to set up.

An additional not-so-usual requisit is that you need to set up your Android SDK to contain files both for API-14 (Android 4.0) and for API-15 (Android 4.0.3).  You need two targets because the Number Place Breaker itself currently targets API-15, while OpenCV for Android 2.4.7.1 targets API-14.  (You can change the target later, if you want.)

Use the following steps to ensure API-14 and API-15 files are ready in your Android SDK:

1. Start the Android SDK Manager.
2. Make sure "SDK Platform" package under "Android 4.0.3 (API 15)" is "Installed".  Otherwise, check the check box for it.
3. Make sure "SDK Platform" package under "Android 4.0 (API 14) is "Installed".  Otherwise, check the check box for it.
4. If you have checked either or both of the above two check boxes (in step 2 or 3), click on the button "Install X packages..." where "X" is a number.

Download Files from Github
--------------------------

Clone (download) the NumberPlace repository on Github using a standalone (i.e., something that you can use when Eclipse is not running) git tool.  You can't use EGit (Eclipse plug-in for Git) for the purpose, because we want the root directory in the repository to be the workspace directory of Eclipse, where Eclipse puts .metadata, and it is not easy to use EGit to set do so.

The following steps assumes you are using a command-line git, but a standalone GUI git tool (e.g., Tortoise Git for Windows) should also works.

1. cd to a directory that you want to create the Eclipse workspace for the Number Place Breaker.
2. Use the following command to clone (download) the repository:
    * git clone https://github.com/AlissaSabre/NumberPlace.git
    * You should run this command when Eclipse is not running.
3. The directory "NumberPlace" created by the above git command will be the Eclipse workspace directory.  (You can rename it now if you want.)

Download OpenCV for Android SDK
-------------------------------

1. Download the zip file of [OpenCV for Android SDK 2.4.7.1] (https://sourceforge.net/projects/opencvlibrary/files/opencv-android/2.4.7/OpenCV-2.4.7.1-android-sdk.zip/download).
    * The version number of the OpenCV SDK (2.4.7.1) is embedded in several configuration files.  If you are to use other version of the OpenCV, you need to modify them properly.
2. Unzip the downloaded file in any directory outside of the workspace directory.
3. Copy the directory OpenCV-2.4.7.1-android-sdk/sdk/java in the zip file as well as all of its contents into the workspace directory renaming it to "OpenCV Library - 2.4.7.1".
    * The enclosing double quotation marks are not a part of the name.
    * Note that this directory name "OpenCV Library - 2.4.7.1" is a must for the following procedure to work.

After the above steps, your workspace directory will look like:

	NumberPlace/
	    README.md
	    ReleaseNotes.txt
	      ...
	    NumberPlaceBreaker/
		libs/
		res/
		src/
		.classpath
		.project
		AndroidManifest.xml
		  ...
	    OpenCV Library - 2.4.7.1/		<- Extracted from zip and renamed from "java".
		.settings/
		gen/
		res/
		src/
		.classpath
		.project
		AndroidManifest.xml
		  ...
	    .git/

Download and copy the SAT4J library file
----------------------------------------

Go to the SAT4J web site (http://sat4j.org) and download the zipped SAT4J Core library files.  As of this writing, the latest version is sat4j-core-v20130525.zip dated 2013-05-25 and is available at [a download page on OW2] (http://forge.ow2.org/project/download.php?group_id=228)

Unzip the downloaded file, and copy the files "org.sat4j.core.jar" into the "libs" directory of the "NumberPlaceBreaker" project directory.

Import to Eclipse
-----------------

1. Start Eclipse.  Choose the "NumberPlace" created above as the workspace directory.
    The workspace appears empty at first.  It's normal.
2. From Eclipse [File] menu, choose [Import...].
3. On the [Select] page of the import wizard, choose [Git]|[Projects From Git], then issue [Next>].
4. On the [Select Repository Source] page of the wizard, choose [Local], then issue [Next>].
5. On the [Select a Git Repository] page of the wizard, issue [Add...].
6. On the [Add Git Repositories] window, [Search and select Git repositories on your local file system] page, put the full path to the workspace directory in the [Directory:] (using [Browse...] and [Search] button if needed), and issue [Finish].
7. On the [Select a Git Repository] page of the wizard, again, make sure that, you see a full path to the workspace directory in the list, and issue [Next>].
8. On the [Select a wizard to use for importing projects] page of the wizard, choose [Import existing projects] in [Wizard for project import] and issue [Next>].
9. On the [Import Projects] page of the wizard, make sure you see two projects, "NumberPlaceBreaker" and "OpenCV Library - 2.4.7.1", in the list and both projects' check boxes are checked, then issue [Finish].

Then, wait for a while until all the files are compiled.  You may clean and refresh projects several times.






