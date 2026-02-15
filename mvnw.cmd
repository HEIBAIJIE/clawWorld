@REM Maven Wrapper startup script for Windows
@echo off
@REM Set local scope for the variables with windows NT shell
if "%OS%"=="Windows_NT" setlocal

set MAVEN_CMD_LINE_ARGS=%*

@REM Find the project base dir
set MAVEN_PROJECTBASEDIR=%~dp0
IF NOT "%MAVEN_PROJECTBASEDIR%"=="" goto endDetectBaseDir

:endDetectBaseDir

set WRAPPER_JAR="%MAVEN_PROJECTBASEDIR%\.mvn\wrapper\maven-wrapper.jar"
set WRAPPER_LAUNCHER=org.apache.maven.wrapper.MavenWrapperMain

java -jar %WRAPPER_JAR% %MAVEN_CMD_LINE_ARGS%

:end
@REM End local scope for the variables with windows NT shell
if "%OS%"=="Windows_NT" endlocal
