#!/usr/bin/env bash

logger -st example "Starting build script"
rm -rf classes example
mkdir -p classes bin

set -e

logger -st example "Compiling Clojure sources"

clojure -M -e "(compile 'example.core)"

logger -st example "Clojure sources compiled - created $(du -sh classes | cut -f1) of class files"

logger -st example "Starting native image build"

mise exec -- native-image \
	-cp "$(clojure -Spath):classes" \
	-march=native \
	-H:Name=example \
	-H:+ReportExceptionStackTraces \
	-H:+UnlockExperimentalVMOptions \
	-H:-AddAllFileSystemProviders \
	--features=clj_easy.graal_build_time.InitClojureClasses \
	--enable-url-protocols=http,https \
	--initialize-at-build-time \
	--verbose \
	--no-fallback \
  example.core

logger -st example "Native image build completed - binary size is $(du -sh example | cut -f1)"
