GitMaster - Manage a set of git repositories
=========

GitMaster is a small command line tool with two simple goals:
- Offer an overview of all your git repositories in any directory as your $HOME/WORKSPACE.
- Manage a set of git repositories with a third git "master repo". For example, you have a project with several services or subprojects, Each project is store in a different repository. You want to allows any developer to clone the master repository and then clone all repositories. Then you want to allow the developer to check or pull every repositories at the same time. This tool do that.

Features
--------
`gmaster help` list the commands:

status  | Show the status of each repositories
fetch   | git fetch each repositories
init    | Clone all repositories defined in the .gitmaster file
pull    | git pull each repositories
dump    | Dump the list of repositories in the .gitmaster file
help    | Show this help

Several commands can be executed in the same run. 
For example, `gitmaster fetch status` is going to first fetch the last remote version and then display the status of each repository.

Install it
----------
Requirement: You need java installed.

Copy the gmaster file in your bin path. For example under /usr/bin/.
This command do that for you: `sudo curl -Lo /usr/bin/gmaster https://github.com/francoiscabrol/gitmaster/releases/download/0.7/gmaster`.
You will need to change the permissions with `sudo chmod -x /usr/bin/gmaster`.
Then you will be able to run it direcly in your terminal.
`gmaster help`

You can try to run `gmaster` in your Workspace to check the status of your repositories.

Build it yourself
-----------------

Requirement: You need sbt (simple build tool) installed.

Then run `sbt assembly` to build the gmaster executable.

Develop
----------

Requirement: You need sbt (simple build tool) installed.

Run it with `sbt run ARGUMENTS` like `sbt run help` or `sbt run --dir ~/path/of/repositories/ status`

