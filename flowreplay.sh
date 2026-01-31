#!/bin/bash

# FlowReplay启动脚本

JAR_FILE="flowreplay-cli/target/flowreplay-cli-1.0.0-SNAPSHOT-jar-with-dependencies.jar"

if [ ! -f "$JAR_FILE" ]; then
    echo "错误: 找不到jar文件，请先运行 mvn clean package"
    exit 1
fi

java -jar "$JAR_FILE" "$@"
