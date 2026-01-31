@echo off

REM FlowReplay启动脚本 (Windows)

set JAR_FILE=flowreplay-cli\target\flowreplay-cli-1.0.0-SNAPSHOT-jar-with-dependencies.jar

if not exist "%JAR_FILE%" (
    echo 错误: 找不到jar文件，请先运行 mvn clean package
    exit /b 1
)

java -jar "%JAR_FILE%" %*
