#!/bin/sh
# Regenerates docs/ui-spec/images from the real client.
#   1. builds the server and the client,
#   2. runs Capture.java: a server on port 5099 with a fresh users file, four bot players,
#      the JavaFX client driven through each screen (raw snapshots go to $WORK),
#   3. runs overlay.py: draws the numbered callouts and writes ../images/*.png (needs Google Chrome).
# Usage: sh docs/ui-spec/capture/capture.sh        (macOS; takes about a minute)
set -e
HERE="$(cd "$(dirname "$0")" && pwd)"
REPO="$(cd "$HERE/../../.." && pwd)"
WORK="${WORK:-$(mktemp -d)}"

cmake --build "$REPO/server/build" >/dev/null
(cd "$REPO/client" && sh mvnw -q compile)

FX="$HOME/.m2/repository/org/openjfx"
V=21.0.6
CLS=$(uname -m | sed 's/arm64/mac-aarch64/;s/x86_64/mac/')
MP="$FX/javafx-base/$V/javafx-base-$V-$CLS.jar:$FX/javafx-graphics/$V/javafx-graphics-$V-$CLS.jar:$FX/javafx-controls/$V/javafx-controls-$V-$CLS.jar:$FX/javafx-fxml/$V/javafx-fxml-$V-$CLS.jar"

mkdir -p "$WORK/classes"
javac -nowarn -d "$WORK/classes" --module-path "$MP" --add-modules javafx.controls,javafx.fxml \
      -cp "$REPO/client/target/classes" "$HERE/Capture.java"
# In-memory preferences: the run does not touch the saved server address or username.
java --module-path "$MP" --add-modules javafx.controls,javafx.fxml \
     -Djava.util.prefs.PreferencesFactory='Capture$MemPrefsFactory' \
     -cp "$WORK/classes:$REPO/client/target/classes" Capture "$REPO/server/build/server" "$WORK" 5099

python3 "$HERE/overlay.py" "$WORK" "$HERE/../images"
echo "raw snapshots and server log: $WORK"
