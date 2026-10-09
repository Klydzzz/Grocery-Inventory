import java.time.LocalDate;
import java.util.*;


// File overview: Linked list used to preserve item insertion order.
// This class keeps the related logic together for easier reading and maintenance.
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

    public long comparisons = 0;

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

    public boolean remove(Item item) {
        Node prev = null;
        for (Node n = head; n != null; prev = n, n = n.next) {
            comparisons++;
            if (n.item.getCode().equals(item.getCode())) {
                if (prev == null) head = n.next;
                else prev.next = n.next;
                if (n == tail) tail = prev;
                size--;
                return true;
            }
        }
        return false;
    }

    public Item find(String code) {
        for (Node n = head; n != null; n = n.next) {
            comparisons++;
            if (n.item.getCode().equals(code)) return n.item;
        }
        return null;
    }

    public Item findByDateAndCode(LocalDate date, String code) {
        for (Node n = head; n != null; n = n.next) {
            comparisons++;
            if (n.item.getExpiryDate().equals(date) && n.item.getCode().equals(code)) return n.item;
        }
        return null;
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