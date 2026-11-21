import java.util.*;

import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.data.Location;

/**
 * Lab 10: 32 x 32-bit register file, structured like the class reference design
 * (photos of "12 CPU" in Logisim): 32 bit Register, 32 bit 0 Register, 3x4 zipper,
 * 3x4 Zipper (4 wide), Register Cell, Register Cell w/ 0 and Register File.
 * Same idea as Figure 10.2: decoders select, tri-state buffers drive Bus A / Bus B,
 * RegWrite gates the write decoder, register 0 is the constant 0.
 */
public class RegFile {
    static Location L(int x, int y) { return LogisimKit.L(x, y); }
    static Location end(Component c, int i) { return LogisimKit.end(c, i); }
    static Location port(Component c, String l) { return LogisimKit.port(c, l); }

    static final String[] ABW = {"A", "B", "W"};

    /** Control (3 bits: bit0 = A read enable, bit1 = B read enable, bit2 = W write enable). */
    static Component controlSplitter(LogisimKit.Sheet sh, int x, int y, boolean withW) {
        Component p = sh.pinIn("Control", 3, x - 40, y);
        Component sp = sh.add("Wiring", "Splitter", x, y, "facing", "east", "fanout", "3", "incoming", "3", "appear", "left");
        sh.wire(end(p, 0), end(sp, 0));
        for (int b = 0; b < (withW ? 3 : 2); b++) sh.stubTunnel(end(sp, b + 1), 'E', 20 + 50 * b, ABW[b], 1);
        sh.add("Base", "Text", x - 20, y - 60, "text", "Control = W/B/A (bit2..bit0)");
        return sp;
    }

    /** Q -> two south-free tri-state buffers -> BusA / BusB output pins. */
    static void readBuffers(LogisimKit.Sheet sh, Location q) {
        int x = q.getX(), y = q.getY();
        Component bufA = sh.add("Gates", "Controlled Buffer", x + 100, y, "width", "32");
        Component bufB = sh.add("Gates", "Controlled Buffer", x + 100, y + 100, "width", "32");
        Location j = L(x + 40, y);
        sh.wire(q, j);
        sh.wire(j, end(bufA, 1));
        sh.wire(j, L(x + 40, y + 100), end(bufB, 1));
        sh.stubTunnel(end(bufA, 2), 'S', 10, "A", 1);
        sh.stubTunnel(end(bufB, 2), 'S', 10, "B", 1);
        Component pa = sh.pinOut("BusA", 32, x + 180, y);
        Component pb = sh.pinOut("BusB", 32, x + 180, y + 100);
        sh.wire(end(bufA, 0), end(pa, 0));
        sh.wire(end(bufB, 0), end(pb, 0));
    }

    public static Circuit reg32(LogisimKit k) {
        Circuit c = k.newCircuit("32 bit Register");
        LogisimKit.Sheet sh = k.sheet(c);
        Component busW = sh.pinIn("BusW", 32, 100, 200);
        controlSplitter(sh, 140, 340, true);
        Component reg = sh.add("Memory", "Register", 400, 200, "width", "32", "trigger", "rising");
        sh.wire(end(busW, 0), end(reg, 1));                                   // D
        sh.wire(end(reg, 4), L(350, 210), L(350, 260));                       // EN <- W
        sh.tunnel("W", 1, L(350, 260), "north");
        Component clk = sh.add("Wiring", "Clock", 380, 300, "facing", "north"); // clock inside, as in class
        sh.wire(end(clk, 0), end(reg, 2));
        readBuffers(sh, end(reg, 0));
        sh.commit();
        LogisimKit.appearance(c, false, new int[]{50, 50, 50, 50}, 2,
                new Object[][]{{"Register", 75, 62}, {"W", 59, 74}, {"Cntrl", 69, 86}, {"A", 60, 96}, {"B", 90, 96}},
                new Object[][]{{"BusW", 50, 70}, {"Control", 50, 80}, {"BusA", 60, 100}, {"BusB", 90, 100}}, 50, 70);
        return c;
    }

    public static Circuit reg0(LogisimKit k) {
        Circuit c = k.newCircuit("32 bit 0 Register");
        LogisimKit.Sheet sh = k.sheet(c);
        controlSplitter(sh, 140, 340, false);
        Component zero = sh.add("Wiring", "Constant", 400, 200, "width", "32", "value", "0x0");
        readBuffers(sh, end(zero, 0));
        sh.commit();
        LogisimKit.appearance(c, false, new int[]{50, 50, 50, 50}, 2,
                new Object[][]{{"0 Reg", 75, 64}, {"Cntrl", 69, 78}, {"A", 60, 96}, {"B", 90, 96}},
                new Object[][]{{"Control", 50, 70}, {"BusA", 60, 100}, {"BusB", 90, 100}}, 50, 70);
        return c;
    }

    /**
     * Zipper: three n-wide one-hot select buses (RA, RB, RW, each 4 groups of g bits)
     * -> four Control outputs C0..C3, Ci = {RW group i, RB group i, RA group i}.
     */
    static Circuit zipper(LogisimKit k, String name, int g) {
        Circuit c = k.newCircuit(name);
        LogisimKit.Sheet sh = k.sheet(c);
        String[] in = {"RA", "RB", "RW"};
        String[] t = {"a", "b", "w"};
        for (int s = 0; s < 3; s++) {
            int y = 100 + s * 150;
            Component p = sh.pinIn(in[s], 4 * g, 100, y);
            Component sp = sh.add("Wiring", "Splitter", 140, y, "facing", "east", "fanout", "4",
                    "incoming", "" + 4 * g, "appear", "left");
            sh.wire(end(p, 0), end(sp, 0));
            for (int i = 0; i < 4; i++) sh.stubTunnel(end(sp, i + 1), 'E', 20 + 50 * (i % 3), t[s] + i, g);
        }
        for (int i = 0; i < 4; i++) {
            int y = 100 + i * 120;
            Component sp = sh.add("Wiring", "Splitter", 600, y, "facing", "west", "fanout", "3",
                    "incoming", "" + 3 * g, "appear", "left");
            Component o = sh.pinOut("C" + i, 3 * g, 640, y);
            sh.wire(end(sp, 0), end(o, 0));
            for (int s = 0; s < 3; s++) sh.stubTunnel(end(sp, s + 1), 'W', 20 + 50 * s, t[s] + i, g);
        }
        sh.commit();
        return c;
    }

    public static Circuit zip3x4(LogisimKit k) {
        Circuit c = zipper(k, "3x4 zipper", 1);
        LogisimKit.appearance(c, true, new int[]{200, 60, 200, 160, 140, 150, 140, 70}, 4,
                new Object[][]{{"3x4", 170, 102}, {"to", 170, 112}, {"4x3", 170, 122}, {"Zip", 172, 133}},
                new Object[][]{{"RA", 140, 90}, {"RB", 140, 110}, {"RW", 140, 140},
                        {"C0", 200, 80}, {"C1", 200, 100}, {"C2", 200, 130}, {"C3", 200, 150}}, 200, 80);
        return c;
    }

    public static Circuit zip4wide(LogisimKit k) {
        Circuit c = zipper(k, "3x4 Zipper (4 wide)", 4);
        LogisimKit.appearance(c, false, new int[]{50, 50, 40, 60}, 2,
                new Object[][]{{"3x16", 70, 73}, {"to", 70, 83}, {"4x12", 70, 93}, {"Zip", 70, 105}},
                new Object[][]{{"RA", 50, 60}, {"RB", 50, 80}, {"RW", 50, 100},
                        {"C0", 90, 60}, {"C1", 90, 70}, {"C2", 90, 80}, {"C3", 90, 90}}, 50, 60);
        return c;
    }

    /** Four registers + 3x4 zipper. withZero: register 0 is the 32 bit 0 Register. */
    public static Circuit cell(LogisimKit k, String name, boolean withZero, Circuit r32, Circuit r0, Circuit zip) {
        Circuit c = k.newCircuit(name);
        LogisimKit.Sheet sh = k.sheet(c);
        Component z = sh.sub(zip, 300, 200);
        String[] in = {"RA", "RB", "RW"};
        for (String s : in) {
            Location p = port(z, s);
            Component pin = sh.pinIn(s, 4, 100, p.getY());
            sh.wire(end(pin, 0), p);
        }
        for (int i = 0; i < 4; i++) sh.stubTunnel(port(z, "C" + i), 'E', 20 + 60 * (i % 2), "C" + i, 3);
        for (int i = 0; i < 4; i++) {
            boolean zero = withZero && i == 0;
            Component r = sh.sub(zero ? r0 : r32, 600, 100 + i * 140, "label", zero ? "R0 = 0" : "reg " + i);
            if (!zero) sh.stubTunnel(port(r, "BusW"), 'W', 20, "BusW", 32);
            sh.stubTunnel(port(r, "Control"), 'W', zero ? 20 : 80, "C" + i, 3);
            sh.stubTunnel(port(r, "BusA"), 'S', 20, "BusA", 32);
            sh.stubTunnel(port(r, "BusB"), 'S', 50, "BusB", 32);
        }
        Component w = sh.pinIn("BusW", 32, 100, 520);
        sh.stubTunnel(end(w, 0), 'E', 40, "BusW", 32);
        Component a = sh.pinOut("BusA", 32, 900, 120);
        Component b = sh.pinOut("BusB", 32, 900, 240);
        sh.stubTunnel(end(a, 0), 'W', 40, "BusA", 32);
        sh.stubTunnel(end(b, 0), 'W', 40, "BusB", 32);
        sh.commit();
        LogisimKit.appearance(c, false, new int[]{50, 50, 90, 80}, 2,
                new Object[][]{{withZero ? "Cell w/ 0" : "Reg Cell", 100, 64}, {"RA", 67, 74}, {"RB", 67, 94},
                        {"RW", 67, 114}, {"W", 70, 125}, {"A", 100, 125}, {"B", 130, 125}},
                new Object[][]{{"RA", 50, 70}, {"RB", 50, 90}, {"RW", 50, 110},
                        {"BusW", 70, 130}, {"BusA", 100, 130}, {"BusB", 130, 130}}, 50, 70);
        return c;
    }

    /** West-facing 16->1 splitter whose split ends line up with decoder outputs first..first+15. */
    static Component decoderBus(LogisimKit.Sheet sh, Component dec, int first) {
        Location o0 = end(dec, first);
        for (String app : new String[]{"left", "right"}) {
            Component probe = sh.add("Wiring", "Splitter", 0, 0, "facing", "west", "fanout", "16", "incoming", "16", "appear", app);
            int dx = o0.getX() + 40 - end(probe, 1).getX(), dy = o0.getY() - end(probe, 1).getY();
            sh.remove(probe);
            Component sp = sh.add("Wiring", "Splitter", dx, dy, "facing", "west", "fanout", "16", "incoming", "16", "appear", app);
            boolean ok = true;
            for (int b = 0; b < 16; b++) ok &= end(sp, b + 1).getY() == end(dec, first + b).getY();
            if (ok) {
                for (int b = 0; b < 16; b++) sh.wire(end(dec, first + b), end(sp, b + 1));
                return sp;
            }
            sh.remove(sp);
        }
        throw new IllegalStateException("cannot align decoder bus");
    }

    public static Circuit regFile(LogisimKit k, Circuit cell0, Circuit cell, Circuit z4) {
        Circuit c = k.newCircuit("Register File");
        LogisimKit.Sheet sh = k.sheet(c);
        String[] sel = {"RA", "RB", "RW"};
        String[] bus = {"A", "B", "W"};
        for (int d = 0; d < 3; d++) {
            int y = 420 + d * 440;
            boolean w = d == 2;
            Component dec = sh.add("Plexers", "Decoder", 160, y, "select", "5", "enable", w ? "true" : "false",
                    "disabled", "0");
            Component p = sh.pinIn(sel[d], 5, 160, y + 50, "facing", "north", "labelloc", "south");
            sh.wire(end(p, 0), end(dec, 32));
            if (w) { // RegWrite enables the write decoder (all outputs 0 when RegWrite = 0)
                Location en = end(dec, 33);
                Component rw = sh.pinIn("RegWrite", 1, en.getX() - 60, en.getY() + 30);
                sh.wire(end(rw, 0), L(en.getX(), en.getY() + 30), en);
            }
            Component lo = decoderBus(sh, dec, 0), hi = decoderBus(sh, dec, 16);
            sh.stubTunnel(end(lo, 0), 'E', 30, bus[d] + "_0-15", 16);
            sh.stubTunnel(end(hi, 0), 'E', 30, bus[d] + "_16-31", 16);
        }
        // two 3x16 -> 4x12 zippers: registers 0-15 and 16-31
        for (int h = 0; h < 2; h++) {
            Component z = sh.sub(z4, 520, 300 + h * 600);
            String half = h == 0 ? "_0-15" : "_16-31";
            for (int s = 0; s < 3; s++) sh.stubTunnel(port(z, sel[s]), 'W', 30, bus[s] + half, 16);
            for (int i = 0; i < 4; i++) sh.stubTunnel(port(z, "C" + i), 'E', 20 + 50 * i, "Z" + (4 * h + i), 12);
        }
        // eight register cells (4 registers each)
        for (int j = 0; j < 8; j++) {
            int cx = 960 + (j / 4) * 420, cy = 200 + (j % 4) * 250;
            Component cl = sh.sub(j == 0 ? cell0 : cell, cx, cy, "label", (4 * j) + "-" + (4 * j + 3));
            Component sp = sh.add("Wiring", "Splitter", cx - 80, cy - 60, "facing", "east", "fanout", "3",
                    "incoming", "12", "appear", "left");
            sh.stubTunnel(end(sp, 0), 'W', 30, "Z" + j, 12);
            for (int s = 0; s < 3; s++) {
                Location e = end(sp, s + 1), q = port(cl, sel[s]);
                int chx = cx - 50 + 10 * s;
                sh.wire(e, L(chx, e.getY()), L(chx, q.getY()), q);
            }
            sh.stubTunnel(port(cl, "BusW"), 'S', 20, "Bus W", 32);
            sh.stubTunnel(port(cl, "BusA"), 'S', 50, "Bus A", 32);
            sh.stubTunnel(port(cl, "BusB"), 'S', 20, "Bus B", 32);
        }
        Component bw = sh.pinIn("Bus W", 32, 520, 1260);
        sh.stubTunnel(end(bw, 0), 'E', 40, "Bus W", 32);
        Component ba = sh.pinOut("Bus A", 32, 1900, 200);
        Component bb = sh.pinOut("Bus B", 32, 1900, 320);
        sh.stubTunnel(end(ba, 0), 'W', 40, "Bus A", 32);
        sh.stubTunnel(end(bb, 0), 'W', 40, "Bus B", 32);
        sh.add("Base", "Text", 1180, 140, "text", "Registers");
        sh.commit();
        LogisimKit.appearance(c, false, new int[]{50, 50, 130, 150}, 2,
                new Object[][]{{"Ra", 67, 105}, {"Rb", 68, 144}, {"Rw", 68, 175}, {"RegWrite", 85, 194},
                        {"Bus W", 140, 194}, {"Bus A", 155, 95}, {"Bus B", 155, 155}, {"Register", 115, 130}},
                new Object[][]{{"RA", 50, 100}, {"RB", 50, 140}, {"RW", 50, 170}, {"Bus A", 180, 90},
                        {"Bus B", 180, 150}, {"RegWrite", 80, 200}, {"Bus W", 140, 200}}, 50, 100);
        return c;
    }

    public static Circuit buildAll(LogisimKit k) {
        Circuit r32 = reg32(k), r0 = reg0(k), z = zip3x4(k), z4 = zip4wide(k);
        Circuit cell = cell(k, "Register Cell", false, r32, r0, z);
        Circuit cell0 = cell(k, "Register Cell w/ 0", true, r32, r0, z);
        return regFile(k, cell0, cell, z4);
    }

    public static void main(String[] a) throws Exception {
        LogisimKit k = new LogisimKit();
        Circuit rf = buildAll(k);
        k.dropTemplateMain(rf);
        // order in the explorer like the class file: Register File first
        System.out.println("lab10 saved: " + k.save(a[0] + "/lab10-register-file/lab10_register_file.circ"));
    }

    // ------------------------------------------------------------------ tests
    /** One Ctrl+T cycle: every Clock component goes 0 -> 1 (rising edge) -> 0. */
    static void cycle(LogisimKit.Sim s) {
        for (int t = 0; t < 2; t++) {
            s.state.getPropagator().tick();
            s.propagate();
        }
    }

    static void tickOnce(LogisimKit.Sim s) { s.state.getPropagator().tick(); s.propagate(); }

    public static void test(String root) throws Exception {
        String f = root + "/lab10-register-file/lab10_register_file.circ";
        Tests.begin(root, "lab10-register-file", "Lab 10 - 32x32-bit register file (class design, Figure 10.2 idea)");

        // ---- 3x4 zipper: Ci = {RW[i], RB[i], RA[i]}
        LogisimKit.Sim z = new LogisimKit.Sim(f, "3x4 zipper");
        for (int v = 0; v < 4096; v++) {
            int ra = v & 15, rb = v >> 4 & 15, rw = v >> 8 & 15;
            z.set("RA", ra); z.set("RB", rb); z.set("RW", rw); z.propagate();
            for (int i = 0; i < 4; i++)
                Tests.check(z.get("C" + i) == ((ra >> i & 1) | (rb >> i & 1) << 1 | (rw >> i & 1) << 2), "3x4 zipper");
        }
        Tests.log.println("3x4 zipper: all 4096 inputs, Ci = {RW[i],RB[i],RA[i]}");
        // ---- 3x4 Zipper (4 wide): Ci = {RW nibble i, RB nibble i, RA nibble i}
        LogisimKit.Sim z4 = new LogisimKit.Sim(f, "3x4 Zipper (4 wide)");
        Random rnd = new Random(10);
        for (int n = 0; n < 2000; n++) {
            int ra = rnd.nextInt(65536), rb = rnd.nextInt(65536), rw = rnd.nextInt(65536);
            z4.set("RA", ra); z4.set("RB", rb); z4.set("RW", rw); z4.propagate();
            for (int i = 0; i < 4; i++)
                Tests.check(z4.get("C" + i) == ((ra >> 4 * i & 15) | (rb >> 4 * i & 15) << 4 | (rw >> 4 * i & 15) << 8), "zip4");
        }
        Tests.log.println("3x4 Zipper (4 wide): 2000 random inputs");
        // ---- 32 bit Register / 32 bit 0 Register
        LogisimKit.Sim r = new LogisimKit.Sim(f, "32 bit Register");
        r.set("BusW", 0x13572468L); r.set("Control", 4); r.propagate(); cycle(r);       // W=1, no read
        Tests.check(r.raw("BusA").toString().contains("x") && r.raw("BusB").toString().contains("x"), "buffers off -> floating");
        r.set("Control", 1); r.propagate();
        Tests.check(r.get("BusA") == 0x13572468L && r.raw("BusB").toString().contains("x"), "A only");
        r.set("Control", 2); r.set("BusW", 0xFFFFFFFFL); r.propagate(); cycle(r);        // W=0 -> keeps old value
        Tests.check(r.get("BusB") == 0x13572468L, "B only, write disabled");
        r.set("Control", 7); r.propagate(); cycle(r);
        Tests.check(r.get("BusA") == 0xFFFFFFFFL && r.get("BusB") == 0xFFFFFFFFL, "write + both reads");
        Tests.log.println("32 bit Register: write only when W=1 on the clock edge; A/B buffers float (x) unless enabled");
        LogisimKit.Sim r0 = new LogisimKit.Sim(f, "32 bit 0 Register");
        r0.set("Control", 7); r0.propagate(); cycle(r0);
        Tests.check(r0.get("BusA") == 0 && r0.get("BusB") == 0, "0 register");
        Tests.log.println("32 bit 0 Register: always reads 0");

        // ---- Register File
        LogisimKit.Sim s = new LogisimKit.Sim(f, "Register File");
        long[] model = new long[32];
        s.set("RegWrite", 0); s.set("RW", 0); s.set("Bus W", 0); s.propagate();
        for (int i = 0; i < 32; i++) {
            s.set("RA", i); s.set("RB", 31 - i); s.propagate();
            Tests.check(s.get("Bus A") == 0 && s.get("Bus B") == 0, "power-up R" + i);
        }
        Tests.log.println("1) power-up: all 32 registers read 0 on Bus A and Bus B");
        s.set("RegWrite", 1);
        for (int i = 0; i < 32; i++) {
            long v = rnd.nextInt() & 0xFFFFFFFFL;
            s.set("RW", i); s.set("Bus W", v); s.propagate();
            cycle(s);
            if (i != 0) model[i] = v;
        }
        for (int ra = 0; ra < 32; ra++)
            for (int rb = 0; rb < 32; rb++) {
                s.set("RA", ra); s.set("RB", rb); s.propagate();
                Tests.check(s.get("Bus A") == model[ra] && s.get("Bus B") == model[rb], "read " + ra + "," + rb);
            }
        Tests.log.println("2) wrote a random value to R0..R31 (RegWrite=1, one clock cycle each)");
        Tests.log.println("3) read back all 1024 (RA,RB) combinations; R0 stays 0");
        for (int i : new int[]{0, 1, 2, 3, 4, 15, 16, 31}) {
            s.set("RA", i); s.set("RB", i); s.propagate();
            Tests.log.println(String.format("   R%-2d = 0x%08X (Bus A) 0x%08X (Bus B)", i, s.get("Bus A"), s.get("Bus B")));
        }
        s.set("RegWrite", 0);
        for (int i = 0; i < 32; i++) { s.set("RW", i); s.set("Bus W", 0xDEADBEEFL); s.propagate(); cycle(s); }
        boolean ok = true;
        for (int i = 0; i < 32; i++) { s.set("RA", i); s.propagate(); ok &= s.get("Bus A") == model[i]; }
        Tests.check(ok, "RegWrite=0 blocks writes");
        Tests.log.println("4) RegWrite=0 + clock cycles with Bus W=0xDEADBEEF: no register changed");
        s.set("RegWrite", 1); s.set("RW", 7); s.set("Bus W", 0x0BADF00DL); s.set("RA", 7); s.propagate();
        tickOnce(s);                                           // clock 0 -> 1: write
        Tests.check(s.get("Bus A") == 0x0BADF00DL, "rising edge writes");
        s.set("Bus W", 0x11111111L); s.propagate();
        tickOnce(s);                                           // clock 1 -> 0: no write
        Tests.check(s.get("Bus A") == 0x0BADF00DL, "falling edge does not write");
        model[7] = 0x0BADF00DL;
        Tests.log.println("5) the write happens on the rising clock edge only (R7 = 0x0BADF00D)");
        s.set("RW", 31); s.set("Bus W", 0xCAFEBABEL); s.set("RA", 31); s.set("RB", 31); s.propagate();
        Tests.check(s.get("Bus A") == model[31], "old value before edge");
        cycle(s);
        Tests.check(s.get("Bus A") == 0xCAFEBABEL && s.get("Bus B") == 0xCAFEBABEL, "new value after edge");
        Tests.log.println("6) RA=RB=RW=31: old value before the edge, 0xCAFEBABE after it on both buses");
        Tests.end(s);
    }
}
