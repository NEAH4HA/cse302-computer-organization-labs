# Lab 7 - Tasks 1 and 2 checked by MARS itself
        .data
t1:     .float -123456789                  # Task 1: MARS assembler encodes the decimal value
t2:     .word  0xCA4C1800                  # Task 2: 1 10010100 10011000001100000000000
nl:     .asciiz "\n"
        .text
        lwc1  $f0, t1
        lwc1  $f1, t2
        mov.s $f12, $f0                    # print Task 1 value as MARS rounds it
        li    $v0, 2
        syscall
        la    $a0, nl
        li    $v0, 4
        syscall
        mov.s $f12, $f1                    # print Task 2 decimal value
        li    $v0, 2
        syscall
        la    $a0, nl
        li    $v0, 4
        syscall
