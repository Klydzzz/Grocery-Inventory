import java.time.*;
import java.util.*;

/**
 * Binary Search Tree of items, ordered by expiry date (earliest on the left).
 * Two items can share an expiry date, so ties are broken by product code;
 * that makes every (date, code) pair a unique key.
 *
 * In-order traversal (left, node, right) visits items from the nearest
 * expiry date to the farthest, so the list comes out sorted with no extra sorting.
 *
 * Note: this tree is not self-balancing. Inserting items already in date order
 * makes it lopsided (O(n) per operation instead of O(log n)).
 */
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

    /** Order by expiry date first, then by product code. */
    private static int compare(Item a, Item b) {
        int c = a.getExpiryDate().compareTo(b.getExpiryDate());
        return (c != 0) ? c : a.getCode().compareTo(b.getCode());
    }

    // ---------------------------------------------------------------- insert

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
        // c == 0: this exact item is already in the tree, so do nothing
        return node;
    }

    // ---------------------------------------------------------------- remove

    /**
     * Removes the item. Returns true if it was found.
     * IMPORTANT: the tree finds the item by its expiry date, so if you ever change
     * an item's expiry date, remove it from the tree BEFORE changing it,
     * then insert it again afterwards.
     */
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
            // Case 1 and 2: zero or one child -> the child (or null) takes this node's place
            if (node.left == null)  return node.right;
            if (node.right == null) return node.left;

            // Case 3: two children -> copy in the in-order successor
            // (smallest item in the right subtree), then delete that successor node
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

    // ------------------------------------------------------------- traversal

    /** Every item, nearest expiry date first. */
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

    /**
     * Items expiring on or before the limit date (this includes already-expired items),
     * nearest first. Once a node is past the limit, everything to its right is too,
     * so those branches are skipped entirely.
     */
    public List<Item> expiringOnOrBefore(LocalDate limit) {
        List<Item> out = new ArrayList<>();
        collectUpTo(root, limit, out);
        return out;
    }

    private void collectUpTo(Node node, LocalDate limit, List<Item> out) {
        if (node == null) return;
        collectUpTo(node.left, limit, out);
        if (node.item.getExpiryDate().isAfter(limit)) return;   // node and its right side are too far
        out.add(node.item);
        collectUpTo(node.right, limit, out);
    }

    public int size() {
        return size;
    }
}
