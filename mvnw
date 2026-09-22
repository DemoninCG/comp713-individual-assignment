#!/bin/sh
set -eu

maven_version="3.9.11"
maven_dist_dir="${HOME}/.m2/wrapper/dists/apache-maven-${maven_version}"
maven_command="${maven_dist_dir}/bin/mvn"
distribution_url="https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/${maven_version}/apache-maven-${maven_version}-bin.zip"

if [ ! -x "${maven_command}" ]; then
  command -v curl >/dev/null 2>&1 || { echo "ERROR: curl is required to download Maven." >&2; exit 1; }
  command -v unzip >/dev/null 2>&1 || { echo "ERROR: unzip is required to extract Maven." >&2; exit 1; }
  mkdir -p "${HOME}/.m2/wrapper/dists"
  archive_path="${TMPDIR:-/tmp}/apache-maven-${maven_version}-$$.zip"
  echo "Downloading Apache Maven ${maven_version}..."
  curl -fsSL "${distribution_url}" -o "${archive_path}"
  unzip -q -o "${archive_path}" -d "${HOME}/.m2/wrapper/dists"
  rm -f "${archive_path}"
fi

exec "${maven_command}" "$@"

