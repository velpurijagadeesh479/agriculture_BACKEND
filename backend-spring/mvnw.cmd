@REM ----------------------------------------------------------------------------
@REM Licensed to the Apache Software Foundation (ASF) under one
@REM or more contributor license agreements.  See the NOTICE file
@REM distributed with this work for additional information
@REM regarding copyright ownership.  The ASF licenses this file
@REM to you under the Apache License, Version 2.0 (the
@REM "License"); you may not use this file except in compliance
@REM with the License.  You may obtain a copy of the License at
@REM
@REM    http://www.apache.org/licenses/LICENSE-2.0
@REM
@REM Unless required by applicable law or agreed to in writing,
@REM software distributed under the License is distributed on an
@REM "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
@REM KIND, either express or implied.  See the License for the
@REM specific language governing permissions and limitations
@REM under the License.
@REM ----------------------------------------------------------------------------

@REM ----------------------------------------------------------------------------
@REM Maven Start Up Batch script
@REM ----------------------------------------------------------------------------

@if "%MAVEN_BATCH_ECHO%" == "on"  echo %MAVEN_BATCH_ECHO%

@setlocal

@set ERROR_CODE=0

@REM ==== START VALIDATION ====
@if "%JAVA_HOME%" == "" (
    set "JAVA_HOME=C:\Program Files\Java\jdk-25.0.2"
)

@if not "%JAVA_HOME%" == "" goto OkJHome

@set "JAVA_EXE=java"
@%JAVA_EXE% -version >NUL 2>&1
@if %ERRORLEVEL% == 0 goto init

@echo.
@echo ERROR: JAVA_HOME not found and 'java' command is not in PATH.
@echo.
@goto error

:OkJHome
@set "JAVA_EXE=%JAVA_HOME%\bin\java.exe"

@if exist "%JAVA_EXE%" goto init

@echo.
@echo ERROR: JAVA_HOME is set to an invalid directory.
@echo JAVA_HOME = "%JAVA_HOME%"
@goto error

:init
@set MAVEN_PROJECTBASEDIR=%~dp0
@set MAVEN_PROJECTBASEDIR=%MAVEN_PROJECTBASEDIR:~0,-1%

@set WRAPPER_JAR="%MAVEN_PROJECTBASEDIR%\.mvn\wrapper\maven-wrapper.jar"
@set WRAPPER_LAUNCHER=org.apache.maven.wrapper.MavenWrapperMain

:run
@set MAVEN_OPTS=%MAVEN_OPTS% -XX:+TieredCompilation -XX:TieredStopAtLevel=1
@"%JAVA_EXE%" %MAVEN_OPTS% -classpath %WRAPPER_JAR% %WRAPPER_LAUNCHER% "-Dmaven.multiModuleProjectDirectory=%MAVEN_PROJECTBASEDIR%" %*

:error
@set ERROR_CODE=1

:end
@setlocal DisableDelayedExpansion
@exit /B %ERROR_CODE%
