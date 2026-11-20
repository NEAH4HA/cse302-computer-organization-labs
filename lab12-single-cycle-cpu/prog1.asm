# Lab 12 - prog1.txt disassembled (MIPS instructions executed by the single-cycle CPU)
# Data memory image mem1.txt: word[0]=1, word[1]=1, word[2]=10
# Computes 1 + 2 + ... + 10 = 55 and stores it in word[0] of data memory.
# Assemble in MARS with Settings > Memory Configuration > "Compact, Data at Address 0".
        .data
        .word 1, 1, 10          # mem1.txt
        .text
        sub  $0, $0, $0         # 0x00000022  no-op ($0 stays 0)
        lw   $1, 0($0)          # 0x8C010000  $1 = 1   (increment)
        lw   $2, 4($0)          # 0x8C020004  $2 = 1   (counter i)
        lw   $3, 8($0)          # 0x8C030008  $3 = 10  (limit)
        sub  $4, $4, $4         # 0x00842022  $4 = 0   (sum)
loop:   add  $4, $4, $2         # 0x00822020  sum += i
        slt  $5, $2, $3         # 0x0043282A  $5 = (i < 10)
        beq  $5, $0, done       # 0x10A00002  exit loop when i >= 10
        add  $2, $2, $1         # 0x00411020  i += 1
        beq  $0, $0, loop       # 0x1000FFFB  unconditional branch back
done:   sw   $4, 0($0)          # 0xAC040000  word[0] = sum = 55
halt:   beq  $0, $0, halt       # 0x1000FFFF  stop (branch to itself)
