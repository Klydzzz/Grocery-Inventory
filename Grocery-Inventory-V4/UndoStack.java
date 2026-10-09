


// File overview: Undo history stack for reversible store actions.
// This class keeps the related logic together for easier reading and maintenance.
public class UndoStack {

    public interface Action {

        String description();

        void undo();
    }

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
        final Node below;

        Node(Action action, Node below) {
            this.action = action;
            this.below = below;
        }
    }

    private Node top;
    private int size = 0;

    public void push(Action action) {
        top = new Node(action, top);
        size++;
    }

    public Action pop() {
        if (top == null) return null;
        Action a = top.action;
        top = top.below;
        size--;
        return a;
    }

    public Action peek() {
        return (top == null) ? null : top.action;
    }

    public boolean isEmpty() {
        return top == null;
    }

    public int size() {
        return size;
    }

    public void clear() {
        top = null;
        size = 0;
    }
}