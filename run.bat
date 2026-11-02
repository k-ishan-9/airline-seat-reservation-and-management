@echo off
rem Compile and run with only a JDK (21+) and the bundled MySQL driver: no Maven needed.
rem Uses JAVA_HOME if set, otherwise the java on PATH.
setlocal
cd /d "%~dp0"
set JAVA=java
set JAVAC=javac
if defined JAVA_HOME (
  set "JAVA=%JAVA_HOME%\bin\java"
  set "JAVAC=%JAVA_HOME%\bin\javac"
)
if exist out\run rmdir /s /q out\run
mkdir out\run
dir /s /b src\main\java\*.java > out\sources.txt
"%JAVAC%" --release 21 -d out\run -cp lib\mysql-connector-j-8.3.0.jar @out\sources.txt
if errorlevel 1 (echo Compilation failed. Make sure JDK 21 or newer is installed. & pause & exit /b 1)
xcopy /e /q /y src\main\resources out\run\ >nul
"%JAVA%" -cp "out\run;lib\mysql-connector-j-8.3.0.jar" com.arms.App
pause
