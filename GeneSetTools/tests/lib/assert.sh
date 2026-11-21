#!/bin/bash
# Small assertion helpers for the shell tests (works with bash 3.2+).
# Each assert_* prints FAIL with a message when it fails and is counted;
# call finish_tests at the end to print a summary and get the exit status.

ASSERTIONS=0
FAILURES=0
CURRENT_TEST=""

start_test() {
	CURRENT_TEST="$1"
	echo "--- $1"
}

_fail() {
	FAILURES=$((FAILURES + 1))
	echo "  FAIL [$CURRENT_TEST]: $*"
}

_ok() {
	ASSERTIONS=$((ASSERTIONS + 1))
}

assert_eq() { # expected actual message
	ASSERTIONS=$((ASSERTIONS + 1))
	if [ "$1" != "$2" ]; then
		_fail "$3 (expected '$1', got '$2')"
	fi
}

assert_true() { # message command...
	local msg="$1"; shift
	ASSERTIONS=$((ASSERTIONS + 1))
	if ! "$@" ; then
		_fail "$msg"
	fi
}

assert_file_exists() {
	ASSERTIONS=$((ASSERTIONS + 1))
	[ -e "$1" ] || _fail "${2:-expected file to exist}: $1"
}

assert_file_missing() {
	ASSERTIONS=$((ASSERTIONS + 1))
	[ ! -e "$1" ] || _fail "${2:-expected file not to exist}: $1"
}

assert_file_nonempty() {
	ASSERTIONS=$((ASSERTIONS + 1))
	[ -s "$1" ] || _fail "${2:-expected non-empty file}: $1"
}

assert_contains() { # file fixed-string message
	ASSERTIONS=$((ASSERTIONS + 1))
	if [ ! -e "$1" ]; then
		_fail "${3:-expected '$2' in file} (file missing: $1)"
	elif ! grep -qF -- "$2" "$1" ; then
		_fail "${3:-expected '$2' in file}: $1"
	fi
}

assert_not_contains() { # file fixed-string message
	ASSERTIONS=$((ASSERTIONS + 1))
	if [ -e "$1" ] && grep -qF -- "$2" "$1" ; then
		_fail "${3:-did not expect '$2' in file}: $1 -> $(grep -F -- "$2" "$1" | head -3)"
	fi
}

assert_matches() { # file regex message
	ASSERTIONS=$((ASSERTIONS + 1))
	if [ ! -e "$1" ] || ! grep -qE -- "$2" "$1" ; then
		_fail "${3:-expected /$2/ in file}: $1"
	fi
}

assert_no_match() { # file regex message
	ASSERTIONS=$((ASSERTIONS + 1))
	if [ -e "$1" ] && grep -qE -- "$2" "$1" ; then
		_fail "${3:-did not expect /$2/ in file}: $1 -> $(grep -E -- "$2" "$1" | head -3)"
	fi
}

finish_tests() {
	echo
	if [ $FAILURES -eq 0 ]; then
		echo "PASSED: $ASSERTIONS assertions"
		return 0
	fi
	echo "FAILED: $FAILURES of $ASSERTIONS assertions failed"
	return 1
}
