# Windows installer source

`install.cmd` and `install.ps1` are the complete installation logic used by
the self-extracting `Graphwar-Setup.exe`. The installer expands a bundled
Java runtime and the three game/server jars into `%LOCALAPPDATA%\Graphwar`,
then creates a Start menu shortcut for the client.

The generated installer is kept as a distribution artifact rather than in
the source tree because it contains the bundled runtime and is rebuilt from
these scripts and the project jars.
