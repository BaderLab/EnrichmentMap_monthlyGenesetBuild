#!/bin/bash
#
# Compile GeneSetTools (src/) and the Java tests, then run them.
# Needs a JDK (11 or later). Compiles for Java 11 like the build container.

HERE=$(cd "$(dirname "$0")" && pwd)
TOOLS=$(cd "$HERE/../.." && pwd)

if ! command -v javac > /dev/null ; then
	echo "ERROR: javac not found - the Java tests need a JDK"
	exit 1
fi

CLASSES=$(mktemp -d "${TMPDIR:-/tmp}/genesettools_classes.XXXXXX")
trap 'rm -rf "$CLASSES"' EXIT

echo "compiling GeneSetTools and tests..."
if ! javac --release 11 -nowarn -encoding UTF-8 -d "$CLASSES" -cp "$TOOLS/lib/*" \
		"$TOOLS"/src/*.java "$HERE"/GenesetToolsTests.java 2>&1 | grep -v "^Note:" ; then
	: # grep found nothing to print
fi
if [ ! -f "$CLASSES/GenesetToolsTests.class" ] || [ ! -f "$CLASSES/GenesetTools.class" ]; then
	echo "ERROR: compilation failed"
	exit 1
fi

java -cp "$CLASSES:$TOOLS/lib/*" GenesetToolsTests
