#!/usr/bin/env sh
# Compile and run with only a JDK (21+) and the bundled MySQL driver: no Maven needed.
set -e
cd "$(dirname "$0")"
rm -rf out/run && mkdir -p out/run
javac --release 21 -d out/run -cp lib/mysql-connector-j-8.3.0.jar $(find src/main/java -name '*.java')
cp -r src/main/resources/* out/run/
java -cp "out/run:lib/mysql-connector-j-8.3.0.jar" com.arms.App
