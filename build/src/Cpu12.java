import java.io.*;
import java.lang.reflect.Method;
import java.util.*;

import com.cburch.logisim.circuit.*;
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.data.Value;
import com.cburch.logisim.instance.StdAttr;
import com.cburch.logisim.std.memory.Ram;

/**
 * Lab 12 runner for the (external, not generated) single-cycle CPU.
 * Does what the lab sheet says, through Logisim's own code:
 *   1. "restart the simulation"          -> fresh CircuitState
 *   2. instruction memory > Load Image   -> Ram.loadImage(prog)
 *   3. data memory > Load Image          -> Ram.loadImage(mem)
 *   4. Ctrl+T repeatedly                 -> Propagator.tick() + propagate() (tick = toggle all clocks)
 * Usage: java Cpu12 cpu.circ prog.txt mem.txt cycles out.txt [snapshot.png]
 */
public class Cpu12 {
    /** RAM contents via MemState.getContents()/MemContents.get(addr) (package-private classes). */
    static int memGet(CircuitState st, Component ram, long addr) throws Exception {
        Object ms = st.getData(ram);
        Method gc = ms.getClass().getMethod("getContents");
        gc.setAccessible(true);
        Object mc = gc.invoke(ms);
        Method g = mc.getClass().getMethod("get", long.class);
        g.setAccessible(true);
        return (Integer) g.invoke(mc, addr);
    }

    public static void main(String[] a) {
        try { run(a); System.exit(0); } catch (Throwable t) { t.printStackTrace(); System.exit(2); }
    }

    static void run(String[] a) throws Exception {
        LogisimKit.Sim s = new LogisimKit.Sim(a[0], null);
        File prog = new File(a[1]), mem = new File(a[2]);
        int cycles = Integer.parseInt(a[3]);
        PrintStream out = new PrintStream(new FileOutputStream(a[4]));

        List<Component> rams = new ArrayList<>();
        Component pc = null;
        for (Component c : s.circ.getNonWires()) {
            if (c.getFactory() instanceof Ram) rams.add(c);
            if (c.getFactory().getName().equals("Register")
                    && c.getAttributeSet().getValue(StdAttr.WIDTH).getWidth() == 30) pc = c;
        }
        rams.sort(Comparator.comparingInt(c -> c.getLocation().getX()));
        Component imem = rams.get(0), dmem = rams.get(1);   // left RAM = instruction memory
        Ram ram = (Ram) imem.getFactory();
        ram.loadImage(s.state.getInstanceState(imem), prog);
        ram.loadImage(s.state.getInstanceState(dmem), mem);
        s.state.getPropagator().propagate();

        out.println("# Lab 12 - single-cycle CPU (external design: github.com/Kumbong/mini-mips, MIT)");
        out.println("# Simulated with Logisim 2.7.1; program = " + prog.getName() + ", data = " + mem.getName());
        out.println("# one cycle = two Ctrl+T ticks (clock low->high->low)");
        out.println("cycle  PC(word)  PC(byte)  instruction");
        for (int cyc = 0; cyc < cycles; cyc++) {
            Value pcv = s.state.getValue(LogisimKit.end(pc, 0));
            Value ins = s.state.getValue(LogisimKit.end(imem, 0));
            long p = pcv.isFullyDefined() ? pcv.toIntValue() & 0x3FFFFFFFL : -1;
            out.println(String.format("%5d  %8s  %8s  %s", cyc,
                    p < 0 ? pcv.toString() : String.format("0x%04X", p),
                    p < 0 ? "?" : String.format("0x%04X", p * 4),
                    ins.isFullyDefined() ? String.format("0x%08X", ins.toIntValue()) : ins.toString()));
            for (int t = 0; t < 2; t++) {
                s.state.getPropagator().tick();
                s.state.getPropagator().propagate();
            }
        }
        out.println();
        out.println("Data memory after " + cycles + " cycles (word address: value):");
        for (int i = 0; i < 8; i++) {
            int v = memGet(s.state, dmem, i);
            out.println(String.format("  [%d] = 0x%08X = %d", i, v, v));
        }
        out.close();
        if (a.length > 5) s.exportPng(s.circ, s.state, a[5], 1.0);
        System.out.println("DMEM[0..3] = " + memGet(s.state, dmem, 0) + " " + memGet(s.state, dmem, 1) + " " + memGet(s.state, dmem, 2) + " " + memGet(s.state, dmem, 3));
    }
}
