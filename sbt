#!/usr/bin/env sh
# sbt runner script: Zero-dependency, self-contained sbt launcher
# Place this script in your repository and use it to run sbt without installation.

# Default sbt version if not specified
SBT_VERSION=${SBT_VERSION:-"1.10.5"}

# Directory for sbt cache and launcher jar
SBT_DIR="${HOME}/.sbt"
SBT_LAUNCH_JAR="${SBT_DIR}/sbt-launch-${SBT_VERSION}.jar"

# Create sbt directory if it doesn't exist
mkdir -p "${SBT_DIR}"

# Download the sbt-launch jar if it is not already cached
if [ ! -f "${SBT_LAUNCH_JAR}" ]; then
  echo "Downloading sbt-launch-${SBT_VERSION}.jar..."
  curl -sL -o "${SBT_LAUNCH_JAR}" "https://repo1.maven.org/maven2/org/scala-sbt/sbt-launch/${SBT_VERSION}/sbt-launch-${SBT_VERSION}.jar"
  if [ $? -ne 0 ]; then
    echo "Failed to download sbt-launch-${SBT_VERSION}.jar. Please check your internet connection or the sbt version."
    exit 1
  fi
fi

# Set Java options (optional, adjust as needed)
JAVA_OPTS="${JAVA_OPTS:-"-Xms512M -Xmx1024M"}"

# Execute sbt
exec java ${JAVA_OPTS} -jar "${SBT_LAUNCH_JAR}" "$@"
