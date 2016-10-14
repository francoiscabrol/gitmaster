GitMaster
=========

GitMaster is a small command line tool with two simple goals:
- Offer an overview of all your git repositories in any directory as your $HOME/WORKSPACE.
- Manage a set of git repositories with a third git "master repo". For example, you have a project with several services or subprojects, Each project is store in a different repository. You want to allows any developer to clone the master repository and then clone all repositories. Then you want to allow the developer to check or pull every repositories at the same time. This tool do that.

ALPHA VERSION

# Install it
Requirement: You need java installed.

Copy the gmaster file in your bin path. For example under /usr/bin/
This command do that for you: `curl -Lo /usr/bin/gmaster https://github.com/francoiscabrol/gitmaster/releases/download/v0.5/gmaster`
Then you will be able to run it direcly in your terminal.
`gmaster help`

You can try to run `gmaster` in your Workspace to check the status of your repositories.

# Build it yourself

Requirement: You need sbt (simple build tool) installed.

Then run `sbt assembly` to build the gmaster executable.

