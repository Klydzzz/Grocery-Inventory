import java.time.LocalDate;
import java.util.*;


// File overview: Regression-style test runner for the project data structures.
// This class keeps the related logic together for easier reading and maintenance.
public class TestCaseRunner {

    public static class Result {
        public final String id;
        public final String name;
        public final boolean passed;
        public final String details;

        Result(String id, String name, boolean passed, String details) {
            this.id = id;
            this.name = name;
            this.passed = passed;
            this.details = details;
        }
    }

    private interface Test {
        String run();
    }

    private static class Case {
        final String id;
        final String name;
        final Test test;

        Case(String id, String name, Test test) {
            this.id = id;
            this.name = name;
            this.test = test;
        }
    }

    private static final LocalDate BASE_DATE = LocalDate.of(2030, 1, 1);

    public static List<Result> run() {
        List<Case> cases = Arrays.asList(
                new Case("T01", "Empty structure case", TestCaseRunner::emptyStructures),
                new Case("T02", "Single record case", TestCaseRunner::singleRecord),
                new Case("T03", "Duplicate record", TestCaseRunner::duplicateRecord),
                new Case("T04", "Search for existing key", TestCaseRunner::searchExistingKey),
                new Case("T05", "Search missing key", TestCaseRunner::searchMissingKey),
                new Case("T06", "Hash collision", TestCaseRunner::hashCollision),
                new Case("T07", "BFS reachable path", TestCaseRunner::bfsReachablePath),
                new Case("T08", "DFS traversal", TestCaseRunner::dfsTraversal),
                new Case("T09", "BST deletion", TestCaseRunner::bstDeletion),
                new Case("T10", "Heap extraction", TestCaseRunner::heapExtraction)
        );

        List<Result> results = new ArrayList<>();
        for (Case testCase : cases) {
            try {
                results.add(new Result(testCase.id, testCase.name, true, testCase.test.run()));
            } catch (AssertionError | RuntimeException ex) {
                String details = ex.getMessage();
                if (details == null || details.trim().isEmpty()) details = ex.getClass().getSimpleName();
                results.add(new Result(testCase.id, testCase.name, false, details));
            }
        }
        return results;
    }

    private static String emptyStructures() {
        InventoryList list = new InventoryList();
        InventoryTable table = new InventoryTable();
        ExpiryTree tree = new ExpiryTree();
        ProductGraph graph = new ProductGraph();
        RestockHeap heap = new RestockHeap();
        check(list.isEmpty() && list.size() == 0, "Inventory list should be empty.");
        check(table.size() == 0 && table.get("missing") == null, "Hash table should be empty.");
        check(tree.size() == 0 && tree.inOrder().isEmpty(), "Expiry tree should be empty.");
        check(graph.productCount() == 0 && graph.bfs("missing", 2).isEmpty(),
                "Product graph should be empty.");
        check(heap.isEmpty() && heap.peek() == null && heap.extractMax() == null,
                "Restock heap should be empty.");
        return "All five structures start empty and return no missing records.";
    }

    private static String singleRecord() {
        Item item = item("ONE", "Single item", 4, BASE_DATE);
        InventoryList list = new InventoryList();
        InventoryTable table = new InventoryTable();
        ExpiryTree tree = new ExpiryTree();
        ProductGraph graph = new ProductGraph();
        RestockHeap heap = new RestockHeap();
        list.addLast(item);
        table.put(item);
        tree.insert(item);
        graph.recordBasket(Collections.singletonList(item.getCode()));
        heap.insert(RestockHeap.entryFor(item, BASE_DATE.minusDays(1)));

        check(list.size() == 1 && list.find("ONE") == item, "List should contain the item.");
        check(table.size() == 1 && table.get("ONE") == item, "Hash table should contain the item.");
        check(tree.size() == 1 && tree.find(BASE_DATE, "ONE") == item, "Tree should contain the item.");
        check(graph.productCount() == 1, "Graph should contain the single product.");
        check(heap.size() == 1 && heap.peek().item == item, "Heap should contain the item.");
        return "One record is stored and retrievable in each structure.";
    }

    private static String duplicateRecord() {
        InventoryTable table = new InventoryTable();
        Item original = item("DUP", "Original", 1, BASE_DATE);
        Item replacement = item("DUP", "Replacement", 2, BASE_DATE.plusDays(1));
        table.put(original);
        table.put(replacement);
        check(table.size() == 1, "A duplicate key should not increase the table size.");
        check(table.get("DUP") == replacement, "A duplicate key should replace its existing record.");
        return "Putting an existing key replaces its value without adding another record.";
    }

    private static String searchExistingKey() {
        InventoryTable table = new InventoryTable();
        Item expected = item("FOUND", "Existing item", 2, BASE_DATE);
        table.put(expected);
        check(table.get("FOUND") == expected, "Existing key should return its record.");
        return "An existing key returns the stored record.";
    }

    private static String searchMissingKey() {
        InventoryList list = new InventoryList();
        InventoryTable table = new InventoryTable();
        Item present = item("PRESENT", "Present item", 2, BASE_DATE);
        list.addLast(present);
        table.put(present);
        check(list.find("ABSENT") == null, "List search should return null for a missing key.");
        check(table.get("ABSENT") == null, "Hash-table search should return null for a missing key.");
        return "Missing keys return no record from either lookup structure.";
    }

    private static String hashCollision() {
        InventoryTable table = new InventoryTable();
        Item first = item("Aa", "First colliding item", 1, BASE_DATE);
        Item second = item("BB", "Second colliding item", 1, BASE_DATE.plusDays(1));
        check(first.getCode().hashCode() == second.getCode().hashCode(),
                "Test keys must have the same hash code.");
        table.put(first);
        table.put(second);
        check(table.size() == 2, "Colliding keys should both be stored.");
        check(table.get("Aa") == first && table.get("BB") == second,
                "Both colliding keys should remain independently retrievable.");
        return "Keys \"Aa\" and \"BB\" share a hash code and both remain retrievable.";
    }

    private static String bfsReachablePath() {
        ProductGraph graph = new ProductGraph();
        graph.recordBasket(Arrays.asList("A", "B"));
        graph.recordBasket(Arrays.asList("B", "C"));
        List<ProductGraph.Reach> reachable = graph.bfs("A", 2);
        check(reachable.size() == 2, "BFS should reach both connected products.");
        check("B".equals(reachable.get(0).code) && reachable.get(0).hops == 1,
                "B should be reached first in one hop.");
        check("C".equals(reachable.get(1).code) && reachable.get(1).hops == 2,
                "C should be reached in two hops.");
        return "BFS visits B at 1 hop, then C at 2 hops along A-B-C.";
    }

    private static String dfsTraversal() {
        ProductGraph graph = new ProductGraph();
        graph.recordBasket(Arrays.asList("A", "B"));
        graph.recordBasket(Arrays.asList("B", "C"));
        graph.recordBasket(Collections.singletonList("ISOLATED"));
        List<List<String>> groups = graph.groups(1);
        check(groups.size() == 1, "DFS should return one connected group.");
        check(new HashSet<>(groups.get(0)).equals(new HashSet<>(Arrays.asList("A", "B", "C"))),
                "DFS should visit every product in the connected component.");
        return "DFS finds the connected group {A, B, C} and excludes an isolated product.";
    }

    private static String bstDeletion() {
        ExpiryTree tree = new ExpiryTree();
        Item root = item("M", "Root", 1, BASE_DATE.plusDays(2));
        Item left = item("A", "Left", 1, BASE_DATE.plusDays(1));
        Item right = item("Z", "Right", 1, BASE_DATE.plusDays(3));
        tree.insert(root);
        tree.insert(left);
        tree.insert(right);
        check(tree.remove(root), "Existing root with two children should be removed.");
        check(tree.size() == 2, "Tree size should decrease after deletion.");
        check(tree.find(root.getExpiryDate(), root.getCode()) == null, "Deleted item should not be found.");
        List<Item> remaining = tree.inOrder();
        check(remaining.size() == 2 && remaining.get(0) == left && remaining.get(1) == right,
                "Remaining items should preserve sorted order.");
        return "Deleting a two-child BST node preserves size, lookup, and in-order order.";
    }

    private static String heapExtraction() {
        RestockHeap heap = new RestockHeap();
        Item urgent = item("URGENT", "Urgent", 0, BASE_DATE);
        Item medium = item("MEDIUM", "Medium", 5, BASE_DATE);
        Item low = item("LOW", "Low", 10, BASE_DATE);
        LocalDate today = BASE_DATE.minusDays(100);
        heap.insert(RestockHeap.entryFor(low, today));
        heap.insert(RestockHeap.entryFor(medium, today));
        heap.insert(RestockHeap.entryFor(urgent, today));
        check(heap.extractMax().item == urgent, "Highest-priority item should be extracted first.");
        check(heap.extractMax().item == medium, "Medium-priority item should be extracted second.");
        check(heap.extractMax().item == low, "Lowest-priority item should be extracted last.");
        check(heap.isEmpty() && heap.extractMax() == null, "Heap should be empty after extraction.");
        return "Items extract in descending priority order and the empty heap returns null.";
    }

    private static Item item(String code, String name, int quantity, LocalDate expiry) {
        return new Item(code, name, 0, quantity, 10, expiry, 1.0);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}