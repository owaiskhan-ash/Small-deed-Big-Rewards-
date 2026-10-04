@rem
@rem Gradle start-up script for Windows.
@rem Pins the build to the distribution declared in
@rem gradle\wrapper\gradle-wrapper.properties.
@rem

@if "%DEBUG%"=="" @echo off
@rem Set local scope for the variables with windows NT shell
if "%OS%"=="Windows_NT" setlocal

set DIRNAME=%~dp0
if "%DIRNAME%"=="" set DIRNAME=.
@rem This is normally unused
set APP_BASE_NAME=%~n0
set APP_HOME=%DIRNAME%

set DEFAULT_JVM_OPTS="-Xmx64m" "-Xms64m"

@rem --- Locate a JDK -------------------------------------------------------
if defined JAVA_HOME goto findJavaFromJavaHome

set JAVA_EXE=java.exe
%JAVA_EXE% -version >NUL 2>&1
if %ERRORLEVEL% equ 0 goto execute

echo ERROR: JAVA_HOME is not set and no 'java' command could be found in PATH.
echo        Install JDK 17 or newer (AGP 9.1.1 requires it).
exit /b 1

:findJavaFromJavaHome
set JAVA_HOME=%JAVA_HOME:"=%
set JAVA_EXE=%JAVA_HOME%\bin\java.exe
if exist "%JAVA_EXE%" goto execute

echo ERROR: JAVA_HOME is set to an invalid directory: %JAVA_HOME%
exit /b 1

:execute
@rem --- Wrapper jar --------------------------------------------------------
set WRAPPER_JAR=%APP_HOME%gradle\wrapper\gradle-wrapper.jar
if not exist "%WRAPPER_JAR%" (
    echo ERROR: gradle\wrapper\gradle-wrapper.jar is missing.
    echo.
    echo This checkout does not carry the wrapper jar. Regenerate it once on any
    echo machine that already has Gradle or Android Studio installed:
    echo.
    echo     gradle wrapper --gradle-version 9.3.1 --distribution-type bin
    echo.
    echo The pinned distribution is declared in
    echo gradle\wrapper\gradle-wrapper.properties
    echo ^(AGP 9.1.1 needs Gradle ^>= 9.3.1 and JDK 17^).
    exit /b 1
)

set CLASSPATH=%WRAPPER_JAR%

@rem --- Run ----------------------------------------------------------------
"%JAVA_EXE%" ^
    %DEFAULT_JVM_OPTS% ^
    %JAVA_OPTS% ^
    %GRADLE_OPTS% ^
    "-Dorg.gradle.appname=%APP_BASE_NAME%" ^
    -classpath "%CLASSPATH%" ^
    org.gradle.wrapper.GradleWrapperMain ^
    %*

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
exit /b %EXIT_CODE%
