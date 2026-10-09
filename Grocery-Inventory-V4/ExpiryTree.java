import java.time.*;
import java.util.*;


// File overview: Expiry-based binary search tree for time-sensitive inventory checks.
// This class keeps the related logic together for easier reading and maintenance.
public class ExpiryTree {

    private static class Node {
        Item item;
        Node left, right;

        Node(Item item) {
            this.item = item;
        }
    }

    private Node root;
    private int size = 0;

    public long comparisons = 0;

    private int compare(Item a, Item b) {
        comparisons++;
        int c = a.getExpiryDate().compareTo(b.getExpiryDate());
        return (c != 0) ? c : a.getCode().compareTo(b.getCode());
    }

    public void insert(Item item) {
        root = insert(root, item);
    }

    private Node insert(Node node, Item item) {
        if (node == null) {
            size++;
            return new Node(item);
        }
        int c = compare(item, node.item);
        if (c < 0)      node.left  = insert(node.left, item);
        else if (c > 0) node.right = insert(node.right, item);

        return node;
    }

    public boolean remove(Item item) {
        int before = size;
        root = remove(root, item);
        return size < before;
    }

    private Node remove(Node node, Item item) {
        if (node == null) return null;

        int c = compare(item, node.item);
        if (c < 0) {
            node.left = remove(node.left, item);
        } else if (c > 0) {
            node.right = remove(node.right, item);
        } else {
            size--;

            if (node.left == null)  return node.right;
            if (node.right == null) return node.left;

            Node successor = node.right;
            while (successor.left != null) successor = successor.left;
            node.item = successor.item;
            node.right = removeMin(node.right);
        }
        return node;
    }

    private Node removeMin(Node node) {
        if (node.left == null) return node.right;
        node.left = removeMin(node.left);
        return node;
    }

    public List<Item> inOrder() {
        List<Item> out = new ArrayList<>();
        inOrder(root, out);
        return out;
    }

    private void inOrder(Node node, List<Item> out) {
        if (node == null) return;
        inOrder(node.left, out);
        out.add(node.item);
        inOrder(node.right, out);
    }

    public List<Item> expiringOnOrBefore(LocalDate limit) {
        List<Item> out = new ArrayList<>();
        collectUpTo(root, limit, out);
        return out;
    }

    private void collectUpTo(Node node, LocalDate limit, List<Item> out) {
        if (node == null) return;
        collectUpTo(node.left, limit, out);
        if (node.item.getExpiryDate().isAfter(limit)) return;
        out.add(node.item);
        collectUpTo(node.right, limit, out);
    }

    public Item find(LocalDate date, String code) {
        Node n = root;
        while (n != null) {
            comparisons++;
            int c = date.compareTo(n.item.getExpiryDate());
            if (c == 0) c = code.compareTo(n.item.getCode());
            if (c < 0)      n = n.left;
            else if (c > 0) n = n.right;
            else            return n.item;
        }
        return null;
    }

    public int size() {
        return size;
    }
}