
version=$(curl https://api.github.com/repos/francoiscabrol/gitmaster/releases/latest | grep tag_name | awk '{print $2}' | sed 's/[^0-9.?]*//g')

sudo curl -Lo $GITMASTER_PATH https://github.com/francoiscabrol/gitmaster/releases/download/$version/gmaster

sudo chmod 755 $GITMASTER_PATH

