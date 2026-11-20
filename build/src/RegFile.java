import java.util.*;

import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.data.Location;


/** Lab 10: 32 x 32-bit register file exactly as in Figure 10.2. */
public class RegFile {
    static Location L(int x, int y) { return LogisimKit.L(x, y); }
    static Location end(Component c, int i) { return LogisimKit.end(c, i); }

    public static Circuit build(LogisimKit k) {
        Circuit c = k.newCircuit("RegisterFile");
        LogisimKit.Sheet sh = k.sheet(c);

        // ---- three 5x32 decoders: RW (write select), RA and RB (read select)
        String[] decName = {"W", "A", "B"};
        String[] decPin = {"RW", "RA", "RB"};
        for (int d = 0; d < 3; d++) {
            int dy = 520 + d * 520;
            Component dec = sh.add("Plexers", "Decoder", 160, dy, "select", "5", "enable", "false");
            Component p = sh.pinIn(decPin[d], 5, 160, dy + 50, "facing", "north", "labelloc", "south");
            sh.wire(end(p, 0), end(dec, 32));
            for (int i = 0; i < 32; i++) {
                if (d == 0 && i == 0) continue; // "R0 is not used" for writing
                int len = 20 + (i % 3) * 50;      // stagger the tunnel labels
                sh.stubTunnel(end(dec, i), 'E', len, decName[d] + i, 1);
            }
        }

        // ---- shared input buses through tunnels
        String[] inName = {"BusW", "RegWrite", "Clock"};
        int[] inBits = {32, 1, 1};
        for (int i = 0; i < 3; i++) {
            Component p = sh.pinIn(inName[i], inBits[i], 60, 1700 + i * 60);
            sh.stubTunnel(end(p, 0), 'E', 40, inName[i], inBits[i]);
        }
        // ---- outputs
        String[] outName = {"BusA", "BusB"};
        for (int i = 0; i < 2; i++) {
            Component p = sh.pinOut(outName[i], 32, 1160, 140 + i * 60);
            sh.stubTunnel(end(p, 0), 'W', 40, outName[i], 32);
        }

        // ---- one row per register
        int x0 = 560;
        for (int r = 0; r < 32; r++) {
            int y = 140 + r * 90;
            Location q;
            if (r == 0) {
                Component zero = sh.add("Wiring", "Constant", x0 + 90, y, "width", "32", "value", "0x0");
                q = end(zero, 0); // register 0 is the constant 0 ("0" in Figure 10.2)
            } else {
                // AND(decoder_W[r], RegWrite) -> register enable
                Component and = sh.add("Gates", "AND Gate", x0, y + 10, "inputs", "2", "size", "30");
                sh.stubTunnel(end(and, 1), 'W', 20, "W" + r, 1);
                sh.stubTunnel(end(and, 2), 'W', 20, "RegWrite", 1);
                Component reg = sh.add("Memory", "Register", x0 + 90, y, "width", "32",
                        "trigger", "rising", "label", "R" + r);
                sh.wire(end(and, 0), end(reg, 4));                    // EN
                Location d = end(reg, 1);                             // D <- BusW
                Location t = L(d.getX() - 40, d.getY() - 20);
                sh.wire(d, L(d.getX() - 10, d.getY()), L(d.getX() - 10, t.getY()), t);
                sh.tunnel("BusW", 32, t, "east");
                sh.stubTunnel(end(reg, 2), 'S', 20, "Clock", 1);        // CK
                q = end(reg, 0);
            }
            // Q -> tri-state buffer to BusA (enable = decoder_A[r]) and to BusB (decoder_B[r])
            Component bufA = sh.add("Gates", "Controlled Buffer", q.getX() + 100, y, "width", "32");
            Component bufB = sh.add("Gates", "Controlled Buffer", q.getX() + 200, y + 50, "width", "32");
            Location j = L(q.getX() + 40, y);
            sh.wire(q, j);
            sh.wire(j, end(bufA, 1));
            sh.wire(j, L(j.getX(), y + 50), end(bufB, 1));
            sh.stubTunnel(end(bufA, 2), 'S', 10, "A" + r, 1);
            sh.stubTunnel(end(bufB, 2), 'S', 10, "B" + r, 1);
            sh.stubTunnel(end(bufA, 0), 'E', 20, "BusA", 32);
            sh.stubTunnel(end(bufB, 0), 'E', 20, "BusB", 32);
        }
        sh.commit();
        return c;
    }

    public static void main(String[] a) throws Exception {
        LogisimKit k = new LogisimKit();
        Circuit rf = build(k);
        k.dropTemplateMain(rf);
        System.out.println("lab10 saved: " + k.save(a[0] + "/lab10-register-file/lab10_register_file.circ"));
    }

    // ------------------------------------------------------------------ tests
    static void clock(LogisimKit.Sim s) {
        s.set("Clock", 0); s.propagate();
        s.set("Clock", 1); s.propagate();   // rising edge
        s.set("Clock", 0); s.propagate();
    }

    public static void test(String root) throws Exception {
        Tests.begin(root, "lab10-register-file", "Lab 10 - 32x32-bit register file (Figure 10.2)");
        LogisimKit.Sim s = new LogisimKit.Sim(root + "/lab10-register-file/lab10_register_file.circ", "RegisterFile");
        long[] model = new long[32];
        s.set("Clock", 0); s.set("RegWrite", 0); s.set("RW", 0); s.set("BusW", 0);
        s.propagate();

        // 1) power-up: every register reads 0 on both ports
        for (int r = 0; r < 32; r++) {
            s.set("RA", r); s.set("RB", 31 - r); s.propagate();
            Tests.check(s.get("BusA") == 0 && s.get("BusB") == 0, "power-up read R" + r);
        }
        Tests.log.println("1) power-up: all 32 registers read 0 on BusA and BusB");

        // 2) write a distinct value into every register (including R0, which must ignore it)
        Random rnd = new Random(10);
        s.set("RegWrite", 1);
        for (int r = 0; r < 32; r++) {
            long v = rnd.nextInt() & 0xFFFFFFFFL;
            s.set("RW", r); s.set("BusW", v); s.propagate();
            clock(s);
            if (r != 0) model[r] = v;
        }
        // 3) read every (RA, RB) combination: 32 x 32 = 1024 reads
        for (int ra = 0; ra < 32; ra++)
            for (int rb = 0; rb < 32; rb++) {
                s.set("RA", ra); s.set("RB", rb); s.propagate();
                Tests.check(s.get("BusA") == model[ra] && s.get("BusB") == model[rb],
                        "read RA=" + ra + " RB=" + rb);
            }
        Tests.log.println("2) wrote random values to R0..R31 (RegWrite=1, one rising edge each)");
        Tests.log.println("3) read back all 1024 (RA,RB) combinations; R0 stays 0");
        for (int r = 0; r < 32; r += 1) {
            s.set("RA", r); s.set("RB", r); s.propagate();
            if (r < 6 || r == 31)
                Tests.log.println(String.format("   R%-2d = 0x%08X (BusA) 0x%08X (BusB)", r, s.get("BusA"), s.get("BusB")));
        }

        // 4) RegWrite = 0 blocks writes
        s.set("RegWrite", 0);
        for (int r = 0; r < 32; r++) { s.set("RW", r); s.set("BusW", 0xDEADBEEFL); s.propagate(); clock(s); }
        boolean ok = true;
        for (int r = 0; r < 32; r++) { s.set("RA", r); s.propagate(); ok &= s.get("BusA") == model[r]; }
        Tests.check(ok, "RegWrite=0 must not change any register");
        Tests.log.println("4) RegWrite=0 + clock edges with BusW=0xDEADBEEF: no register changed");

        // 5) writing only happens on the rising edge
        s.set("RegWrite", 1); s.set("RW", 7); s.set("BusW", 0x0BADF00DL);
        s.propagate();                             // let RW/BusW settle (setup time)
        s.set("Clock", 1); s.propagate();          // edge -> written
        s.set("BusW", 0x11111111L); s.propagate();  // clock still high: no new write
        s.set("RA", 7); s.propagate();
        Tests.check(s.get("BusA") == 0x0BADF00DL, "edge-triggered write");
        s.set("Clock", 0); s.propagate();          // falling edge: no write
        Tests.check(s.get("BusA") == 0x0BADF00DL, "no write on falling edge");
        model[7] = 0x0BADF00DL;
        Tests.log.println("5) write happens only on the rising clock edge (R7 = 0x0BADF00D)");

        // 6) read-after-write on the same register, and both ports reading the same register
        s.set("RW", 31); s.set("BusW", 0xCAFEBABEL); s.set("RA", 31); s.set("RB", 31); s.propagate();
        Tests.check(s.get("BusA") == model[31], "before edge old value");
        clock(s);
        Tests.check(s.get("BusA") == 0xCAFEBABEL && s.get("BusB") == 0xCAFEBABEL, "after edge new value");
        Tests.log.println("6) RA=RB=RW=31: old value before the edge, 0xCAFEBABE after it on both buses");
        Tests.end(s);
    }
}
