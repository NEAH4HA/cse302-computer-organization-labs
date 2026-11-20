import java.io.File;
import java.io.InputStream;
import java.util.*;

import com.cburch.logisim.circuit.*;
import com.cburch.logisim.comp.*;
import com.cburch.logisim.data.*;
import com.cburch.logisim.file.*;
import com.cburch.logisim.instance.*;
import com.cburch.logisim.proj.Project;
import com.cburch.logisim.std.wiring.Pin;
import com.cburch.logisim.tools.*;

/**
 * Thin helper over Logisim 2.7.1's own Java API.
 * Every component, wire and file here is created by Logisim's own classes:
 * the "plain" template a new GUI project starts from, the library component
 * factories, CircuitMutation (with Logisim's WireRepair) and Loader.save().
 */
public class LogisimKit {
    public final Loader loader;
    public final LogisimFile file;

    public LogisimKit() throws Exception {
        loader = new Loader(null);
        // Same template the GUI uses for File > New ("plain" template).
        InputStream in = Loader.class.getClassLoader()
                .getResourceAsStream("resources/logisim/default.templ");
        file = LogisimFile.load(in, loader);
    }

    public ComponentFactory factory(String lib, String name) {
        for (Library l : file.getLibraries()) {
            if (l.getName().equals(lib)) {
                Tool t = l.getTool(name);
                if (t instanceof AddTool) return ((AddTool) t).getFactory();
            }
        }
        throw new IllegalArgumentException("no tool " + lib + "/" + name);
    }

    /** Creates a new circuit, adds it to the project (Project > Add Circuit). */
    public Circuit newCircuit(String name) {
        Circuit c = new Circuit(name);
        file.addCircuit(c);
        return c;
    }

    /** Removes the template's empty "main" circuit after others exist. */
    public void dropTemplateMain(Circuit newMain) {
        file.setMainCircuit(newMain);
        Circuit m = file.getCircuit("main");
        if (m != null && m != newMain && m.getNonWires().isEmpty()) file.removeCircuit(m);
    }

    public boolean save(String path) {
        return loader.save(file, new File(path));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    static void applyAttrs(AttributeSet a, String... kv) {
        for (int i = 0; i < kv.length; i += 2) {
            Attribute at = a.getAttribute(kv[i]);
            if (at == null) throw new IllegalArgumentException("no attribute " + kv[i]);
            a.setValue(at, at.parse(kv[i + 1]));
        }
    }

    /** Per-circuit editing session; commit() runs one CircuitMutation. */
    public class Sheet {
        public final Circuit circ;
        final List<Component> comps = new ArrayList<>();
        final Set<Wire> wires = new LinkedHashSet<>();

        public Sheet(Circuit c) { circ = c; }

        public Component add(String lib, String name, int x, int y, String... kv) {
            return add(factory(lib, name), x, y, kv);
        }

        public Component add(ComponentFactory f, int x, int y, String... kv) {
            AttributeSet a = f.createAttributeSet();
            applyAttrs(a, kv);
            Component c = f.createComponent(Location.create(x, y), a);
            comps.add(c);
            return c;
        }

        public Component sub(Circuit sub, int x, int y, String... kv) {
            return add(sub.getSubcircuitFactory(), x, y, kv);
        }

        public Component pinIn(String label, int bits, int x, int y, String... kv) {
            String[] base = {"facing", "east", "output", "false", "tristate", "false", "width", "" + bits,
                    "label", label, "labelloc", "west"};
            return add("Wiring", "Pin", x, y, concat(base, kv));
        }

        public Component pinOut(String label, int bits, int x, int y, String... kv) {
            String[] base = {"facing", "west", "output", "true", "width", "" + bits,
                    "label", label, "labelloc", "east"};
            return add("Wiring", "Pin", x, y, concat(base, kv));
        }

        public Component tunnel(String label, int bits, Location at, String facing) {
            return add("Wiring", "Tunnel", at.getX(), at.getY(),
                    "facing", facing, "width", "" + bits, "label", label);
        }

        /**
         * Short wire from a port in direction dir ('N','S','E','W') and a tunnel at its end.
         * Returns the tunnel.
         */
        public Component stubTunnel(Location port, char dir, int len, String label, int bits) {
            int dx = dir == 'E' ? len : dir == 'W' ? -len : 0;
            int dy = dir == 'S' ? len : dir == 'N' ? -len : 0;
            Location e = port.translate(dx, dy);
            wire(port, e);
            String facing = dir == 'E' ? "west" : dir == 'W' ? "east" : dir == 'N' ? "south" : "north";
            return tunnel(label, bits, e, facing);
        }

        public void remove(Component c) { comps.remove(c); }

        /** Polyline through the given points; non-axis-aligned hops get an L (x first). */
        public void wire(Location... pts) {
            for (int i = 0; i + 1 < pts.length; i++) {
                Location a = pts[i], b = pts[i + 1];
                if (a.equals(b)) continue;
                if (a.getX() != b.getX() && a.getY() != b.getY()) {
                    Location m = Location.create(b.getX(), a.getY());
                    wires.add(Wire.create(a, m));
                    wires.add(Wire.create(m, b));
                } else {
                    wires.add(Wire.create(a, b));
                }
            }
        }

        public void commit() {
            CircuitMutation m = new CircuitMutation(circ);
            m.addAll(comps);
            m.addAll(wires);
            m.execute();
            comps.clear();
            wires.clear();
        }
    }

    public Sheet sheet(Circuit c) { return new Sheet(c); }

    static String[] concat(String[] a, String[] b) {
        String[] r = Arrays.copyOf(a, a.length + b.length);
        System.arraycopy(b, 0, r, a.length, b.length);
        return r;
    }

    public static Location L(int x, int y) { return Location.create(x, y); }

    public static Location end(Component c, int i) { return c.getEnd(i).getLocation(); }

    /** Port location of a subcircuit instance, looked up by the label of the inner pin. */
    public static Location port(Component inst, String pinLabel) {
        SubcircuitFactory f = (SubcircuitFactory) inst.getFactory();
        Direction facing = inst.getAttributeSet().getValue(StdAttr.FACING);
        SortedMap<Location, Instance> offs = f.getSubcircuit().getAppearance().getPortOffsets(facing);
        for (Map.Entry<Location, Instance> e : offs.entrySet()) {
            String lbl = e.getValue().getAttributeValue(StdAttr.LABEL);
            if (pinLabel.equals(lbl)) {
                return e.getKey().translate(inst.getLocation().getX(), inst.getLocation().getY());
            }
        }
        throw new IllegalArgumentException("no port " + pinLabel + " on " + f.getName());
    }

    // ------------------------------------------------------------------ simulation

    /** Opens a saved .circ with a fresh Loader and simulates it with Logisim's Propagator. */
    public static class Sim {
        public final LogisimFile file;
        public final Project proj;
        public final Circuit circ;
        public final CircuitState state;
        final Map<String, Component> pins = new HashMap<>();

        public Sim(String path, String circuitName) throws Exception {
            Loader ld = new Loader(null);
            file = ld.openLogisimFile(new File(path));
            proj = new Project(file);
            circ = circuitName == null ? file.getMainCircuit() : file.getCircuit(circuitName);
            if (circ == null) throw new IllegalStateException("circuit not found: " + circuitName);
            state = new CircuitState(proj, circ);
            for (Component c : circ.getNonWires()) {
                if (c.getFactory() instanceof Pin) {
                    String l = c.getAttributeSet().getValue(StdAttr.LABEL);
                    if (l != null && !l.isEmpty()) pins.put(l, c);
                }
            }
            state.getPropagator().propagate();
        }

        public Component pin(String l) {
            Component c = pins.get(l);
            if (c == null) throw new IllegalArgumentException("no pin " + l + " in " + circ.getName());
            return c;
        }

        public void set(String label, long v) {
            Component c = pin(label);
            int w = c.getAttributeSet().getValue(StdAttr.WIDTH).getWidth();
            InstanceState is = state.getInstanceState(c);
            Pin.FACTORY.setValue(is, Value.createKnown(BitWidth.create(w), (int) v));
            is.fireInvalidated(); // what the Poke tool does after changing a pin
        }

        public void propagate() {
            state.getPropagator().propagate();
            if (state.getPropagator().isOscillating()) throw new IllegalStateException("oscillation");
        }

        public Value raw(String label) {
            return Pin.FACTORY.getValue(state.getInstanceState(pin(label)));
        }

        /** Value of an output pin; throws if any bit is unknown (blue) or error (red). */
        public long get(String label) {
            Value v = raw(label);
            if (!v.isFullyDefined()) throw new IllegalStateException(label + " not fully defined: " + v);
            return v.toIntValue() & 0xFFFFFFFFL;
        }

        /** Same steps as Logisim's File > Export Image (gui/main/ExportImage.export). */
        public void exportPng(Circuit c, CircuitState st, String path, double scale) throws Exception {
            java.awt.image.BufferedImage probe = new java.awt.image.BufferedImage(1, 1, java.awt.image.BufferedImage.TYPE_INT_RGB);
            Bounds bds = c.getBounds(probe.getGraphics()).expand(20);
            int w = (int) Math.round(bds.getWidth() * scale), h = (int) Math.round(bds.getHeight() * scale);
            java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(w, h, java.awt.image.BufferedImage.TYPE_INT_RGB);
            java.awt.Graphics base = img.getGraphics();
            java.awt.Graphics g = base.create();
            g.setColor(java.awt.Color.white);
            g.fillRect(0, 0, w, h);
            g.setColor(java.awt.Color.black);
            ((java.awt.Graphics2D) g).setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
            ((java.awt.Graphics2D) g).scale(scale, scale);
            ((java.awt.Graphics2D) g).translate(-bds.getX(), -bds.getY());
            if (st == null) st = proj.getCircuitState(c);
            ComponentDrawContext ctx = new ComponentDrawContext(new javax.swing.JPanel(), c, st, base, g, false);
            c.draw(ctx, null);
            javax.imageio.ImageIO.write(img, "PNG", new File(path));
        }

        /** Width-incompatibility check (orange wires) for every circuit in the file. */
        public List<String> widthProblems() {
            List<String> r = new ArrayList<>();
            for (Circuit c : file.getCircuits()) {
                Set<WidthIncompatibilityData> ws = c.getWidthIncompatibilityData();
                if (ws == null) continue;
                for (WidthIncompatibilityData d : ws) {
                    r.add(c.getName() + ": width mismatch at " + d.getPoint(0));
                }
            }
            return r;
        }
    }
}
