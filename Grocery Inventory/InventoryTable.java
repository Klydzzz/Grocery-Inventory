import java.util.ArrayList;
import java.util.List;

/**
 * Hash table storing product code -> Item pairs using separate chaining.
 * Average time complexity is O(1) for put/get/remove operations.
 */
public class InventoryTable {

    private static class Node {
        final String key;
        Item value;
        Node next;

        Node(String key, Item value, Node next) {
            this.key = key;
            this.value = value;
            this.next = next;
        }
    }

    private static final int INITIAL_CAPACITY = 16;
    private static final double MAX_LOAD_FACTOR = 0.75;

    private Node[] buckets = new Node[INITIAL_CAPACITY];
    private int size = 0;

    // Masking with 0x7fffffff keeps the hash non-negative.
    private int indexFor(String key, int capacity) {
        return (key.hashCode() & 0x7fffffff) % capacity;
    }

    /** Adds the item, or replaces the existing one with the same code. */
    public void put(Item item) {
        String key = item.getCode();
        int i = indexFor(key, buckets.length);

        for (Node n = buckets[i]; n != null; n = n.next) {
            if (n.key.equals(key)) {
                n.value = item;
                return;
            }
        }
        buckets[i] = new Node(key, item, buckets[i]);   // insert at head of chain
        size++;

        if ((double) size / buckets.length > MAX_LOAD_FACTOR) {
            resize();
        }
    }

    /** Returns the item, or null if the code isn't in the table. */
    public Item get(String code) {
        int i = indexFor(code, buckets.length);
        for (Node n = buckets[i]; n != null; n = n.next) {
            if (n.key.equals(code)) return n.value;
        }
        return null;
    }

    public boolean contains(String code) {
        return get(code) != null;
    }

    /** Removes and returns the item, or null if it wasn't there. */
    public Item remove(String code) {
        int i = indexFor(code, buckets.length);
        Node prev = null;
        for (Node n = buckets[i]; n != null; prev = n, n = n.next) {
            if (n.key.equals(code)) {
                if (prev == null) buckets[i] = n.next;
                else prev.next = n.next;
                size--;
                return n.value;
            }
        }
        return null;
    }

    /** All items, in no particular order. The GUI table and BST/heap will use this. */
    public List<Item> values() {
        List<Item> all = new ArrayList<>();
        for (Node head : buckets) {
            for (Node n = head; n != null; n = n.next) {
                all.add(n.value);
            }
        }
        return all;
    }

    public int size() {
        return size;
    }

    // Double the bucket array and re-place every node (indexes change with capacity).
    private void resize() {
        Node[] old = buckets;
        buckets = new Node[old.length * 2];
        for (Node head : old) {
            for (Node n = head; n != null; n = n.next) {
                int i = indexFor(n.key, buckets.length);
                buckets[i] = new Node(n.key, n.value, buckets[i]);
            }
        }
    }
}