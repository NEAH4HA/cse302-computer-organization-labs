import java.util.*;

import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.data.Location;

/**
 * Lab 11: 32-bit ALU, structured like the class reference design (photos of "12 CPU"):
 * a "1 bit ALU" slice repeated 32 times inside "32 Bit ALU".
 *
 *   1 bit ALU   inputs A, B, Less, Sub, Cin, Op[2:0]   outputs Result, C out, Set
 *     Op 0 AND, 1 OR, 2 XOR, 3 NOR (on A, B)
 *     Op 4 SUM  = A + (B xor Sub) + Cin   (C out = carry, Set = sum bit)
 *     Op 5 LESS = the Less input            (Op 6, 7 unused -> 0)
 *   32 Bit ALU  inputs A, B, Sub, Op   outputs Result, Zero flag, Overflow
 *     carry-in of bit 0 = Sub (two's complement subtract), carries ripple bit i -> i+1,
 *     V = c31 xor c32 (signed overflow of A + (B xor Sub)); Overflow = V when Op = 4 (add/sub), else 0,
 *     Less of bit 0 = Set31 xor V (slt, correct even when A - B overflows), Less of bits 1..31 = 0,
 *     Shifter block (Appendix 11): B shifted by A[4:0]; Op 6 = SLL, Op 7 = SRL (Sub = 0) / SRA (Sub = 1),
 *     a 2:1 mux picks the shifter (Op = 6, 7) or the slices (Op = 0..5); Zero flag = NOR of the result.
 */
public class Alu {
    static Location L(int x, int y) { return LogisimKit.L(x, y); }
    static Location end(Component c, int i) { return LogisimKit.end(c, i); }
    static Location port(Component c, String l) { return LogisimKit.port(c, l); }

    static Component gate(LogisimKit.Sheet sh, String type, int x, int y, String a, String b, String out) {
        Component g = sh.add("Gates", type, x, y, "inputs", "2", "size", "30");
        sh.stubTunnel(end(g, 1), 'W', 30, a, 1);
        sh.stubTunnel(end(g, 2), 'W', 30, b, 1);
        sh.stubTunnel(end(g, 0), 'E', 30, out, 1);
        return g;
    }

    public static Circuit alu1(LogisimKit k) {
        Circuit c = k.newCircuit("1 bit ALU");
        LogisimKit.Sheet sh = k.sheet(c);
        String[] in = {"A", "B", "Less", "Sub", "Cin", "Op"};
        for (int i = 0; i < in.length; i++) {
            Component p = sh.pinIn(in[i], i == 5 ? 3 : 1, 80, 100 + i * 60);
            sh.stubTunnel(end(p, 0), 'E', 30, in[i], i == 5 ? 3 : 1);
        }
        gate(sh, "XOR Gate", 320, 100, "B", "Sub", "Bx");       // B xor Sub (subtract)
        gate(sh, "AND Gate", 320, 180, "A", "B", "and");
        gate(sh, "OR Gate", 320, 260, "A", "B", "or");
        gate(sh, "XOR Gate", 320, 340, "A", "B", "xor");
        gate(sh, "NOR Gate", 320, 420, "A", "B", "nor");
        Component add = sh.add("Arithmetic", "Adder", 320, 540, "width", "1");
        sh.stubTunnel(end(add, 0), 'W', 30, "A", 1);
        sh.stubTunnel(end(add, 1), 'W', 30, "Bx", 1);
        sh.stubTunnel(end(add, 3), 'N', 20, "Cin", 1);
        sh.stubTunnel(end(add, 4), 'S', 20, "Cout", 1);
        sh.stubTunnel(end(add, 2), 'E', 30, "sum", 1);

        Component mux = sh.add("Plexers", "Multiplexer", 580, 300, "select", "3", "width", "1", "enable", "false");
        String[] mi = {"and", "or", "xor", "nor", "sum", "Less"};
        for (int i = 0; i < 8; i++) {
            int len = i % 2 == 0 ? 20 : 70;
            if (i < 6) sh.stubTunnel(end(mux, i), 'W', len, mi[i], 1);
            else {                                                   // Op 6, 7: unused -> constant 0
                Location e = end(mux, i);
                Component k0 = sh.add("Wiring", "Constant", e.getX() - len, e.getY(), "value", "0x0");
                sh.wire(end(k0, 0), e);
            }
        }
        sh.stubTunnel(end(mux, 8), 'S', 30, "Op", 3);
        Component r = sh.pinOut("Result", 1, 660, 300);
        sh.wire(end(mux, 9), end(r, 0));
        Component set = sh.pinOut("Set", 1, 660, 420);
        sh.stubTunnel(end(set, 0), 'W', 40, "sum", 1);
        Component co = sh.pinOut("C out", 1, 660, 480);
        sh.stubTunnel(end(co, 0), 'W', 40, "Cout", 1);
        sh.add("Base", "Text", 580, 200, "text", "Op: 0 AND 1 OR 2 XOR 3 NOR 4 SUM 5 LESS");
        sh.commit();
        LogisimKit.appearance(c, false, new int[]{50, 50, 70, 70}, 2,
                new Object[][]{{"1b ALU", 87, 97}, {"A", 58, 74}, {"B", 58, 94}, {"L", 57, 114},
                        {"Sub", 62, 63}, {"Cin", 90, 63}, {"Op", 111, 63}, {"R", 113, 84},
                        {"Cout", 81, 115}, {"Set", 108, 115}},
                new Object[][]{{"A", 50, 70}, {"B", 50, 90}, {"Less", 50, 110}, {"Sub", 60, 50},
                        {"Cin", 90, 50}, {"Op", 110, 50}, {"Result", 120, 80}, {"C out", 80, 120},
                        {"Set", 110, 120}}, 50, 70);
        return c;
    }

    /** East-facing 1->32 splitter of bus 'bus' with each bit on tunnel prefix+i (staggered). */
    static Component fanOut(LogisimKit.Sheet sh, int x, int y, String bus, String prefix) {
        Component sp = sh.add("Wiring", "Splitter", x, y, "facing", "east", "fanout", "32", "incoming", "32", "appear", "left");
        sh.stubTunnel(end(sp, 0), 'W', 30, bus, 32);
        for (int b = 0; b < 32; b++) sh.stubTunnel(end(sp, b + 1), 'E', 20 + 50 * (b % 3), prefix + b, 1);
        return sp;
    }

    public static Circuit alu32(LogisimKit k, Circuit alu1) {
        Circuit c = k.newCircuit("32 Bit ALU");
        LogisimKit.Sheet sh = k.sheet(c);
        String[] in = {"A", "B", "Sub", "Op"};
        int[] w = {32, 32, 1, 3};
        for (int i = 0; i < 4; i++) {
            Component p = sh.pinIn(in[i], w[i], 80, 100 + i * 60);
            sh.stubTunnel(end(p, 0), 'E', 30, in[i], w[i]);
        }
        fanOut(sh, 200, 400, "A", "a");
        fanOut(sh, 200, 800, "B", "b");

        for (int i = 0; i < 32; i++) {
            int x = 560 + (i / 16) * 420, y = 160 + (i % 16) * 120;
            Component u = sh.sub(alu1, x, y);
            sh.stubTunnel(port(u, "A"), 'W', 30, "a" + i, 1);
            sh.stubTunnel(port(u, "B"), 'W', 30, "b" + i, 1);
            if (i == 0) sh.stubTunnel(port(u, "Less"), 'W', 30, "less", 1);    // slt: Less0 = Set31 xor V
            else {
                Location l = port(u, "Less");
                Component z = sh.add("Wiring", "Constant", l.getX() - 20, l.getY(), "value", "0x0");
                sh.wire(end(z, 0), l);
            }
            sh.stubTunnel(port(u, "Sub"), 'N', 10, "Sub", 1);
            sh.stubTunnel(port(u, "Op"), 'N', 10, "Op", 3);
            if (i == 0) sh.stubTunnel(port(u, "Cin"), 'N', 40, "Sub", 1);       // Cin0 = Sub
            else if (i == 16) sh.stubTunnel(port(u, "Cin"), 'N', 40, "c16", 1);
            if (i == 15) sh.stubTunnel(port(u, "C out"), 'S', 20, "c16", 1);
            else if (i != 31) {                                                 // ripple carry to the slice below
                Location co = port(u, "C out");
                Location ci = co.translate(10, 120 - 70);                       // next slice's Cin
                if (i == 30) {                                                  // carry into bit 31 tapped west
                    sh.wire(co, co.translate(0, 12), co.translate(0, 25), co.translate(10, 25), ci);
                    sh.stubTunnel(co.translate(0, 12), 'W', 40, "c31", 1);
                } else sh.wire(co, co.translate(0, 25), co.translate(10, 25), ci);
            }
            if (i == 31) {
                sh.stubTunnel(port(u, "Set"), 'S', 20, "Set31", 1);
                sh.stubTunnel(port(u, "C out"), 'S', 50, "c32", 1);              // carry out of bit 31
            }
            sh.stubTunnel(port(u, "Result"), 'E', 30, "r" + i, 1);
        }

        // result bus: 32 -> 1 splitter
        Component rs = sh.add("Wiring", "Splitter", 1500, 400, "facing", "west", "fanout", "32", "incoming", "32", "appear", "left");
        for (int b = 0; b < 32; b++) sh.stubTunnel(end(rs, b + 1), 'W', 20 + 50 * (b % 3), "r" + b, 1);
        sh.stubTunnel(end(rs, 0), 'E', 30, "Rs", 32);                  // result of the 32 slices

        // ---- Shifter block (Appendix 11): B shifted by A[4:0]
        List<String> kv = new ArrayList<>(Arrays.asList("facing", "east", "fanout", "2", "incoming", "32", "appear", "left"));
        for (int b = 0; b < 32; b++) { kv.add("bit" + b); kv.add(b < 5 ? "0" : "1"); }   // A[4:0] -> output 0
        Component sa = sh.add("Wiring", "Splitter", 2000, 160, kv.toArray(new String[0]));
        sh.stubTunnel(end(sa, 0), 'W', 30, "A", 32);
        sh.stubTunnel(end(sa, 1), 'E', 30, "sh", 5);
        String[] kind = {"ll", "lr", "ar"}, kout = {"sll", "srl", "sra"};
        for (int i = 0; i < 3; i++) {
            Component s = sh.add("Arithmetic", "Shifter", 2300, 160 + 100 * i, "width", "32", "shift", kind[i]);
            sh.stubTunnel(end(s, 0), 'W', 30, "B", 32);
            sh.stubTunnel(end(s, 1), 'W', 30, "sh", 5);
            sh.stubTunnel(end(s, 2), 'E', 30, kout[i], 32);
        }
        // Op bits
        Component os = sh.add("Wiring", "Splitter", 2000, 420, "facing", "east", "fanout", "3", "incoming", "3", "appear", "left");
        sh.stubTunnel(end(os, 0), 'W', 30, "Op", 3);
        for (int b = 0; b < 3; b++) sh.stubTunnel(end(os, b + 1), 'E', 20 + 50 * b, "op" + b, 1);
        // result selection: SRL/SRA by Sub, SLL/right by op0, shifter/slices by op2 AND op1
        String[][] mx = {{"srl", "sra", "Sub", "srx"}, {"sll", "srx", "op0", "shf"}, {"Rs", "shf", "isShift", null}};
        Component last = null;
        for (int i = 0; i < 3; i++) {
            Component m = sh.add("Plexers", "Multiplexer", 2500, 480 + 120 * i, "select", "1", "width", "32", "enable", "false");
            sh.stubTunnel(end(m, 0), 'W', 30, mx[i][0], 32);
            sh.stubTunnel(end(m, 1), 'W', 30, mx[i][1], 32);
            sh.stubTunnel(end(m, 2), 'S', 20, mx[i][2], 1);
            if (mx[i][3] != null) sh.stubTunnel(end(m, 3), 'E', 30, mx[i][3], 32);
            last = m;
        }
        Component res = sh.pinOut("Result", 32, 2640, 720);
        Location j = L(2560, 720);
        sh.wire(end(last, 3), j, end(res, 0));
        sh.wire(j, L(2560, 760));
        sh.tunnel("R", 32, L(2560, 760), "north");
        gate(sh, "AND Gate", 2100, 560, "op2", "op1", "isShift");

        // ---- Overflow and SLT
        gate(sh, "XOR Gate", 2100, 680, "c31", "c32", "V");          // signed overflow of A + (B xor Sub)
        gate(sh, "XOR Gate", 2100, 780, "Set31", "V", "less");       // slt = sign xor overflow
        gate(sh, "NOR Gate", 2100, 880, "op1", "op0", "lo0");
        gate(sh, "AND Gate", 2100, 980, "op2", "lo0", "isAdd");      // Op = 4 (add / sub)
        gate(sh, "AND Gate", 2100, 1080, "V", "isAdd", "ovf");
        Component ovp = sh.pinOut("Overflow", 1, 2240, 1080);
        sh.stubTunnel(end(ovp, 0), 'W', 40, "ovf", 1);
        sh.add("Base", "Text", 2300, 80, "text", "Shifter: B shifted by A[4:0]  (Op 6 SLL, Op 7 SRL / SRA when Sub = 1)");
        sh.add("Base", "Text", 2160, 640, "text", "Overflow and SLT");

        // Zero flag: 32-input NOR of the result bits
        Component nor = sh.add("Gates", "NOR Gate", 1740, 1200, "inputs", "32", "size", "30");
        Component probe = sh.add("Wiring", "Splitter", 1480, 0, "facing", "east", "fanout", "32", "incoming", "32", "appear", "left");
        int dy = end(nor, 1).getY() - end(probe, 1).getY();
        sh.remove(probe);
        Component zs = sh.add("Wiring", "Splitter", 1480, dy, "facing", "east", "fanout", "32", "incoming", "32", "appear", "left");
        List<Integer> off = new ArrayList<>();
        for (int b = 1; b <= 32; b++) {
            if (end(zs, b).getY() == end(nor, b).getY()) sh.wire(end(zs, b), end(nor, b));
            else off.add(b);
        }
        for (int i = 0; i < off.size(); i++) {           // staircase: no corner lies on another bit's wire
            Location e = end(zs, off.get(i)), t = end(nor, off.get(i));
            int cx = e.getX() + 10 * (off.size() - i);
            sh.wire(e, L(cx, e.getY()), L(cx, t.getY()), t);
        }
        sh.stubTunnel(end(zs, 0), 'W', 30, "R", 32);
        Component zero = sh.pinOut("Zero flag", 1, 1800, 1200);
        sh.wire(end(nor, 0), end(zero, 0));
        sh.commit();
        LogisimKit.appearance(c, false, new int[]{50, 50, 160, 180}, 2,
                new Object[][]{{"A", 62, 94}, {"B", 62, 144}, {"Sub", 100, 67}, {"Op", 170, 67},
                        {"R", 198, 104}, {"Ovf", 193, 154}, {"Zero", 190, 194}, {"32 Bit ALU", 125, 125}},
                new Object[][]{{"A", 50, 90}, {"B", 50, 140}, {"Sub", 100, 50}, {"Op", 170, 50},
                        {"Result", 210, 100}, {"Overflow", 210, 150}, {"Zero flag", 210, 190}}, 50, 90);
        return c;
    }

    public static void main(String[] a) throws Exception {
        LogisimKit k = new LogisimKit();
        Circuit one = alu1(k);
        Circuit alu = alu32(k, one);
        k.dropTemplateMain(alu);
        System.out.println("lab11 saved: " + k.save(a[0] + "/lab11-alu/lab11_alu32.circ"));
    }

    // ------------------------------------------------------------------ tests
    static long u(long v) { return v & 0xFFFFFFFFL; }

    public static void test(String root) throws Exception {
        String f = root + "/lab11-alu/lab11_alu32.circ";
        Tests.begin(root, "lab11-alu", "Lab 11 - 32-bit ALU (class design: 32 x 1 bit ALU, plus shifter, Overflow and SLT fix)");

        // ---- 1 bit ALU: every Op x every (A, B, Less, Sub, Cin)
        LogisimKit.Sim s1 = new LogisimKit.Sim(f, "1 bit ALU");
        Tests.log.println("1 bit ALU, Result for inputs A B Less Sub Cin = 00000 .. 11111:");
        for (int op = 0; op < 8; op++) {
            StringBuilder row = new StringBuilder();
            for (int v = 0; v < 32; v++) {
                int A = v >> 4 & 1, B = v >> 3 & 1, Ls = v >> 2 & 1, Sb = v >> 1 & 1, Ci = v & 1;
                s1.set("A", A); s1.set("B", B); s1.set("Less", Ls); s1.set("Sub", Sb); s1.set("Cin", Ci);
                s1.set("Op", op); s1.propagate();
                int bx = B ^ Sb, sum = A + bx + Ci;
                int[] exp = {A & B, A | B, A ^ B, 1 - (A | B), sum & 1, Ls, 0, 0};
                long r = s1.get("Result");
                row.append(r);
                Tests.check(r == exp[op] && s1.get("Set") == (sum & 1) && s1.get("C out") == (sum >> 1),
                        "1 bit ALU op " + op + " in " + v);
            }
            Tests.log.println("  Op " + op + ": " + row);
        }
        Tests.log.println("  (Set = sum bit and C out = carry checked on all 256 cases)");

        // ---- 32 Bit ALU
        LogisimKit.Sim s = new LogisimKit.Sim(f, "32 Bit ALU");
        Random rnd = new Random(11);
        long[] edge = {0, 1, 2, 5, 31, 0x7FFFFFFFL, 0x80000000L, 0xFFFFFFFFL, 0xFFFFFFFEL, 0x12345678L, 0xF0F0F0F0L, 0x0F0F0F0FL};
        List<long[]> ab = new ArrayList<>();
        for (long x : edge) for (long y : edge) ab.add(new long[]{x, y});
        for (int i = 0; i < 2000; i++) ab.add(new long[]{u(rnd.nextInt()), u(rnd.nextInt())});
        ab.add(new long[]{0x12345678L, 0x12345678L});

        String[] names = {"AND", "OR", "XOR", "NOR", "ADD", "SUB", "SLT", "SLL", "SRL", "SRA", "SLL*"};
        int[][] ctl = {{0, 0}, {0, 1}, {0, 2}, {0, 3}, {0, 4}, {1, 4}, {1, 5}, {0, 6}, {0, 7}, {1, 7}, {1, 6}}; // {Sub, Op}
        int sltOvf = 0, ovfAdd = 0, ovfSub = 0;
        for (long[] v : ab) {
            long A = v[0], B = v[1];
            int sa = (int) (A & 31), bi = (int) B;
            for (int op = 0; op < names.length; op++) {
                long exp, expV = 0;
                switch (op) {
                    case 0: exp = A & B; break;
                    case 1: exp = A | B; break;
                    case 2: exp = A ^ B; break;
                    case 3: exp = u(~(A | B)); break;
                    case 4: exp = u(A + B); expV = (long) (int) A + (int) B != (int) (A + B) ? 1 : 0; break;
                    case 5: exp = u(A - B); expV = (long) (int) A - (int) B != (int) (A - B) ? 1 : 0; break;
                    case 6: exp = (int) A < (int) B ? 1 : 0; break;      // true signed compare, overflow or not
                    case 7: case 10: exp = u(bi << sa); break;
                    case 8: exp = u(bi >>> sa); break;
                    default: exp = u(bi >> sa);
                }
                s.set("A", A); s.set("B", B); s.set("Sub", ctl[op][0]); s.set("Op", ctl[op][1]); s.propagate();
                long r = s.get("Result"), z = s.get("Zero flag"), ov = s.get("Overflow");
                Tests.check(r == exp && z == (exp == 0 ? 1 : 0) && ov == expV,
                        String.format("%s A=%08x B=%08x got %08x Z=%d V=%d exp %08x V=%d", names[op], A, B, r, z, ov, exp, expV));
                if (op == 4 && expV == 1) ovfAdd++;
                if (op == 5 && expV == 1) ovfSub++;
                if (op == 6 && (long) (int) A - (int) B != (int) (A - B)) sltOvf++;
            }
        }
        Tests.log.println("32 Bit ALU: AND, OR, XOR, NOR, ADD, SUB, SLT, SLL, SRL, SRA (and SLL with Sub = 1) on " + ab.size()
                + " (A,B) pairs (" + edge.length * edge.length + " edge cases + 2000 random, seed 11 + A=B): Result, Zero flag and Overflow");
        Tests.log.println("Overflow = 1 exactly on signed overflow of ADD (" + ovfAdd + " pairs) and SUB (" + ovfSub + " pairs), 0 for every other op");
        Tests.log.println("SLT = true signed A < B on every pair, including the " + sltOvf + " pairs where A - B overflows");
        Tests.log.println("Shifts: B shifted by A[4:0] (A = 0, 1, 2, 5, 31 in the edge cases, random amounts otherwise)");
        Tests.log.println("Examples (from simulation):");
        Tests.log.println("  op   A          B          | Result     Zero Ovf");
        long[][] ex = {{4, 5, 3}, {4, 0xFFFFFFFFL, 1}, {4, 0x7FFFFFFFL, 1}, {5, 5, 3}, {5, 3, 5}, {5, 0x80000000L, 1}, {6, 3, 5}, {6, 5, 3},
                {6, 0xFFFFFFFFL, 1}, {6, 0x80000000L, 1}, {5, 0x12345678L, 0x12345678L}, {0, 0xC, 0xA}, {1, 0xC, 0xA}, {2, 0xC, 0xA}, {3, 0xC, 0xA},
                {7, 4, 0x0000000FL}, {8, 4, 0xF0000000L}, {9, 4, 0xF0000000L}};
        for (long[] e : ex) {
            int op = (int) e[0];
            s.set("A", e[1]); s.set("B", e[2]); s.set("Sub", ctl[op][0]); s.set("Op", ctl[op][1]); s.propagate();
            Tests.log.println(String.format("  %-4s 0x%08X 0x%08X | 0x%08X %d    %d", names[op], e[1], e[2], s.get("Result"), s.get("Zero flag"), s.get("Overflow")));
        }
        Tests.end(s);
    }
}
