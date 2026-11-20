import java.util.*;

import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.data.Location;

/**
 * Lab 11: 32-bit ALU following Figure 11.3.
 *   ALUSel : 00 Shift, 01 SLT, 10 Arith, 11 Logic
 *   ShiftOp: 00 none (B), 01 SLL, 10 SRL, 11 SRA   (data = B, amount = A[4:0])
 *   SUB    : 0 ADD, 1 SUB   (B xor SUB, carry-in = SUB)
 *   LogicOp: 00 AND, 01 OR, 10 NOR, 11 XOR
 *   Overflow = c31 xor c32 (carries into and out of bit 31), SLT = sign xor overflow
 *   Zero = NOR of all 32 result bits
 * The arithmetic block reuses the Lab 9 Adder32 (4 x RCA8 of FA0 blocks).
 */
public class Alu {
    static Location L(int x, int y) { return LogisimKit.L(x, y); }
    static Location end(Component c, int i) { return LogisimKit.end(c, i); }

    /** 32 -> 2 splitter: out0 = bits 0..30, out1 = bit 31. */
    static Component msbSplitter(LogisimKit.Sheet sh, int x, int y) {
        String[] kv = new String[4 + 64];
        kv[0] = "fanout"; kv[1] = "2"; kv[2] = "incoming"; kv[3] = "32";
        for (int b = 0; b < 32; b++) { kv[4 + 2 * b] = "bit" + b; kv[5 + 2 * b] = b == 31 ? "1" : "0"; }
        String[] all = LogisimKit.concat(new String[]{"facing", "east", "appear", "left"}, kv);
        return sh.add("Wiring", "Splitter", x, y, all);
    }

    static void muxInputs(LogisimKit.Sheet sh, Component mux, String[] labels) {
        for (int i = 0; i < 4; i++) sh.stubTunnel(end(mux, i), 'W', 20 + 70 * i, labels[i], 32);
    }

    public static Circuit build(LogisimKit k, Circuit adder32) {
        Circuit c = k.newCircuit("ALU32");
        LogisimKit.Sheet sh = k.sheet(c);

        // ---------------- inputs
        String[] in = {"A", "B", "ShiftOp", "SUB", "LogicOp", "ALUSel"};
        int[] w = {32, 32, 2, 1, 2, 2};
        for (int i = 0; i < in.length; i++) {
            Component p = sh.pinIn(in[i], w[i], 60, 100 + i * 60);
            sh.stubTunnel(end(p, 0), 'E', 40, in[i], w[i]);
        }

        // ---------------- shifter block (x 300..800, y 100..450)
        String[] sk = new String[4 + 64];
        sk[0] = "fanout"; sk[1] = "2"; sk[2] = "incoming"; sk[3] = "32";
        for (int b = 0; b < 32; b++) { sk[4 + 2 * b] = "bit" + b; sk[5 + 2 * b] = b < 5 ? "0" : "1"; }
        Component shamt = sh.add("Wiring", "Splitter", 360, 120,
                LogisimKit.concat(new String[]{"facing", "east", "appear", "left"}, sk));
        sh.stubTunnel(end(shamt, 0), 'W', 30, "A", 32);
        sh.stubTunnel(end(shamt, 1), 'E', 30, "shamt", 5); // "lsb 5" of A = shift amount

        String[] mode = {"ll", "lr", "ar"};
        Component[] shf = new Component[3];
        for (int i = 0; i < 3; i++) {
            shf[i] = sh.add("Arithmetic", "Shifter", 560, 200 + i * 100, "width", "32", "shift", mode[i]);
            sh.stubTunnel(end(shf[i], 0), 'W', 30, "B", 32);
            sh.stubTunnel(end(shf[i], 1), 'W', 30, "shamt", 5);
        }
        Component muxS = sh.add("Plexers", "Multiplexer", 760, 300, "select", "2", "width", "32", "enable", "false");
        sh.stubTunnel(end(muxS, 0), 'W', 20, "B", 32);                       // 00 none
        int[] chan = {640, 660, 680};
        for (int i = 0; i < 3; i++) {                                         // 01 SLL, 10 SRL, 11 SRA
            Location o = end(shf[i], 2), t = end(muxS, i + 1);
            sh.wire(o, L(chan[i], o.getY()), L(chan[i], t.getY()), t);
        }
        sh.stubTunnel(end(muxS, 4), 'S', 20, "ShiftOp", 2);
        sh.stubTunnel(end(muxS, 5), 'E', 30, "ShiftRes", 32);

        // ---------------- arithmetic block (y 500..1000)
        Component ext = sh.add("Wiring", "Bit Extender", 420, 560, "in_width", "1", "out_width", "32", "type", "sign");
        sh.stubTunnel(end(ext, 1), 'W', 30, "SUB", 1);
        Component xorB = sh.add("Gates", "XOR Gate", 540, 550, "inputs", "2", "width", "32", "size", "30");
        sh.stubTunnel(end(xorB, 1), 'W', 30, "B", 32);
        sh.wire(end(ext, 0), L(470, 560), end(xorB, 2));
        sh.stubTunnel(end(xorB, 0), 'E', 30, "Bx", 32);                       // B xor SUB

        Component add = sh.sub(adder32, 760, 700);
        sh.stubTunnel(LogisimKit.port(add, "A"), 'W', 30, "A", 32);
        sh.stubTunnel(LogisimKit.port(add, "B"), 'W', 100, "Bx", 32);
        sh.stubTunnel(LogisimKit.port(add, "Ci"), 'N', 30, "SUB", 1);
        sh.stubTunnel(LogisimKit.port(add, "Co"), 'S', 30, "Cout", 1);
        sh.stubTunnel(LogisimKit.port(add, "S"), 'E', 30, "Sum", 32);

        // overflow = c31 xor c32, with c31 = A31 xor Bx31 xor Sum31 (since Sum31 = A31^Bx31^c31)
        String[] msb = {"A", "Bx", "Sum"};
        for (int i = 0; i < 3; i++) {
            Component sp = msbSplitter(sh, 420, 820 + i * 60);
            sh.stubTunnel(end(sp, 0), 'W', 30, msb[i], 32);
            sh.stubTunnel(end(sp, 2), 'E', 30, msb[i] + "31", 1);
        }
        Component x3 = sh.add("Gates", "XOR Gate", 660, 900, "inputs", "3", "size", "50", "xor", "odd");
        sh.stubTunnel(end(x3, 1), 'W', 20, "A31", 1);
        sh.stubTunnel(end(x3, 2), 'W', 70, "Bx31", 1);
        sh.stubTunnel(end(x3, 3), 'W', 20, "Sum31", 1);
        sh.stubTunnel(end(x3, 0), 'E', 30, "c31", 1);
        Component xv = sh.add("Gates", "XOR Gate", 660, 1000, "inputs", "2", "size", "50");
        sh.stubTunnel(end(xv, 1), 'W', 20, "c31", 1);
        sh.stubTunnel(end(xv, 2), 'W', 20, "Cout", 1);
        sh.stubTunnel(end(xv, 0), 'E', 30, "V", 1);

        // ---------------- comparator: less = sign xor overflow, zero-extended
        Component xl = sh.add("Gates", "XOR Gate", 660, 1100, "inputs", "2", "size", "50");
        sh.stubTunnel(end(xl, 1), 'W', 20, "Sum31", 1);
        sh.stubTunnel(end(xl, 2), 'W', 20, "V", 1);
        Component zext = sh.add("Wiring", "Bit Extender", 760, 1090, "in_width", "1", "out_width", "32", "type", "zero");
        sh.wire(end(xl, 0), L(700, 1100), L(700, 1090), end(zext, 1));
        sh.stubTunnel(end(zext, 0), 'E', 30, "SltRes", 32);

        // ---------------- logic unit (y 1200..1600)
        Component and = sh.add("Gates", "AND Gate", 540, 1220, "inputs", "2", "width", "32", "size", "30");
        Component or = sh.add("Gates", "OR Gate", 540, 1320, "inputs", "2", "width", "32", "size", "30");
        Component not = sh.add("Gates", "NOT Gate", 640, 1380, "width", "32");
        Component xr = sh.add("Gates", "XOR Gate", 540, 1460, "inputs", "2", "width", "32", "size", "30");
        for (Component g : new Component[]{and, or, xr}) {
            sh.stubTunnel(end(g, 1), 'W', 30, "A", 32);
            sh.stubTunnel(end(g, 2), 'W', 80, "B", 32);
        }
        Component muxL = sh.add("Plexers", "Multiplexer", 860, 1360, "select", "2", "width", "32", "enable", "false");
        Location ao = end(and, 0), oo = end(or, 0), no = end(not, 0), xo = end(xr, 0);
        // separate vertical channels; no wire corner lies on another net's wire
        sh.wire(ao, L(740, ao.getY()), L(740, end(muxL, 0).getY()), end(muxL, 0));
        Location jo = L(580, oo.getY());
        sh.wire(oo, jo, L(770, oo.getY()), L(770, end(muxL, 1).getY()), end(muxL, 1));
        sh.wire(jo, L(580, no.getY()), end(not, 1));                                 // NOR = NOT(OR)
        sh.wire(no, L(790, no.getY()), L(790, end(muxL, 2).getY()), end(muxL, 2));
        sh.wire(xo, L(805, xo.getY()), L(805, end(muxL, 3).getY()), end(muxL, 3));
        sh.stubTunnel(end(muxL, 4), 'S', 20, "LogicOp", 2);
        sh.stubTunnel(end(muxL, 5), 'E', 30, "LogicRes", 32);

        // ---------------- result multiplexer
        Component muxR = sh.add("Plexers", "Multiplexer", 1240, 400, "select", "2", "width", "32", "enable", "false");
        muxInputs(sh, muxR, new String[]{"ShiftRes", "SltRes", "Sum", "LogicRes"});
        sh.stubTunnel(end(muxR, 4), 'S', 20, "ALUSel", 2);
        Component res = sh.pinOut("Result", 32, 1340, 400);
        Location jr = L(1280, 400);
        sh.wire(end(muxR, 5), jr, end(res, 0));
        sh.wire(jr, L(1280, 460));
        sh.tunnel("Result", 32, L(1280, 460), "north");

        Component ovf = sh.pinOut("Overflow", 1, 1340, 520);
        sh.stubTunnel(end(ovf, 0), 'W', 40, "V", 1);

        // ---------------- zero detector: 32-input NOR on the result bits
        Component nor = sh.add("Gates", "NOR Gate", 1420, 900, "inputs", "32", "size", "30");
        Component probe = sh.add("Wiring", "Splitter", 1160, 0, "facing", "east", "fanout", "32", "incoming", "32", "appear", "left");
        int dy = end(nor, 1).getY() - end(probe, 1).getY();
        sh.remove(probe);
        Component zs = sh.add("Wiring", "Splitter", 1160, dy, "facing", "east", "fanout", "32", "incoming", "32", "appear", "left");
        // NOR inputs skip the output row, so bits whose input is lower get their own
        // staircase channel (right-most channel for the top-most bit: no corner touches another bit)
        List<Integer> off = new ArrayList<>();
        for (int b = 1; b <= 32; b++) {
            if (end(zs, b).getY() == end(nor, b).getY()) sh.wire(end(zs, b), end(nor, b));
            else off.add(b);
        }
        for (int i = 0; i < off.size(); i++) {
            Location e = end(zs, off.get(i)), t = end(nor, off.get(i));
            int cx = e.getX() + 10 * (off.size() - i);
            sh.wire(e, L(cx, e.getY()), L(cx, t.getY()), t);
        }
        sh.stubTunnel(end(zs, 0), 'W', 30, "Result", 32);
        Component zero = sh.pinOut("Zero", 1, 1480, 900);
        sh.wire(end(nor, 0), end(zero, 0));

        sh.commit();
        return c;
    }

    public static void main(String[] a) throws Exception {
        LogisimKit k = new LogisimKit();
        Circuit fa = Adders.buildFA0(k);
        Circuit rca = Adders.buildRCA8(k, fa);
        Circuit add32 = Adders.buildAdder32(k, rca);
        Circuit alu = build(k, add32);
        k.dropTemplateMain(alu);
        System.out.println("lab11 saved: " + k.save(a[0] + "/lab11-alu/lab11_alu32.circ"));
    }

    // ------------------------------------------------------------------ tests
    static long u(long v) { return v & 0xFFFFFFFFL; }

    public static void test(String root) throws Exception {
        Tests.begin(root, "lab11-alu", "Lab 11 - 32-bit ALU (Figure 11.3)");
        LogisimKit.Sim s = new LogisimKit.Sim(root + "/lab11-alu/lab11_alu32.circ", "ALU32");
        Random rnd = new Random(11);
        long[] edge = {0, 1, 2, 5, 31, 32, 0x7FFFFFFFL, 0x80000000L, 0xFFFFFFFFL, 0xFFFFFFFEL, 0x12345678L, 0xF0F0F0F0L};
        List<long[]> ab = new ArrayList<>();
        for (long x : edge) for (long y : edge) ab.add(new long[]{x, y});
        for (int i = 0; i < 3000; i++) ab.add(new long[]{u(rnd.nextInt()), u(rnd.nextInt())});

        String[] opName = new String[0];
        int[] counts = new int[10];
        String[] names = {"PASS-B", "SLL", "SRL", "SRA", "SLT", "ADD", "SUB", "AND", "OR", "NOR", "XOR"};
        int[] perOp = new int[names.length];
        for (long[] v : ab) {
            long A = v[0], B = v[1];
            int sh = (int) (A & 31);
            for (int op = 0; op < names.length; op++) {
                int sel, shiftOp = 0, sub = 0, logicOp = 0;
                long exp;
                Integer expV = null;
                switch (op) {
                    case 0: sel = 0; shiftOp = 0; exp = B; break;
                    case 1: sel = 0; shiftOp = 1; exp = u(B << sh); break;
                    case 2: sel = 0; shiftOp = 2; exp = B >>> sh; break;
                    case 3: sel = 0; shiftOp = 3; exp = u(((int) B) >> sh); break;
                    case 4: sel = 1; sub = 1; exp = ((int) A < (int) B) ? 1 : 0; break;
                    case 5: sel = 2; sub = 0; exp = u(A + B);
                        expV = (((int) A + (long) (int) B) != (int) (A + B)) ? 1 : 0; break;
                    case 6: sel = 2; sub = 1; exp = u(A - B);
                        expV = (((long) (int) A - (long) (int) B) != (int) (A - B)) ? 1 : 0; break;
                    case 7: sel = 3; logicOp = 0; exp = A & B; break;
                    case 8: sel = 3; logicOp = 1; exp = A | B; break;
                    case 9: sel = 3; logicOp = 2; exp = u(~(A | B)); break;
                    default: sel = 3; logicOp = 3; exp = A ^ B; break;
                }
                s.set("A", A); s.set("B", B); s.set("ALUSel", sel); s.set("ShiftOp", shiftOp);
                s.set("SUB", sub); s.set("LogicOp", logicOp); s.propagate();
                long r = s.get("Result");
                boolean ok = r == exp && s.get("Zero") == (exp == 0 ? 1 : 0);
                if (expV != null) ok &= s.get("Overflow") == expV;
                Tests.check(ok, String.format("%s A=%08x B=%08x got %08x Z=%d V=%d exp %08x",
                        names[op], A, B, r, s.get("Zero"), s.get("Overflow"), exp));
                perOp[op]++;
            }
        }
        Tests.log.println("Each operation checked on " + ab.size() + " (A,B) pairs (" + edge.length * edge.length
                + " edge-case pairs + 3000 random, seed 11): Result and Zero; Overflow for ADD/SUB");
        Tests.log.println("Examples (from simulation):");
        long[][] ex = {
                {0x00000005, 0x00000003, 2, 0, 0, 0}, {0x00000005, 0x00000003, 2, 0, 1, 0},
                {0x00000003, 0x00000005, 2, 0, 1, 0}, {0x7FFFFFFF, 0x00000001, 2, 0, 0, 0},
                {0x80000000L, 0x00000001, 2, 0, 1, 0}, {0xFFFFFFFFL, 0x00000001, 1, 0, 1, 0},
                {0x00000001, 0xFFFFFFFFL, 1, 0, 1, 0}, {0x00000004, 0x0000000F, 0, 1, 0, 0},
                {0x00000004, 0xF0000000L, 0, 2, 0, 0}, {0x00000004, 0xF0000000L, 0, 3, 0, 0},
                {0x0000FF00, 0x00F0F000, 3, 0, 0, 0}, {0x0000FF00, 0x00F0F000, 3, 0, 0, 1},
                {0x0000FF00, 0x00F0F000, 3, 0, 0, 2}, {0x0000FF00, 0x00F0F000, 3, 0, 0, 3},
                {0x12345678, 0x12345678, 2, 0, 1, 0}};
        Tests.log.println("  A          B          ALUSel ShiftOp SUB LogicOp | Result     Zero Ovf");
        for (long[] e : ex) {
            s.set("A", e[0]); s.set("B", e[1]); s.set("ALUSel", e[2]); s.set("ShiftOp", e[3]);
            s.set("SUB", e[4]); s.set("LogicOp", e[5]); s.propagate();
            Tests.log.println(String.format("  0x%08X 0x%08X   %d      %d     %d     %d    | 0x%08X  %d    %d",
                    e[0], e[1], e[2], e[3], e[4], e[5], s.get("Result"), s.get("Zero"), s.get("Overflow")));
        }
        Tests.end(s);
    }
}
