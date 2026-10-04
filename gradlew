#!/bin/sh

#
# Gradle start-up script for POSIX environments.
#
# Pins the build to the distribution declared in gradle/wrapper/
# gradle-wrapper.properties so every machine (and CI) uses the same Gradle.
#

# Resolve the directory this script lives in, following symlinks.
PRG="$0"
while [ -h "$PRG" ] ; do
    ls=$(ls -ld "$PRG")
    link=$(expr "$ls" : '.*-> \(.*\)$')
    if expr "$link" : '/.*' > /dev/null; then
        PRG="$link"
    else
        PRG=$(dirname "$PRG")/"$link"
    fi
done
APP_HOME=$(cd "$(dirname "$PRG")" > /dev/null && pwd -P) || exit

APP_NAME="Gradle"
APP_BASE_NAME=$(basename "$0")

DEFAULT_JVM_OPTS='"-Xmx64m" "-Xms64m"'

# --- Locate a JDK -----------------------------------------------------------
if [ -n "$JAVA_HOME" ] ; then
    if [ -x "$JAVA_HOME/jre/sh/java" ] ; then
        JAVACMD="$JAVA_HOME/jre/sh/java"
    else
        JAVACMD="$JAVA_HOME/bin/java"
    fi
    if [ ! -x "$JAVACMD" ] ; then
        echo "ERROR: JAVA_HOME is set to an invalid directory: $JAVA_HOME" >&2
        exit 1
    fi
else
    JAVACMD="java"
    if ! command -v java > /dev/null 2>&1 ; then
        echo "ERROR: JAVA_HOME is not set and 'java' is not on PATH." >&2
        echo "       Install JDK 17 or newer (AGP 9.1.1 requires it)." >&2
        exit 1
    fi
fi

# --- Wrapper jar ------------------------------------------------------------
# gradle-wrapper.jar is a build artefact of the wrapper itself and is not
# regenerated here. If it is absent (for example after a clone that excluded
# binaries) fail with an actionable message instead of a confusing JVM error.
WRAPPER_JAR="$APP_HOME/gradle/wrapper/gradle-wrapper.jar"
if [ ! -f "$WRAPPER_JAR" ] ; then
    cat >&2 <<EOF
ERROR: gradle/wrapper/gradle-wrapper.jar is missing.

This checkout does not carry the wrapper jar. Regenerate it once on any
machine that already has Gradle or Android Studio installed:

    gradle wrapper --gradle-version 9.3.1 --distribution-type bin

or, in Android Studio: File > Sync Project with Gradle Files, then
Tools > Gradle > 'Generate Gradle wrapper'.

The pinned distribution is declared in gradle/wrapper/gradle-wrapper.properties
(AGP 9.1.1 needs Gradle >= 9.3.1 and JDK 17).
EOF
    exit 1
fi

CLASSPATH="$WRAPPER_JAR"

# --- Run --------------------------------------------------------------------
# shellcheck disable=SC2086
exec "$JAVACMD" \
    $DEFAULT_JVM_OPTS \
    $JAVA_OPTS \
    $GRADLE_OPTS \
    "-Dorg.gradle.appname=$APP_BASE_NAME" \
    -classpath "$CLASSPATH" \
    org.gradle.wrapper.GradleWrapperMain \
    "$@"
