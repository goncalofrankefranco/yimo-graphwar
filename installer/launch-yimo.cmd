@echo off
setlocal
set "BASE=%~dp0"
set "JAVA=%BASE%runtime\bin\javaw.exe"
if not exist "%JAVA%" set "JAVA=javaw.exe"
start "YIMO Graphwar" "%JAVA%" -Xms64m -Xmx256m -Dfile.encoding=UTF-8 -jar "%BASE%YIMO-Graphwar-2.0.0.jar" --config "%BASE%yimo.properties" %*
endlocal
