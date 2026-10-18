@echo off
set "JAVA_HOME=C:\Program Files\Java\jdk-21.0.11"
set PATH=%JAVA_HOME%\bin;%PATH%
set "GRADLE_USER_HOME=E:\contruction_managment\.gradle-new"
call E:\contruction_managment\.tooling\gradle-9.3.1\bin\gradle.bat --no-daemon -Dorg.gradle.jvmargs= --max-workers=1 --console=plain assembleDebug
