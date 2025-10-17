@rem
@rem Copyright 2015 the original author or authors.
@rem
@rem Licensed under the Apache License, Version 2.0 (the "License");
@rem you may not use this file except in compliance with the License.
@rem You may obtain a copy of the License at
@rem
@rem      https://www.apache.org/licenses/LICENSE-2.0
@rem
@rem Unless required by applicable law or agreed to in writing, software
@rem distributed under the License is distributed on an "AS IS" BASIS,
@rem WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
@rem See the License for the specific language governing permissions and
@rem limitations under the License.
@rem
@rem SPDX-License-Identifier: Apache-2.0
@rem

@if "%DEBUG%"=="" @echo off
@rem ##########################################################################
@rem
@rem  Gradle startup script for Windows
@rem
@rem ##########################################################################

@rem Set local scope for the variables with windows NT shell
if "%OS%"=="Windows_NT" setlocal

set DIRNAME=%~dp0
if "%DIRNAME%"=="" set DIRNAME=.
@rem This is normally unused
set APP_BASE_NAME=%~n0
set APP_HOME=%DIRNAME%

@rem Resolve any "." and ".." in APP_HOME to make it shorter.
for %%i in ("%APP_HOME%") do set APP_HOME=%%~fi

@rem Add default JVM options here. You can also use JAVA_OPTS and GRADLE_OPTS to pass JVM options to this script.
set DEFAULT_JVM_OPTS="-Xmx64m" "-Xms64m"

@rem Find java.exe
if defined JAVA_HOME goto findJavaFromJavaHome

set JAVA_EXE=java.exe
%JAVA_EXE% -version >NUL 2>&1
if %ERRORLEVEL% equ 0 goto execute

echo. 1>&2
echo ERROR: JAVA_HOME is not set and no 'java' command could be found in your PATH. 1>&2
echo. 1>&2
echo Please set the JAVA_HOME variable in your environment to match the 1>&2
echo location of your Java installation. 1>&2

goto fail

:findJavaFromJavaHome
set JAVA_HOME=%JAVA_HOME:"=%
set JAVA_EXE=%JAVA_HOME%/bin/java.exe

if exist "%JAVA_EXE%" goto execute

echo. 1>&2
echo ERROR: JAVA_HOME is set to an invalid directory: %JAVA_HOME% 1>&2
echo. 1>&2
echo Please set the JAVA_HOME variable in your environment to match the 1>&2
echo location of your Java installation. 1>&2

goto fail

:execute
@rem Setup the command line


set WRAPPER_JAR=%APP_HOME%\gradle\wrapper\gradle-wrapper.jar
if "%GRADLE_WRAPPER_URL%"=="" (
    set "WRAPPER_URL=https://github.com/gradle/gradle/raw/v8.7.0/gradle/wrapper/gradle-wrapper.jar"
) else (
    set "WRAPPER_URL=%GRADLE_WRAPPER_URL%"
)
if "%GRADLE_WRAPPER_SHA256%"=="" (
    set "WRAPPER_SHA256=cb0da6751c2b753a16ac168bb354870ebb1e162e9083f116729cec9c781156b8"
) else (
    set "WRAPPER_SHA256=%GRADLE_WRAPPER_SHA256%"
)

if not exist "%WRAPPER_JAR%" (
    call :downloadWrapperJar
    if %ERRORLEVEL% neq 0 goto fail
)
set CLASSPATH=%WRAPPER_JAR%

@rem Execute Gradle
"%JAVA_EXE%" %DEFAULT_JVM_OPTS% %JAVA_OPTS% %GRADLE_OPTS% "-Dorg.gradle.appname=%APP_BASE_NAME%" -classpath "%CLASSPATH%" org.gradle.wrapper.GradleWrapperMain %*

:downloadWrapperJar
setlocal
set "TMP_JAR=%WRAPPER_JAR%.tmp"
for %%I in ("%TMP_JAR%") do set "TMP_DIR=%%~dpI"
if not exist "%TMP_DIR%" mkdir "%TMP_DIR%" >NUL
where powershell >NUL 2>&1
if %ERRORLEVEL% neq 0 (
    echo ERROR: Gradle wrapper jar missing and PowerShell is not available. 1>&2
    endlocal
    exit /b 1
)
powershell -NoProfile -Command "try { $ErrorActionPreference = 'Stop'; $uri = $env:WRAPPER_URL; $tmp = $env:TMP_JAR; $sha = $env:WRAPPER_SHA256; Invoke-WebRequest -Uri $uri -OutFile $tmp -UseBasicParsing; if ($sha) { $actual = (Get-FileHash -Path $tmp -Algorithm SHA256).Hash.ToLower(); if ($actual -ne $sha.ToLower()) { throw ('Checksum mismatch: expected ' + $sha + ' but got ' + $actual) } } } catch { Write-Error $_; exit 1 }"
if %ERRORLEVEL% neq 0 (
    if exist "%TMP_JAR%" del "%TMP_JAR%"
    endlocal
    exit /b 1
)
move /Y "%TMP_JAR%" "%WRAPPER_JAR%" >NUL
endlocal
exit /b 0

:end
@rem End local scope for the variables with windows NT shell
if %ERRORLEVEL% equ 0 goto mainEnd

:fail
rem Set variable GRADLE_EXIT_CONSOLE if you need the _script_ return code instead of
rem the _cmd.exe /c_ return code!
set EXIT_CODE=%ERRORLEVEL%
if %EXIT_CODE% equ 0 set EXIT_CODE=1
if not ""=="%GRADLE_EXIT_CONSOLE%" exit %EXIT_CODE%
exit /b %EXIT_CODE%

:mainEnd
if "%OS%"=="Windows_NT" endlocal

:omega
