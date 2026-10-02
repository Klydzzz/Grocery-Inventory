import java.util.*;

/**
 * "Bought together" graph.
 *
 *   node = a product (identified by its product code)
 *   edge = two products were in the same basket; the edge's weight is how many
 *          times that happened (so a heavier edge = a stronger link)
 *
 * It is stored as an ADJACENCY MATRIX (a 2D array): weight[a][b] is the number of
 * baskets that contained both product a and product b. A weight of 0 means no edge.
 * The graph is undirected, so weight[a][b] always equals weight[b][a].
 *
 * Algorithms:
 *   BFS (uses a queue)  -> products within N links of a product, nearest first
 *   DFS (uses a stack)  -> groups of products that are connected to each other
 */
public class ProductGraph {

    /** One product found by a BFS. */
    public static class Reach {
        public final String code;
        public final int hops;        // 1 = bought together directly, 2 = bought with something bought with it...
        public final int strength;    // times the link it was reached through was bought together

        Reach(String code, int hops, int strength) {
            this.code = code;
            this.hops = hops;
            this.strength = strength;
        }
    }

    private String[] codes = new String[16];          // codes[i] = product code of node i (null = free slot)
    private int[][] weight = new int[16][16];         // the adjacency matrix
    private int count = 0;                            // slots in use (including freed ones)

    // ------------------------------------------------------------ building

    /** Records one sale: every pair of products in the basket gets linked (or linked more strongly). */
    public void recordBasket(List<String> basket) {
        int n = basket.size();
        int[] idx = new int[n];
        for (int i = 0; i < n; i++) idx[i] = nodeFor(basket.get(i));   // may grow the arrays, so do this first

        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                if (idx[i] == idx[j]) continue;
                weight[idx[i]][idx[j]]++;
                weight[idx[j]][idx[i]]++;
            }
        }
    }

    /** Removes a product and all its links (used when a product is deleted). */
    public void removeProduct(String code) {
        int a = indexOf(code);
        if (a < 0) return;
        for (int b = 0; b < count; b++) {
            weight[a][b] = 0;
            weight[b][a] = 0;
        }
        codes[a] = null;
    }

    /** How many baskets contained both products (0 if never together). */
    public int strengthBetween(String codeA, String codeB) {
        int a = indexOf(codeA), b = indexOf(codeB);
        return (a < 0 || b < 0) ? 0 : weight[a][b];
    }

    /** Number of products that have at least one link. */
    public int productCount() {
        int n = 0;
        for (int i = 0; i < count; i++) if (codes[i] != null) n++;
        return n;
    }

    // ----------------------------------------------------------------- BFS

    /**
     * Breadth-first search from a product. Visits everything directly linked first (1 hop),
     * then everything linked to those (2 hops), and so on up to maxHops. Because it goes
     * level by level, the first time it reaches a product is the fewest-hops route.
     * The result is in BFS order: nearest products first.
     */
    public List<Reach> bfs(String startCode, int maxHops) {
        List<Reach> found = new ArrayList<>();
        int start = indexOf(startCode);
        if (start < 0) return found;

        boolean[] seen = new boolean[count];
        int[] hops = new int[count];
        IntQueue queue = new IntQueue();
        seen[start] = true;
        queue.enqueue(start);

        while (!queue.isEmpty()) {
            int a = queue.dequeue();
            if (hops[a] == maxHops) continue;                 // don't go further than maxHops
            for (int b = 0; b < count; b++) {
                if (weight[a][b] > 0 && !seen[b]) {
                    seen[b] = true;
                    hops[b] = hops[a] + 1;
                    found.add(new Reach(codes[b], hops[b], weight[a][b]));
                    queue.enqueue(b);
                }
            }
        }
        return found;
    }

    // ----------------------------------------------------------------- DFS

    /**
     * Depth-first search to find GROUPS: sets of products that are all connected to each
     * other through links of at least minStrength. Products that are alone are left out.
     * Raising minStrength ignores one-off combinations and splits big groups into tighter ones.
     */
    public List<List<String>> groups(int minStrength) {
        minStrength = Math.max(1, minStrength);
        List<List<String>> result = new ArrayList<>();
        boolean[] seen = new boolean[count];

        for (int s = 0; s < count; s++) {
            if (codes[s] == null || seen[s]) continue;

            List<String> group = new ArrayList<>();
            IntStack stack = new IntStack();
            seen[s] = true;
            stack.push(s);
            while (!stack.isEmpty()) {
                int a = stack.pop();                          // dive into the most recently found product
                group.add(codes[a]);
                for (int b = 0; b < count; b++) {
                    if (weight[a][b] >= minStrength && !seen[b]) {
                        seen[b] = true;
                        stack.push(b);
                    }
                }
            }
            if (group.size() >= 2) result.add(group);
        }
        return result;
    }

    // ------------------------------------------------------------- helpers

    private int indexOf(String code) {
        for (int i = 0; i < count; i++) {
            if (code.equals(codes[i])) return i;
        }
        return -1;
    }

    /** Index of the product's node, creating the node if it is new (re-using a freed slot first). */
    private int nodeFor(String code) {
        int i = indexOf(code);
        if (i >= 0) return i;
        for (i = 0; i < count; i++) {
            if (codes[i] == null) {
                codes[i] = code;
                return i;
            }
        }
        if (count == codes.length) grow();
        codes[count] = code;
        return count++;
    }

    /** Doubles the matrix size and copies the old weights across. */
    private void grow() {
        int capacity = codes.length * 2;
        codes = Arrays.copyOf(codes, capacity);
        int[][] bigger = new int[capacity][capacity];
        for (int i = 0; i < count; i++) System.arraycopy(weight[i], 0, bigger[i], 0, count);
        weight = bigger;
    }

    // ------------------------------------------- small custom queue and stack

    /** First in, first out (linked nodes). BFS uses this to visit products level by level. */
    private static class IntQueue {
        private static class Node {
            final int value;
            Node next;
            Node(int value) { this.value = value; }
        }
        private Node head, tail;

        void enqueue(int value) {
            Node n = new Node(value);
            if (tail == null) head = tail = n;
            else { tail.next = n; tail = n; }
        }

        int dequeue() {
            int v = head.value;
            head = head.next;
            if (head == null) tail = null;
            return v;
        }

        boolean isEmpty() { return head == null; }
    }

    /** Last in, first out (growable array). DFS uses this to dive deep before backing up. */
    private static class IntStack {
        private int[] data = new int[16];
        private int size = 0;

        void push(int value) {
            if (size == data.length) data = Arrays.copyOf(data, size * 2);
            data[size++] = value;
        }

        int pop() { return data[--size]; }

        boolean isEmpty() { return size == 0; }
    }
}
