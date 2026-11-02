#!/usr/bin/env bash
# Compiles :core and runs its JUnit 4 tests WITHOUT Gradle downloading anything. For agent sandboxes where
# Maven Central and Gradle distributions are blocked. It uses the Kotlin compiler and JUnit jars that ship
# inside a local Gradle install (GRADLE_LIB, default /opt/gradle/lib; the JUnit jar may sit in a sibling
# versioned folder, so GRADLE_LIB_JUNIT can point there).
# The normal way, with network, is: ./gradlew -Pstride.coreOnly=true :core:test
# Note: the bundled compiler may be older than the one CI uses (gradle/libs.versions.toml), so keep core
# code free of brand-new syntax.
set -euo pipefail
root="$(cd "$(dirname "$0")/.." && pwd)"
lib="${GRADLE_LIB:-/opt/gradle/lib}"
junit="$(ls "${GRADLE_LIB_JUNIT:-$lib}"/junit-4*.jar "$lib"/../lib/junit-4*.jar /opt/gradle-*/lib/junit-4*.jar 2>/dev/null | head -1)"
hamcrest="$(ls "$lib"/hamcrest-core-*.jar /opt/gradle-*/lib/hamcrest-core-*.jar 2>/dev/null | head -1)"
stdlib="$(ls "$lib"/kotlin-stdlib-[0-9]*.jar | head -1)"
[ -n "$junit" ] && [ -n "$hamcrest" ] && [ -n "$stdlib" ] || { echo "Need junit, hamcrest and kotlin-stdlib jars under $lib"; exit 1; }
compiler_cp="$(ls "$lib"/*.jar | tr '\n' ':')"
out="${CORE_CHECK_OUT:-$root/build/core-offline}"
rm -rf "$out"
mkdir -p "$out/main" "$out/test"

kotlinc() { java -cp "$compiler_cp" org.jetbrains.kotlin.cli.jvm.K2JVMCompiler -no-stdlib -no-reflect -nowarn "$@"; }

echo "Compiling core main..."
kotlinc -cp "$stdlib" -d "$out/main" $(find "$root/core/src/main" -name '*.kt')
echo "Compiling core tests..."
kotlinc -cp "$stdlib:$out/main:$junit:$hamcrest" -d "$out/test" $(find "$root/core/src/test" -name '*.kt')

classes="$(cd "$out/test" && find . -name '*Test.class' | sed -e 's#^\./##' -e 's#\.class$##' -e 's#/#.#g' | sort)"
echo "Running $(wc -l <<< "$classes") test classes..."
java -cp "$out/main:$out/test:$stdlib:$junit:$hamcrest" org.junit.runner.JUnitCore $classes
