#!/bin/bash

base=`dirname $0`

java -cp "$base/cfnassist.jar:$base/conf:$base/lib/*" tw.com.commandline.Main $*
