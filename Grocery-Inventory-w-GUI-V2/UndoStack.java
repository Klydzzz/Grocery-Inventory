/**
 * Custom stack (last in, first out) of actions that can be undone.
 *
 * Each time the cashier does something undoable, an Action that knows how to reverse it
 * is pushed. "Undo" pops the most recent action and runs it, so changes are always
 * reversed in the opposite order they were made.
 *
 * Built from linked nodes, so push, pop and peek are all O(1).
 */
public class UndoStack {

    /** Something that can be reversed. */
    public interface Action {
        /** Short text for messages, e.g. "added 2 x Milk 1L". */
        String description();

        /** Reverses the action. Throws IllegalArgumentException (with a message safe to show) if it can no longer be reversed. */
        void undo();
    }

    /** Convenience builder: an Action from a description and the code that reverses it. */
    public static Action of(String description, Runnable reverse) {
        return new Action() {
            @Override
            public String description() {
                return description;
            }

            @Override
            public void undo() {
                reverse.run();
            }
        };
    }

    private static class Node {
        final Action action;
        final Node below;           // the node underneath this one in the stack

        Node(Action action, Node below) {
            this.action = action;
            this.below = below;
        }
    }

    private Node top;
    private int size = 0;

    /** Puts an action on top of the stack. */
    public void push(Action action) {
        top = new Node(action, top);
        size++;
    }

    /** Removes and returns the most recent action, or null if the stack is empty. */
    public Action pop() {
        if (top == null) return null;
        Action a = top.action;
        top = top.below;
        size--;
        return a;
    }

    /** The most recent action without removing it, or null if the stack is empty. */
    public Action peek() {
        return (top == null) ? null : top.action;
    }

    public boolean isEmpty() {
        return top == null;
    }

    public int size() {
        return size;
    }

    /** Forgets every action (e.g. once a sale is paid, its basket actions can't be undone any more). */
    public void clear() {
        top = null;
        size = 0;
    }
}
