@echo off
setlocal
:: 短路径，没有空格，完美避开mvn.cmd空格解析bug
set "JAVA_HOME=C:\PROGRA~1\Java\jdk1.8.0_202"

:: 先校验jdk8是否存在
if not exist "%JAVA_HOME%\bin\java.exe" (
    echo 错误：找不到JDK8，请检查路径！
    pause
    exit /b
)

echo 打包使用的JAVA_HOME：%JAVA_HOME%
"%JAVA_HOME%\bin\java.exe" -version
echo ---------------------------
h:
cd h:\traeproject\ceshisikongapiAI
mvn clean package -DskipTests
echo 打包结束
pause