package com.automacropro.ui;

import com.automacropro.util.AppLogger;

import javax.swing.JComponent;
import javax.swing.JFormattedTextField;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import javax.swing.text.DefaultFormatter;

/**
 * Hardening for the app's numeric inputs.
 *
 * A bare {@link JSpinner} looks safe because its model has a minimum, but two
 * real holes let bad values through:
 *
 * <ol>
 *   <li><b>Uncommitted text.</b> The editor only writes back to the model when
 *       it is committed - normally on Enter or focus loss. Typing {@code 0} and
 *       clicking Start straight away leaves the model on its previous value
 *       while the field visibly reads 0. Whatever the user believes they set,
 *       the run uses something else. {@code setCommitsOnValidEdit} makes each
 *       keystroke commit.</li>
 *   <li><b>Garbage and out-of-range text.</b> {@code "abc"}, {@code "-5"} or
 *       {@code "0"} typed over a valid value leaves the editor in an invalid
 *       state; the spinner then either throws on {@code commitEdit} or silently
 *       keeps the stale value. Clamping on focus loss and rejecting
 *       non-numeric input outright means the widget can only ever hold a value
 *       its model allows.</li>
 * </ol>
 *
 * Model minimums stay the real source of truth (interval >= 1, count >= 1);
 * this class just makes the widget honour them at every moment, not only when
 * the user happens to press Enter.
 */
public final class NumericInput {

    private NumericInput() {
    }

    /**
     * Applies commit-on-keystroke and clamping to a spinner backed by a
     * {@link SpinnerNumberModel}. Returns the same spinner for chaining.
     */
    public static JSpinner harden(JSpinner spinner) {
        // Wheel support first: it depends only on the model, so it must not be
        // skipped by the editor-type early-out below (CoordinatePickerField
        // installs a custom SpinnerUI, and a future spinner could use a
        // non-default editor).
        addWheelSupport(spinner);

        JComponent editor = spinner.getEditor();
        if (!(editor instanceof JSpinner.DefaultEditor)) {
            return spinner;
        }
        JFormattedTextField field = ((JSpinner.DefaultEditor) editor).getTextField();

        if (field.getFormatter() instanceof DefaultFormatter) {
            ((DefaultFormatter) field.getFormatter()).setCommitsOnValidEdit(true);
        }
        // Refuse to yield focus while the text is not a valid value, and put the
        // nearest legal value back rather than leaving the field unusable.
        field.setFocusLostBehavior(JFormattedTextField.COMMIT);
        field.addFocusListener(new java.awt.event.FocusAdapter() {
            @Override
            public void focusLost(java.awt.event.FocusEvent e) {
                clamp(spinner, field);
            }
        });
        return spinner;
    }

    /**
     * Scroll wheel increments/decrements the value.
     *
     * Uses the model's own {@code getNextValue()}/{@code getPreviousValue()},
     * which return null at the bounds - so the min/max are honoured without any
     * manual range arithmetic here.
     *
     * The {@code consume()} is required, not cosmetic: several of these spinners
     * live inside a {@link javax.swing.JScrollPane} (see AutoClickerPanel), and
     * an unconsumed wheel event would both change the value AND scroll the
     * panel out from under the cursor.
     */
    private static void addWheelSupport(JSpinner spinner) {
        spinner.addMouseWheelListener(e -> {
            if (!spinner.isEnabled() || e.getWheelRotation() == 0) {
                return;
            }
            // Wheel up (negative rotation) increases, matching every other
            // spinner on the platform.
            Object next = e.getWheelRotation() < 0 ? spinner.getModel().getNextValue()
                                                   : spinner.getModel().getPreviousValue();
            if (next != null) {
                spinner.setValue(next);
            }
            e.consume();
        });
    }

    /**
     * Forces {@code spinner} to a legal value right now, whatever the editor
     * currently shows. Call before reading a value that is about to drive a run.
     */
    public static void clamp(JSpinner spinner, JFormattedTextField field) {
        if (!(spinner.getModel() instanceof SpinnerNumberModel)) {
            return;
        }
        SpinnerNumberModel model = (SpinnerNumberModel) spinner.getModel();
        try {
            spinner.commitEdit();
        } catch (java.text.ParseException ex) {
            // Non-numeric text - restore the last good model value and move on.
            field.setValue(model.getValue());
            return;
        }
        Comparable<?> min = model.getMinimum();
        Comparable<?> max = model.getMaximum();
        Number value = (Number) model.getValue();
        if (min instanceof Number && value.doubleValue() < ((Number) min).doubleValue()) {
            model.setValue(min);
            AppLogger.info("Nilai di bawah minimum, dikembalikan ke " + min);
        } else if (max instanceof Number && value.doubleValue() > ((Number) max).doubleValue()) {
            model.setValue(max);
            AppLogger.info("Nilai di atas maksimum, dikembalikan ke " + max);
        }
    }

    /** Commits and clamps a spinner without needing its editor field on hand. */
    public static void clamp(JSpinner spinner) {
        JComponent editor = spinner.getEditor();
        if (editor instanceof JSpinner.DefaultEditor) {
            clamp(spinner, ((JSpinner.DefaultEditor) editor).getTextField());
        }
    }

    /**
     * Hardens every {@link JSpinner} in a container tree.
     *
     * Applied per-panel instead of at each of the app's nine construction sites
     * because a spinner added later would silently miss out on validation - and
     * an unvalidated interval field is exactly the kind of gap that only shows
     * up as "why did it click at the old speed?". One call per panel covers
     * whatever that panel contains, now and later.
     */
    public static void hardenAll(java.awt.Container root) {
        for (java.awt.Component child : root.getComponents()) {
            if (child instanceof JSpinner) {
                harden((JSpinner) child);
            } else if (child instanceof java.awt.Container) {
                hardenAll((java.awt.Container) child);
            }
        }
    }

    /** Commits and clamps every spinner in a tree; call right before reading values for a run. */
    public static void clampAll(java.awt.Container root) {
        for (java.awt.Component child : root.getComponents()) {
            if (child instanceof JSpinner) {
                clamp((JSpinner) child);
            } else if (child instanceof java.awt.Container) {
                clampAll((java.awt.Container) child);
            }
        }
    }
}
