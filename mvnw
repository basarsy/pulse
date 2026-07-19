#!/bin/sh
# ----------------------------------------------------------------------------
# Apache Maven Wrapper startup batch script, version 3.3.2
# ----------------------------------------------------------------------------

# Provide a default set of JVM options
MAVEN_OPTS="-Xms256m -Xmx512m"

# Find base directory
PRG="$0"
while [ -h "$PRG" ]; do
  ls=`ls -ld "$PRG"`
  link=`expr "$ls" : '.*-> \(.*\)$'`
  if expr "$link" : '/.*' > /dev/null; then
    PRG="$link"
  else
    PRG=`dirname "$PRG"`/"$link"
  fi
done
SAVED=`pwd`
CDPATH=
export CDPATH
cd "`dirname "$PRG"`/" >/dev/null
MAVEN_PROJECTBASEDIR=`pwd`
cd "$SAVED" >/dev/null

JAVACMD="java"
if [ -n "$JAVA_HOME" ]; then
  if [ -x "$JAVA_HOME/bin/java" ]; then
    JAVACMD="$JAVA_HOME/bin/java"
  fi
fi

WRAPPER_JAR="$MAVEN_PROJECTBASEDIR/.mvn/wrapper/maven-wrapper.jar"
exec "$JAVACMD" $MAVEN_OPTS -classpath "$WRAPPER_JAR" org.apache.maven.wrapper.MavenWrapperMain "$@"
