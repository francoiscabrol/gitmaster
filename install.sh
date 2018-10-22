#!/bin/bash
version=$(curl https://api.github.com/repos/francoiscabrol/gitmaster/releases/latest | grep tag_name | awk '{print $2}' | sed 's/[^0-9.?]*//g')
curl -Lo $GITMASTER_PATH/gmaster https://github.com/francoiscabrol/gitmaster/releases/download/$version/gmaster
chmod 755 $GITMASTER_PATH/gmaster

