@echo off
setlocal
set "MAVEN_VERSION=3.9.11"
set "MAVEN_DIST_DIR=%USERPROFILE%\.m2\wrapper\dists\apache-maven-%MAVEN_VERSION%"
set "MAVEN_COMMAND=%MAVEN_DIST_DIR%\bin\mvn.cmd"

if exist "%MAVEN_COMMAND%" goto run_maven

where powershell.exe >nul 2>nul
if errorlevel 1 (
  echo ERROR: PowerShell is required to download the Maven distribution. 1>&2
  exit /b 1
)

echo Downloading Apache Maven %MAVEN_VERSION%...
set "MAVEN_ARCHIVE=%TEMP%\apache-maven-%MAVEN_VERSION%-%RANDOM%.zip"
powershell.exe -NoLogo -NoProfile -ExecutionPolicy Bypass -Command "$ErrorActionPreference='Stop'; [Net.ServicePointManager]::SecurityProtocol=[Net.SecurityProtocolType]::Tls12; New-Item -ItemType Directory -Force -Path '%USERPROFILE%\.m2\wrapper\dists' | Out-Null; Invoke-WebRequest -UseBasicParsing -Uri 'https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/3.9.11/apache-maven-3.9.11-bin.zip' -OutFile '%MAVEN_ARCHIVE%'; Expand-Archive -Force -Path '%MAVEN_ARCHIVE%' -DestinationPath '%USERPROFILE%\.m2\wrapper\dists'; Remove-Item -Force '%MAVEN_ARCHIVE%'"
if errorlevel 1 (
  echo ERROR: Maven download or extraction failed. Check the network connection. 1>&2
  exit /b 1
)

:run_maven
call "%MAVEN_COMMAND%" %*
exit /b %ERRORLEVEL%

