#!/bin/sh

set -eu

classes=build/local/classes
mkdir -p "$classes" build/local
javac -Xlint:deprecation -d "$classes" -sourcepath src/ -classpath "$classes" \
  src/Graphwar/*.java src/GraphServer/*.java src/GlobalServer/*.java src/RoomServer/*.java

jar cfe build/local/graphwar.jar Graphwar.Graphwar -C "$classes" GraphServer -C "$classes" Graphwar -C . rsc
jar cfe build/local/roomServer.jar RoomServer.RoomServer -C "$classes" GraphServer -C "$classes" RoomServer -C "$classes" Graphwar -C . rsc
jar cfe build/local/globalServer.jar GlobalServer.GlobalServer -C "$classes" GraphServer -C "$classes" GlobalServer -C . rsc

rm -rf "$classes"
