import java.util.*;


// File overview: Relationship graph for co-purchased products and recommendations.
// This class keeps the related logic together for easier reading and maintenance.
public class ProductGraph {

    public static class Reach {
        public final String code;
        public final int hops;
        public final int strength;

        Reach(String code, int hops, int strength) {
            this.code = code;
            this.hops = hops;
            this.strength = strength;
        }
    }

    private String[] codes = new String[16];
    private int[][] weight = new int[16][16];
    private int count = 0;

    public void recordBasket(List<String> basket) {
        int n = basket.size();
        int[] idx = new int[n];
        for (int i = 0; i < n; i++) idx[i] = nodeFor(basket.get(i));

        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                if (idx[i] == idx[j]) continue;
                weight[idx[i]][idx[j]]++;
                weight[idx[j]][idx[i]]++;
            }
        }
    }

    public void removeProduct(String code) {
        int a = indexOf(code);
        if (a < 0) return;
        for (int b = 0; b < count; b++) {
            weight[a][b] = 0;
            weight[b][a] = 0;
        }
        codes[a] = null;
    }

    public int strengthBetween(String codeA, String codeB) {
        int a = indexOf(codeA), b = indexOf(codeB);
        return (a < 0 || b < 0) ? 0 : weight[a][b];
    }

    public int productCount() {
        int n = 0;
        for (int i = 0; i < count; i++) if (codes[i] != null) n++;
        return n;
    }

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
            if (hops[a] == maxHops) continue;
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
                int a = stack.pop();
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

    private int indexOf(String code) {
        for (int i = 0; i < count; i++) {
            if (code.equals(codes[i])) return i;
        }
        return -1;
    }

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

    private void grow() {
        int capacity = codes.length * 2;
        codes = Arrays.copyOf(codes, capacity);
        int[][] bigger = new int[capacity][capacity];
        for (int i = 0; i < count; i++) System.arraycopy(weight[i], 0, bigger[i], 0, count);
        weight = bigger;
    }

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