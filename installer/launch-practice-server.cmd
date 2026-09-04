@echo off
setlocal
set "BASE=%~dp0"
set "JAVA=%BASE%runtime\bin\java.exe"
if not exist "%JAVA%" set "JAVA=java.exe"
start "YIMO Practice Lobby" /MIN "%JAVA%" -Xms32m -Xmx128m -XX:+UseSerialGC -Djava.awt.headless=true -jar "%BASE%globalServer.jar" --config "%BASE%practice.properties"
start "YIMO Practice Rooms" /MIN "%JAVA%" -Xms32m -Xmx128m -XX:+UseSerialGC -Djava.awt.headless=true -jar "%BASE%roomServer.jar" --config "%BASE%practice.properties"
echo Practice servers started on 127.0.0.1. Keep both server windows open while testing.
endlocal
