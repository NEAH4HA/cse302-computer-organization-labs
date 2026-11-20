#!/bin/bash
# Rebuilds and re-verifies everything with the real apps (Logisim 2.7.1 + MARS 4.5).
# Needs: Java 11+ (full JRE for the GUI check), xdotool + imagemagick + Xvfb for the GUI check.
set -e
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"
build/get_tools.sh >/dev/null
J=tools/logisim-generic-2.7.1.jar
M=tools/Mars4_5.jar
JAVA="java -Djava.awt.headless=true -cp $J:build/classes"

echo "== compile builders/tests against Logisim 2.7.1"
rm -rf build/classes
javac -nowarn -cp $J -d build/classes build/src/*.java

echo "== build .circ files with Logisim's own API (Labs 8-11)"
$JAVA Adders "$ROOT"
$JAVA RegFile "$ROOT"
$JAVA Alu "$ROOT"

echo "== functional tests in Logisim's simulator"
$JAVA Tests "$ROOT" all

echo "== export circuit images (Logisim's Export Image code path)"
for f in lab08-intro-logisim/lab08_rca8.circ lab09-32bit-adder/lab09_adder32.circ \
         lab10-register-file/lab10_register_file.circ lab11-alu/lab11_alu32.circ; do
  d=$(dirname $f)/images; rm -rf $d; mkdir -p $d
  $JAVA Snap $f $d 1.0 >/dev/null
done

echo "== Lab 12: external CPU, prog1.txt + mem1.txt, 60 cycles"
E=lab12-single-cycle-cpu/external-mini-mips
$JAVA Cpu12 "$E/cpu/Single_Cycle_Cpu.circ" "$E/test files/prog1.txt" "$E/test files/mem1.txt" 60 \
  lab12-single-cycle-cpu/trace_prog1.txt lab12-single-cycle-cpu/cpu_after_prog1.png

echo "== MARS 4.5"
( cd lab12-single-cycle-cpu
  java -jar ../$M a mc CompactDataAtZero dump .text HexText prog1_mars_machine_code.txt prog1.asm >/dev/null
  if diff -q <(tr 'A-F' 'a-f' < prog1_mars_machine_code.txt) <(tail -n +2 "external-mini-mips/test files/prog1.txt"; echo) >/dev/null; then
    echo "prog1.asm assembles to exactly prog1.txt" | tee prog1_mars_check.txt
  else echo "MISMATCH prog1.asm vs prog1.txt"; exit 1; fi
  java -jar ../$M nc mc CompactDataAtZero 200 dec '$1' '$2' '$3' '$4' '$5' 0x0-0xc prog1.asm | tee -a prog1_mars_check.txt )
( cd lab07-floating-point
  java -jar ../$M nc '$f0' '$f1' '$f2' '$f3' '$f4' '$f5' '$f6' '$f7' '$f8' '$f9' task3.asm > mars_task3_hex.txt
  java -jar ../$M nc '$f0' '$f1' verify_tasks12.asm > mars_tasks12.txt
  cat mars_task3_hex.txt mars_tasks12.txt )

if [ -n "$DISPLAY" ]; then
  echo "== open every .circ in the real Logisim GUI"
  for f in lab08-intro-logisim/lab08_rca8.circ lab09-32bit-adder/lab09_adder32.circ \
           lab10-register-file/lab10_register_file.circ lab11-alu/lab11_alu32.circ \
           "$E/cpu/Single_Cycle_Cpu.circ"; do
    d=$(echo "$f" | cut -d/ -f1)
    build/gui_open_check.sh "$f" "$d/gui_screenshot.png" | tee "$d/gui-open-check.txt"
  done
fi
echo "== done"
