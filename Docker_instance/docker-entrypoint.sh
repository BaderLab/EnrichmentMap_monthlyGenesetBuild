#!/bin/bash

# The GeneSetTools jar is NOT rebuilt here any more. It used to be rebuilt with
# ant every time the container started, and if that failed the build carried
# on silently with the old jar (it hadn't actually been rebuilt since 2024).
# Build it as an explicit step whenever the Java code changes instead - see
# GeneSetTools/build_jar.sh and Docker_instance/README.md.

exec "$@"
