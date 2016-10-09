GitMaster
=========

GitMaster is a small command line tool with two simple goals:
- Offer an overview of all your git repositories in any directory as your $HOME/WORKSPACE.
- Manage a set of git repositories with a third git "master repo". For example, you have a project with several services or subprojects, Each project is store in a different repository. You want to allows any developer to clone the master repository and then clone all repositories. Then you want to allow the developer to check or pull every repositories at the same time. This tool do that.

ALPHA VERSION

Requirement: You need scala installed.
For macos: `brew install scala`
For archlinux: `pacman -Ss scala`
I have written the script with scala 2.11.8.

In the future, I would like to release GitMaster as a command line application packaged in a simple runnable file with sbt assembly.
But while it is under development, it's easier to hack it as a simple scala script file.
Copy the gmaster file in your bin path. For example under /usr/bin/
Then you will be able to run it direcly in your terminal.
`gmaster help`

Yu can try to run `gmaster` in your Workspace to check the status of your repositories.
