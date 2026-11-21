import com.cburch.logisim.circuit.Circuit;

/** Debug/export: java Snap file.circ outdir [scale] — exports every circuit as PNG. */
public class Snap {
    public static void main(String[] a) throws Exception {
        LogisimKit.Sim s = new LogisimKit.Sim(a[0], null);
        double sc = a.length > 2 ? Double.parseDouble(a[2]) : 1.0;
        for (Circuit c : s.file.getCircuits()) {
            String p = a[1] + "/" + c.getName().replaceAll("[^A-Za-z0-9_-]", "_") + ".png";
            com.cburch.logisim.circuit.CircuitState st = c == s.circ ? s.state : new com.cburch.logisim.circuit.CircuitState(s.proj, c);
            st.getPropagator().propagate(); // show settled wire values, as when the circuit is opened in the GUI
            s.exportPng(c, st, p, sc);
            System.out.println(p);
        }
        for (String w : s.widthProblems()) System.out.println("WIDTH: " + w);
        System.exit(0);
    }
}
