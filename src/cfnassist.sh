#!/bin/bash

base=`dirname $0` 

java -cp "../lib/cfnassist.jar:$base/../conf:$base/../lib/*" tw.com.commandline.Main $*
