import java.util.*;

/**
 * Singly linked list holding all inventory items in insertion order.
 *
 * This gives us O(1) append and easy iteration, while the hash table provides
 * constant-time lookup by product code.
 */
public class InventoryList implements Iterable<Item> {

    private static class Node {
        Item item;
        Node next;

        Node(Item item) {
            this.item = item;
        }
    }

    private Node head;
    private Node tail;
    private int size = 0;

    /** Appends the item to the end of the list. */
    public void addLast(Item item) {
        Node node = new Node(item);
        if (head == null) {
            head = node;
            tail = node;
        } else {
            tail.next = node;
            tail = node;
        }
        size++;
    }

    /** Removes the item with the same product code. Returns true if it was found. */
    public boolean remove(Item item) {
        Node prev = null;
        for (Node n = head; n != null; prev = n, n = n.next) {
            if (n.item.getCode().equals(item.getCode())) {
                if (prev == null) head = n.next;      // removing the first node
                else prev.next = n.next;              // skip over the node
                if (n == tail) tail = prev;           // removed the last node: move the tail back
                size--;
                return true;
            }
        }
        return false;
    }

    public int size() {
        return size;
    }

    public boolean isEmpty() {
        return size == 0;
    }

    @Override
    public Iterator<Item> iterator() {
        return new Iterator<Item>() {
            private Node current = head;

            @Override
            public boolean hasNext() {
                return current != null;
            }

            @Override
            public Item next() {
                if (current == null) throw new NoSuchElementException();
                Item item = current.item;
                current = current.next;
                return item;
            }
        };
    }
}
