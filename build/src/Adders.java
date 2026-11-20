import java.util.*;

import com.cburch.logisim.analyze.model.*;
import com.cburch.logisim.circuit.*;
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.data.*;
import com.cburch.logisim.instance.StdAttr;
import com.cburch.logisim.std.gates.CircuitBuilder;


/** Lab 8 (FA0 + 8-bit RCA) and Lab 9 (32-bit adder from four RCAs). */
public class Adders {

    /**
     * Lab 8 "Circuit Analysis" flow: Window > Combinational Analysis, inputs Cin,A,B,
     * outputs Cout,S, fill the truth table, press "Build Circuit", name it FA0.
     * Then, as the lab says, Ci faces south and Co faces north.
     */
    public static Circuit buildFA0(LogisimKit k) {
        AnalyzerModel model = new AnalyzerModel();
        model.setVariables(Arrays.asList("Cin", "A", "B"), Arrays.asList("Cout", "S"));
        TruthTable tt = model.getTruthTable();
        int rows = tt.getRowCount();
        Entry[] cout = new Entry[rows], s = new Entry[rows];
        for (int r = 0; r < rows; r++) {
            int ones = 0;
            for (int c = 0; c < 3; c++) if (TruthTable.isInputSet(r, c, 3)) ones++;
            cout[r] = ones >= 2 ? Entry.ONE : Entry.ZERO;
            s[r] = (ones & 1) == 1 ? Entry.ONE : Entry.ZERO;
        }
        tt.setOutputColumn(tt.getOutputIndex("Cout"), cout);
        tt.setOutputColumn(tt.getOutputIndex("S"), s);

        Circuit fa = k.newCircuit("FA0");
        // Exactly what BuildCircuitButton.performAction does (two-input gates off, NANDs off).
        CircuitMutation xn = CircuitBuilder.build(fa, model, false, false);
        xn.execute();

        // Re-orient Ci (south) and Co (north) for use as a block (Figure 8.5).
        CircuitMutation re = new CircuitMutation(fa);
        for (Component c : fa.getNonWires()) {
            String lbl = c.getAttributeSet().getValue(StdAttr.LABEL);
            if ("Cin".equals(lbl)) re.set(c, StdAttr.FACING, Direction.SOUTH);
            if ("Cout".equals(lbl)) re.set(c, StdAttr.FACING, Direction.NORTH);
        }
        re.execute();
        return fa;
    }

    /** Routes bit k from a[k] to b[k] through vertical channel chanX + k*step. */
    static void route(LogisimKit.Sheet sh, List<Location> a, List<Location> b, int chanX, int step) {
        for (int i = 0; i < a.size(); i++) {
            int cx = chanX + i * step;
            Location p = a.get(i), q = b.get(i);
            sh.wire(p, LogisimKit.L(cx, p.getY()), LogisimKit.L(cx, q.getY()), q);
        }
    }

    static List<Location> splitEnds(Component sp, int n) {
        List<Location> r = new ArrayList<>();
        for (int i = 1; i <= n; i++) r.add(LogisimKit.end(sp, i)); // end 0 = combined bus
        return r;
    }

    /**
     * Generic ripple chain: n instances of `cell` (ports A,B,Ci,Co,S with the given
     * port names), stacked vertically, carry flowing top (Ci) to bottom (Co).
     * Splitters: two east-facing on A and B, one west-facing on S.
     */
    static Circuit buildChain(LogisimKit k, String name, Circuit cell, int n, int cellBits,
                              String pa, String pb, String pci, String pco, String ps) {
        int total = n * cellBits;
        Circuit c = k.newCircuit(name);
        LogisimKit.Sheet sh = k.sheet(c);

        int cellX = 600, top = 120, pitch = 100;
        Component[] cells = new Component[n];
        for (int i = 0; i < n; i++) cells[i] = sh.sub(cell, cellX, top + i * pitch);
        // carry chain
        for (int i = 0; i + 1 < n; i++) sh.wire(LogisimKit.port(cells[i], pco), LogisimKit.port(cells[i + 1], pci));

        int bottom = top + n * pitch + 60;
        // Ci input above the first cell, Co output below the last one
        Location ci0 = LogisimKit.port(cells[0], pci);
        Component ciPin = sh.pinIn("Ci", 1, ci0.getX(), ci0.getY() - 40, "facing", "south", "labelloc", "north");
        sh.wire(LogisimKit.end(ciPin, 0), ci0);
        Location coN = LogisimKit.port(cells[n - 1], pco);
        Component coPin = sh.pinOut("Co", 1, coN.getX(), bottom + 200, "facing", "north", "labelloc", "south");
        sh.wire(coN, LogisimKit.end(coPin, 0));

        // B splitter (upper of the two input buses), A splitter below it; both east-facing
        Component pinB = sh.pinIn("B", total, 60, bottom + 20);
        Component spB = sh.add("Wiring", "Splitter", 100, bottom + 20,
                "facing", "east", "fanout", "" + n, "incoming", "" + total, "appear", "left");
        sh.wire(LogisimKit.end(pinB, 0), LogisimKit.end(spB, 0));

        int aY = bottom + 20 + n * 10 + 60;
        Component pinA = sh.pinIn("A", total, 60, aY);
        Component spA = sh.add("Wiring", "Splitter", 100, aY,
                "facing", "east", "fanout", "" + n, "incoming", "" + total, "appear", "left");
        sh.wire(LogisimKit.end(pinA, 0), LogisimKit.end(spA, 0));

        List<Location> cellA = new ArrayList<>(), cellB = new ArrayList<>(), cellS = new ArrayList<>();
        for (Component ce : cells) {
            cellA.add(LogisimKit.port(ce, pa));
            cellB.add(LogisimKit.port(ce, pb));
            cellS.add(LogisimKit.port(ce, ps));
        }
        route(sh, splitEnds(spB, n), cellB, 200, 10);
        route(sh, splitEnds(spA, n), cellA, 200 + n * 10 + 40, 10);

        // S: west-facing splitter at the right, below the stack
        int sY = bottom + 20;
        Component spS = sh.add("Wiring", "Splitter", 1000, sY,
                "facing", "west", "fanout", "" + n, "incoming", "" + total, "appear", "left");
        Component pinS = sh.pinOut("S", total, 1040, sY);
        sh.wire(LogisimKit.end(spS, 0), LogisimKit.end(pinS, 0));
        route(sh, cellS, splitEnds(spS, n), 760, 10);

        sh.commit();
        return c;
    }

    public static Circuit buildRCA8(LogisimKit k, Circuit fa) {
        // Ci pin faces south and Co faces north, so as a block (Lab 9) the carries
        // sit on the top/bottom edges exactly like FA0.
        return buildChain(k, "RCA8", fa, 8, 1, "A", "B", "Cin", "Cout", "S");
    }

    public static Circuit buildAdder32(LogisimKit k, Circuit rca8) {
        return buildChain(k, "Adder32", rca8, 4, 8, "A", "B", "Ci", "Co", "S");
    }

    public static void main(String[] args) throws Exception {
        String out = args[0];
        // ---- Lab 8 file: FA0 + RCA8 (main)
        LogisimKit k8 = new LogisimKit();
        Circuit fa = buildFA0(k8);
        Circuit rca = buildRCA8(k8, fa);
        k8.dropTemplateMain(rca);
        System.out.println("lab8 saved: " + k8.save(out + "/lab08-intro-logisim/lab08_rca8.circ"));

        // ---- Lab 9 file: FA0 + RCA8 + Adder32 (main)
        LogisimKit k9 = new LogisimKit();
        Circuit fa9 = buildFA0(k9);
        Circuit rca9 = buildRCA8(k9, fa9);
        Circuit add32 = buildAdder32(k9, rca9);
        k9.dropTemplateMain(add32);
        System.out.println("lab9 saved: " + k9.save(out + "/lab09-32bit-adder/lab09_adder32.circ"));
    }
}
