import java.io.*;
import java.util.*;

/**
 * Functional tests. Each test re-opens the saved .circ file with a fresh Logisim
 * Loader and drives it with Logisim's own simulator (Propagator), exactly as the
 * Poke tool / tty mode would. Usage: java Tests <repoRoot> <lab8|lab9|lab10|lab11|all>
 */
public class Tests {
    static PrintStream log;
    static int checks, failures;

    static void check(boolean ok, String what) {
        checks++;
        if (!ok) {
            failures++;
            if (failures < 30) log.println("FAIL: " + what);
        }
    }

    static void begin(String root, String dir, String title) throws Exception {
        log = new PrintStream(new FileOutputStream(root + "/" + dir + "/test-results.txt"), true) {
            @Override public void println(String s) { super.println(s); System.out.println(s); }
        };
        checks = 0; failures = 0;
        log.println("# " + title);
        log.println("# Simulator: Logisim 2.7.1 (com.cburch.logisim.circuit.Propagator), run " + new Date());
    }

    static void end(LogisimKit.Sim s) {
        List<String> w = s.widthProblems();
        for (String x : w) log.println("FAIL: " + x);
        check(w.isEmpty(), "no width-incompatibility (orange) wires");
        log.println(String.format("RESULT: %d checks, %d failures -> %s", checks, failures,
                failures == 0 ? "PASS" : "FAIL"));
        if (failures > 0) overallFail = true;
    }

    static boolean overallFail = false;

    // ------------------------------------------------------------------ Lab 8
    static void lab8(String root) throws Exception {
        String f = root + "/lab08-intro-logisim/lab08_rca8.circ";
        begin(root, "lab08-intro-logisim", "Lab 8 - FA0 full adder and 8-bit ripple-carry adder");
        LogisimKit.Sim fa = new LogisimKit.Sim(f, "FA0");
        log.println("FA0 truth table (from simulation):");
        log.println("Cin A B | Cout S");
        for (int r = 0; r < 8; r++) {
            int ci = r >> 2 & 1, a = r >> 1 & 1, b = r & 1;
            fa.set("Cin", ci); fa.set("A", a); fa.set("B", b); fa.propagate();
            long co = fa.get("Cout"), s = fa.get("S");
            log.println(String.format(" %d  %d %d |  %d   %d", ci, a, b, co, s));
            check(co == ((a + b + ci) >> 1) && s == ((a + b + ci) & 1), "FA0 row " + r);
        }
        end(fa);

        LogisimKit.Sim rca = new LogisimKit.Sim(f, "RCA8");
        int n = 0;
        for (int ci = 0; ci < 2; ci++)
            for (int a = 0; a < 256; a++)
                for (int b = 0; b < 256; b++) {
                    rca.set("A", a); rca.set("B", b); rca.set("Ci", ci); rca.propagate();
                    int sum = a + b + ci;
                    check(rca.get("S") == (sum & 0xFF) && rca.get("Co") == (sum >> 8),
                            "RCA8 " + a + "+" + b + "+" + ci);
                    n++;
                }
        log.println("RCA8: exhaustive test of all " + n + " input combinations (A, B, Ci)");
        int[][] ex = {{0, 0, 0}, {5, 3, 0}, {100, 27, 1}, {255, 1, 0}, {255, 255, 1}, {128, 128, 0}};
        for (int[] e : ex) {
            rca.set("A", e[0]); rca.set("B", e[1]); rca.set("Ci", e[2]); rca.propagate();
            log.println(String.format("  A=%3d B=%3d Ci=%d -> S=%3d Co=%d", e[0], e[1], e[2], rca.get("S"), rca.get("Co")));
        }
        end(rca);
    }

    // ------------------------------------------------------------------ Lab 9
    static void lab9(String root) throws Exception {
        String f = root + "/lab09-32bit-adder/lab09_adder32.circ";
        begin(root, "lab09-32bit-adder", "Lab 9 - 32-bit adder built from four 8-bit RCAs");
        LogisimKit.Sim s = new LogisimKit.Sim(f, "Adder32");
        long[] edge = {0, 1, 2, 0x7FFFFFFFL, 0x80000000L, 0xFFFFFFFFL, 0x000000FFL, 0x0000FF00L,
                0x00FF0000L, 0xFF000000L, 0x12345678L, 0x89ABCDEFL, 0x55555555L, 0xAAAAAAAAL};
        List<long[]> cases = new ArrayList<>();
        for (long a : edge) for (long b : edge) for (int ci = 0; ci < 2; ci++) cases.add(new long[]{a, b, ci});
        Random rnd = new Random(302);
        for (int i = 0; i < 20000; i++)
            cases.add(new long[]{rnd.nextInt() & 0xFFFFFFFFL, rnd.nextInt() & 0xFFFFFFFFL, rnd.nextInt(2)});
        for (long[] c : cases) {
            s.set("A", c[0]); s.set("B", c[1]); s.set("Ci", c[2]); s.propagate();
            long sum = c[0] + c[1] + c[2];
            check(s.get("S") == (sum & 0xFFFFFFFFL) && s.get("Co") == (sum >>> 32),
                    String.format("Adder32 %08x+%08x+%d", c[0], c[1], c[2]));
        }
        log.println("Adder32: " + cases.size() + " vectors (" + (edge.length * edge.length * 2)
                + " edge-case combinations + 20000 random, seed 302)");
        long[][] ex = {{0x12345678L, 0x11111111L, 0}, {0xFFFFFFFFL, 1, 0}, {0x7FFFFFFFL, 1, 0}, {1000000, 2345678, 1}};
        for (long[] e : ex) {
            s.set("A", e[0]); s.set("B", e[1]); s.set("Ci", e[2]); s.propagate();
            log.println(String.format("  A=0x%08X B=0x%08X Ci=%d -> S=0x%08X Co=%d", e[0], e[1], e[2], s.get("S"), s.get("Co")));
        }
        // sub-circuits shipped in the same file must still work
        LogisimKit.Sim r = new LogisimKit.Sim(f, "RCA8");
        for (int i = 0; i < 2000; i++) {
            int a = rnd.nextInt(256), b = rnd.nextInt(256), ci = rnd.nextInt(2);
            r.set("A", a); r.set("B", b); r.set("Ci", ci); r.propagate();
            check(r.get("S") == ((a + b + ci) & 0xFF) && r.get("Co") == ((a + b + ci) >> 8), "RCA8 in lab9 file");
        }
        log.println("RCA8 (inside the Lab 9 file): 2000 random vectors");
        end(s);
    }

    public static void main(String[] a) {
        try { run(a); } catch (Throwable t) { t.printStackTrace(); System.exit(2); }
    }

    static void run(String[] a) throws Exception {
        String root = a[0];
        Set<String> which = new HashSet<>(Arrays.asList(a).subList(1, a.length));
        boolean all = which.contains("all");
        if (all || which.contains("lab8")) lab8(root);
        if (all || which.contains("lab9")) lab9(root);
        if (all || which.contains("lab10")) RegFile.test(root);
        if (all || which.contains("lab11")) Alu.test(root);
        System.out.println(overallFail ? "SOME TESTS FAILED" : "ALL TESTS PASSED");
        System.exit(overallFail ? 1 : 0);
    }
}
