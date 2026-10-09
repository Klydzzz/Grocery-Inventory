import java.util.*;


// File overview: Hash table mapping product codes to Item records.
// This class keeps the related logic together for easier reading and maintenance.
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

    public long comparisons = 0;

    private int indexFor(String key, int capacity) {
        return (key.hashCode() & 0x7fffffff) % capacity;
    }

    public void put(Item item) {
        String key = item.getCode();
        int i = indexFor(key, buckets.length);

        for (Node n = buckets[i]; n != null; n = n.next) {
            comparisons++;
            if (n.key.equals(key)) {
                n.value = item;
                return;
            }
        }
        buckets[i] = new Node(key, item, buckets[i]);
        size++;

        if ((double) size / buckets.length > MAX_LOAD_FACTOR) {
            resize();
        }
    }

    public Item get(String code) {
        int i = indexFor(code, buckets.length);
        for (Node n = buckets[i]; n != null; n = n.next) {
            comparisons++;
            if (n.key.equals(code)) return n.value;
        }
        return null;
    }

    public boolean contains(String code) {
        return get(code) != null;
    }

    public Item remove(String code) {
        int i = indexFor(code, buckets.length);
        Node prev = null;
        for (Node n = buckets[i]; n != null; prev = n, n = n.next) {
            comparisons++;
            if (n.key.equals(code)) {
                if (prev == null) buckets[i] = n.next;
                else prev.next = n.next;
                size--;
                return n.value;
            }
        }
        return null;
    }

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