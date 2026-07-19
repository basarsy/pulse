@REM ----------------------------------------------------------------------------
@REM Licensed to the Apache Software Foundation (ASF) under one
@REM or more contributor license agreements.  See the NOTICE file
@REM distributed with this work for additional information
@REM regarding copyright ownership.  The ASF licenses this file
@REM to you under the Apache License, Version 2.0 (the
@REM "License"); you may not use this file except in compliance
@REM with the License.  You may obtain a copy of the License at
@REM
@REM    https://www.apache.org/licenses/LICENSE-2.0
@REM
@REM Unless required by applicable law or agreed to in writing,
@REM software distributed under the License is distributed on an
@REM "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
@REM KIND, either express or implied.  See the License for the
@REM specific language governing permissions and limitations
@REM under the License.
@REM ----------------------------------------------------------------------------

@REM ----------------------------------------------------------------------------
@REM Apache Maven Wrapper startup batch script, version 3.3.2
@REM ----------------------------------------------------------------------------

@echo off
@setlocal

set ERROR_CODE=0

@REM To isolate internal variables from possible post storage, we use two dynamic variables
set "EXEC_DIR=%~dp0"
set "W_DIR=%EXEC_DIR%"

:findBaseDir
if exist "%W_DIR%\.mvn" goto baseDirFound
set "W_DIR=%W_DIR%..\"
if "%W_DIR%"=="..\" goto baseDirNotFound
goto findBaseDir

:baseDirFound
set "MAVEN_PROJECTBASEDIR=%W_DIR%"
goto init

:baseDirNotFound
set "MAVEN_PROJECTBASEDIR=%EXEC_DIR%"

:init
set "MAVEN_CONFIG=%MAVEN_PROJECTBASEDIR%\.mvn"

@REM Find JAVA_HOME
if defined JAVA_HOME goto findJavaFromJavaHome

set "JAVACMD=java"
goto checkJavaCmd

:findJavaFromJavaHome
set "JAVACMD=%JAVA_HOME%\bin\java.exe"

if exist "%JAVACMD%" goto checkJavaCmd

echo Error: JAVA_HOME is set to an invalid directory. 1>&2
echo JAVA_HOME = "%JAVA_HOME%" 1>&2
echo Please set the JAVA_HOME variable in your environment to match the 1>&2
echo location of your Java installation. 1>&2
goto error

:checkJavaCmd
if exist "%JAVACMD%" goto runWrapper

:runWrapper
set "WRAPPER_JAR=%MAVEN_PROJECTBASEDIR%\.mvn\wrapper\maven-wrapper.jar"
set "WRAPPER_PROPERTIES=%MAVEN_PROJECTBASEDIR%\.mvn\wrapper\maven-wrapper.properties"

"%JAVACMD%" -Dmaven.multiModuleProjectDirectory="%MAVEN_PROJECTBASEDIR%" -classpath "%WRAPPER_JAR%" org.apache.maven.wrapper.MavenWrapperMain %*
if ERRORLEVEL 1 goto error
goto end

:error
set ERROR_CODE=1

:end
@endlocal & set ERROR_CODE=%ERROR_CODE%
cmd /C exit /B %ERROR_CODE%
