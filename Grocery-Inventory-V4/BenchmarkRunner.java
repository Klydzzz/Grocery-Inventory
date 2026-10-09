import java.time.*;
import java.util.*;
import java.util.function.Consumer;


// File overview: Benchmark harness for comparing data structure operations.
// This class keeps the related logic together for easier reading and maintenance.
public class BenchmarkRunner {

    public static final int[] SIZES = {100, 500, 1000, 5000};
    private static final int WARMUP = 3;
    private static final int REPS = 7;
    private static final int QUERIES = 200;
    private static final int DELETES = 100;
    private static final long SEED = 42;

    public static class Row {
        public final int size;
        public final String operation, structure;
        public final double timeNs, comparisons, movements;
        public String observation = "";

        Row(int size, String operation, String structure, double timeNs, double comparisons, double movements) {
            this.size = size;
            this.operation = operation;
            this.structure = structure;
            this.timeNs = timeNs;
            this.comparisons = comparisons;
            this.movements = movements;
        }

        public String timeText() {
            return String.format("%,.0f", timeNs);
        }

        public String countsText() {
            return (movements > 0)
                    ? String.format("%,.0f cmp / %,.0f mov", comparisons, movements)
                    : String.format("%,.1f cmp", comparisons);
        }
    }

    private final List<Row> rows = new ArrayList<>();
    private final Consumer<String> progress;

    private Item[] items;
    private Item[] queries;
    private Item[] toDelete;

    private InventoryList list;
    private InventoryTable table;
    private ExpiryTree tree;

    private long cmp, mov, sink;

    private BenchmarkRunner(Consumer<String> progress) {
        this.progress = progress;
    }

    public static List<Row> run(Consumer<String> progress) {
        return new BenchmarkRunner(progress).runAll();
    }

    private List<Row> runAll() {
        for (int n : SIZES) {
            progress.accept("Testing " + String.format("%,d", n) + " items...");
            makeData(n);
            searchByCode(n);
            searchByDate(n);
            insert(n);
            delete(n);
            sort(n);
        }
        addObservations();
        return rows;
    }

    private void makeData(int n) {
        Random rnd = new Random(SEED);
        Item[] ordered = new Item[n];
        LocalDate base = LocalDate.of(2026, 1, 1);
        for (int i = 0; i < n; i++) {
            ordered[i] = new Item(String.format("BEN-%05d", i + 1), "Test item " + (i + 1), i % 8,
                    rnd.nextInt(100), 5, base.plusDays(rnd.nextInt(1095)), 10 + rnd.nextInt(500));
        }
        List<Item> shuffled = new ArrayList<>(Arrays.asList(ordered));
        Collections.shuffle(shuffled, rnd);
        items = shuffled.toArray(new Item[0]);

        queries = new Item[QUERIES];
        for (int i = 0; i < QUERIES; i++) queries[i] = items[rnd.nextInt(n)];

        Collections.shuffle(shuffled, rnd);
        toDelete = shuffled.subList(0, Math.min(DELETES, n)).toArray(new Item[0]);
    }

    private void buildAll() {
        list = new InventoryList();
        table = new InventoryTable();
        tree = new ExpiryTree();
        for (Item it : items) {
            list.addLast(it);
            table.put(it);
            tree.insert(it);
        }
        list.comparisons = 0;
        table.comparisons = 0;
        tree.comparisons = 0;
    }

    private void searchByCode(int n) {
        String op = "Search by product code (per lookup)";
        bench(n, op, "Linked list (linear search)", QUERIES, this::buildAll, () -> {
            for (Item q : queries) sink += list.find(q.getCode()).getQuantity();
            cmp = list.comparisons;
        });
        bench(n, op, "Hash table", QUERIES, this::buildAll, () -> {
            for (Item q : queries) sink += table.get(q.getCode()).getQuantity();
            cmp = table.comparisons;
        });
    }

    private void searchByDate(int n) {
        String op = "Search by expiry date + code (per lookup)";
        bench(n, op, "Linked list (linear search)", QUERIES, this::buildAll, () -> {
            for (Item q : queries) sink += list.findByDateAndCode(q.getExpiryDate(), q.getCode()).getQuantity();
            cmp = list.comparisons;
        });
        bench(n, op, "BST (binary search)", QUERIES, this::buildAll, () -> {
            for (Item q : queries) sink += tree.find(q.getExpiryDate(), q.getCode()).getQuantity();
            cmp = tree.comparisons;
        });
    }

    private void insert(int n) {
        String op = "Insert all items (total)";
        bench(n, op, "Linked list (add at end)", 1, () -> list = new InventoryList(), () -> {
            for (Item it : items) list.addLast(it);
            sink += list.size();
            cmp = list.comparisons;
        });
        bench(n, op, "Hash table", 1, () -> table = new InventoryTable(), () -> {
            for (Item it : items) table.put(it);
            sink += table.size();
            cmp = table.comparisons;
        });
        bench(n, op, "BST", 1, () -> tree = new ExpiryTree(), () -> {
            for (Item it : items) tree.insert(it);
            sink += tree.size();
            cmp = tree.comparisons;
        });
    }

    private void delete(int n) {
        String op = "Delete by product code (per delete)";
        int d = toDelete.length;
        bench(n, op, "Linked list (scan, then unlink)", d, this::buildAll, () -> {
            for (Item it : toDelete) list.remove(it);
            sink += list.size();
            cmp = list.comparisons;
        });
        bench(n, op, "Hash table", d, this::buildAll, () -> {
            for (Item it : toDelete) table.remove(it.getCode());
            sink += table.size();
            cmp = table.comparisons;
        });
        bench(n, op, "BST", d, this::buildAll, () -> {
            for (Item it : toDelete) tree.remove(it);
            sink += tree.size();
            cmp = tree.comparisons;
        });
    }

    private void sort(int n) {
        String op = "Sort by expiry date (total)";
        final Item[][] work = new Item[1][];
        for (ItemSorter.Algorithm algo : new ItemSorter.Algorithm[]{
                ItemSorter.Algorithm.BUBBLE, ItemSorter.Algorithm.SELECTION, ItemSorter.Algorithm.INSERTION}) {
            bench(n, op, algo.label, 1, () -> work[0] = items.clone(), () -> {
                ItemSorter.Result r = ItemSorter.sort(work[0], ItemSorter.BY_EXPIRY, algo);
                sink += work[0][0].getQuantity();
                cmp = r.comparisons;
                mov = r.movements;
            });
        }
        bench(n, op, "BST (insert all + in-order)", 1, () -> tree = new ExpiryTree(), () -> {
            for (Item it : items) tree.insert(it);
            sink += tree.inOrder().size();
            cmp = tree.comparisons;
            mov = items.length;
        });
    }

    private void bench(int n, String operation, String structure, int perOps, Runnable setup, Runnable run) {
        long[] times = new long[REPS];
        long cmpTotal = 0, movTotal = 0;
        for (int r = 0; r < WARMUP + REPS; r++) {
            setup.run();
            cmp = 0;
            mov = 0;
            long t0 = System.nanoTime();
            run.run();
            long elapsed = System.nanoTime() - t0;
            if (r >= WARMUP) times[r - WARMUP] = elapsed;
            cmpTotal = cmp;
            movTotal = mov;
        }
        Arrays.sort(times);
        rows.add(new Row(n, operation, structure, (double) times[REPS / 2] / perOps,
                (double) cmpTotal / perOps, (double) movTotal / perOps));
    }

    private void addObservations() {
        int first = SIZES[0], last = SIZES[SIZES.length - 1];

        for (Row r : rows) {
            Row fastest = r;
            for (Row o : rows) {
                if (o.size == r.size && o.operation.equals(r.operation) && o.timeNs < fastest.timeNs) fastest = o;
            }
            if (fastest == r) {
                r.observation = "Fastest for this operation.";
            } else {
                r.observation = String.format("%s slower than %s.", times(r.timeNs / fastest.timeNs), fastest.structure);
            }

            if (r.size == last && r.comparisons > 0) {
                for (Row o : rows) {
                    if (o.size == first && o.operation.equals(r.operation) && o.structure.equals(r.structure)
                            && o.comparisons > 0) {
                        r.observation += String.format(" Comparisons grew %s while the size grew %s.",
                                times(r.comparisons / o.comparisons), times((double) last / first));
                    }
                }
            }
        }
    }

    private static String times(double x) {
        return (x >= 10) ? String.format("%,.0fx", x) : String.format("%.1fx", x);
    }
}
