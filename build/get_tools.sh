#!/bin/bash
# Downloads the two real applications used for every file in this repo.
set -e
cd "$(dirname "$0")/.."
mkdir -p tools
[ -f tools/logisim-generic-2.7.1.jar ] || curl -sSL -o tools/logisim-generic-2.7.1.jar \
  "https://sourceforge.net/projects/circuit/files/2.7.x/2.7.1/logisim-generic-2.7.1.jar/download"
[ -f tools/Mars4_5.jar ] || curl -sSL -o tools/Mars4_5.jar \
  "https://github.com/dpetersanderson/MARS/releases/download/v.4.5.1/Mars4_5.jar"
sha256sum -c <<'EOF'
362a78c12ad18c203fed868872c4a01cd9c12141379d92e892bbe2c37e627bc2  tools/logisim-generic-2.7.1.jar
EOF
ls -la tools/*.jar
