package com.automacropro.ui;

import com.automacropro.model.MacroStep;

import javax.swing.DefaultListModel;
import javax.swing.JComponent;
import javax.swing.JList;
import javax.swing.TransferHandler;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Drag &amp; drop reordering for the sequencer step list.
 *
 * Deliberately does NOT serialize the steps: the drag never leaves this one
 * JList, so the transferable carries the source <em>indices</em> and the model
 * is mutated directly. That skips making {@link MacroStep} Serializable and
 * skips a clone-on-drop, so a reordered step stays the very same object the
 * rest of the panel already holds.
 *
 * MOVE-only. Supports multi-row drags, since the list is
 * MULTIPLE_INTERVAL_SELECTION - a single-index transfer would silently move
 * only one row of a multi-row selection. All of this runs on the EDT; the
 * reorder is safe against a live run because the panel's sync replaces the
 * project's list reference rather than mutating the list the engine iterates.
 */
public final class StepListTransferHandler extends TransferHandler {

    private static final DataFlavor INDEX_FLAVOR =
            new DataFlavor(int[].class, "application/x-automacro-step-indices");

    private final Runnable onReordered;

    StepListTransferHandler(Runnable onReordered) {
        this.onReordered = onReordered;
    }

    @Override
    public int getSourceActions(JComponent c) {
        return MOVE;
    }

    @Override
    protected Transferable createTransferable(JComponent c) {
        int[] indices = ((JList<?>) c).getSelectedIndices();
        if (indices.length == 0) {
            return null;
        }
        return new Transferable() {
            @Override
            public DataFlavor[] getTransferDataFlavors() {
                return new DataFlavor[]{INDEX_FLAVOR};
            }

            @Override
            public boolean isDataFlavorSupported(DataFlavor flavor) {
                return INDEX_FLAVOR.equals(flavor);
            }

            @Override
            public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException {
                if (!INDEX_FLAVOR.equals(flavor)) {
                    throw new UnsupportedFlavorException(flavor);
                }
                return indices;
            }
        };
    }

    @Override
    public boolean canImport(TransferSupport support) {
        return support.isDrop() && support.isDataFlavorSupported(INDEX_FLAVOR);
    }

    @Override
    @SuppressWarnings("unchecked")
    public boolean importData(TransferSupport support) {
        if (!canImport(support)) {
            return false;
        }
        JList<MacroStep> list = (JList<MacroStep>) support.getComponent();
        DefaultListModel<MacroStep> model = (DefaultListModel<MacroStep>) list.getModel();

        int[] from;
        try {
            from = (int[]) support.getTransferable().getTransferData(INDEX_FLAVOR);
        } catch (UnsupportedFlavorException | IOException ex) {
            return false;
        }
        if (from.length == 0) {
            return false;
        }

        JList.DropLocation drop = (JList.DropLocation) support.getDropLocation();
        int dropIndex = drop.getIndex();
        if (dropIndex < 0 || dropIndex > model.size()) {
            return false;
        }
        // Dropping inside the dragged block itself is a no-op, not a move.
        if (from.length == 1 && resolveTarget(from[0], dropIndex, model.size()) < 0) {
            return false;
        }

        // Pull the dragged rows out bottom-up (so earlier indices stay valid),
        // then reinsert them in their original order at a drop point adjusted
        // for however many of them sat above it.
        List<MacroStep> snapshot = new ArrayList<>();
        for (int i = 0; i < model.size(); i++) {
            snapshot.add(model.get(i));
        }
        List<MacroStep> reordered = reorder(snapshot, from, dropIndex);
        model.clear();
        for (MacroStep step : reordered) {
            model.addElement(step);
        }
        int target = dropIndex - countBelow(from, dropIndex);
        list.setSelectionInterval(target, target + from.length - 1);
        onReordered.run();
        return true;
    }

    /**
     * Moves the rows at {@code from} so they sit at {@code dropIndex}, keeping
     * their relative order.
     *
     * Split out and made generic so the reorder arithmetic - the part that is
     * actually easy to get wrong - can be exercised directly by a test instead
     * of being restated there.
     */
    public static <T> List<T> reorder(List<T> items, int[] from, int dropIndex) {
        List<T> working = new ArrayList<>(items);
        List<T> moved = new ArrayList<>();
        for (int i = from.length - 1; i >= 0; i--) {
            moved.add(0, working.remove(from[i]));
        }
        int target = dropIndex - countBelow(from, dropIndex);
        working.addAll(target, moved);
        return working;
    }

    /** How many of the dragged rows sat above the drop point. */
    private static int countBelow(int[] from, int dropIndex) {
        int count = 0;
        for (int index : from) {
            if (index < dropIndex) {
                count++;
            }
        }
        return count;
    }

    /**
     * Translates a single-row drop index into a post-removal insertion index.
     *
     * Swing reports the drop position against the list as it looks BEFORE the
     * dragged row is pulled out, so any target past the source shifts up by one
     * once it is removed. Without this correction every downward drag lands the
     * step one slot too low.
     *
     * @return the insertion index, or -1 if the move is invalid or a no-op
     *         (dropping a row back where it already was).
     */
    public static int resolveTarget(int from, int dropIndex, int size) {
        if (dropIndex < 0 || dropIndex > size || from < 0 || from >= size) {
            return -1;
        }
        int to = dropIndex > from ? dropIndex - 1 : dropIndex;
        return to == from ? -1 : to;
    }

    /**
     * Intentionally empty. The default MOVE implementation removes the source
     * rows after a successful import, which would delete the steps we just
     * re-inserted - {@link #importData} already performed the whole move.
     */
    @Override
    protected void exportDone(JComponent source, Transferable data, int action) {
        // no-op by design; see javadoc
    }
}
