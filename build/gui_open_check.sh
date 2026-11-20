#!/bin/bash
# Opens a .circ in the real Logisim 2.7.1 GUI on an X display ($DISPLAY), lists every
# window the process created (a load-error dialog would show up here), screenshots the
# screen and closes Logisim.
# usage: gui_open_check.sh file.circ screenshot.png
JAR="$(dirname "$0")/../tools/logisim-generic-2.7.1.jar"
LOG=$(mktemp)
java -jar "$JAR" -nosplash "$1" >"$LOG" 2>&1 &
PID=$!
for i in $(seq 1 40); do
  sleep 0.5
  W=$(xdotool search --pid $PID --name "^Logisim: " 2>/dev/null | head -1)
  [ -n "$W" ] && break
done
sleep 4
echo "file: $1"
echo "windows:"
for w in $(xdotool search --pid $PID --name "" 2>/dev/null); do
  n=$(xdotool getwindowname "$w" 2>/dev/null)
  [ -n "$n" ] && echo "  - $n"
done | sort -u
W=$(xdotool search --pid $PID --name "^Logisim: " | head -1)
xdotool windowsize "$W" 1600 1000 windowmove "$W" 0 0 2>/dev/null
sleep 2
import -window root "$2"
echo "stderr: $(grep -v -i 'pref' "$LOG" | head -5)"
kill $PID
wait $PID 2>/dev/null
rm -f "$LOG"
