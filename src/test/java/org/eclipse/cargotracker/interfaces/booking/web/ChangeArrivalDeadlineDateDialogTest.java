package org.eclipse.cargotracker.interfaces.booking.web;

import org.junit.Test;
import org.primefaces.PrimeFaces;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ChangeArrivalDeadlineDateDialogTest {

    @Test
    public void showDialogOpensConfiguredViewWithOnlyTrackingId() {
        RecordingPrimeFaces primeFaces = new RecordingPrimeFaces();
        PrimeFaces.setCurrent(primeFaces);

        try {
            new ChangeArrivalDeadlineDateDialog().showDialog("DEF789");
        } finally {
            PrimeFaces.setCurrent(null);
        }

        assertEquals("/admin/dialogs/changeArrivalDeadlineDate.xhtml",
                primeFaces.dialog.outcome);
        assertEquals(5, primeFaces.dialog.options.size());
        assertEquals(Boolean.TRUE, primeFaces.dialog.options.get("modal"));
        assertEquals(Boolean.TRUE, primeFaces.dialog.options.get("draggable"));
        assertEquals(Boolean.FALSE, primeFaces.dialog.options.get("resizable"));
        assertEquals(410, primeFaces.dialog.options.get("contentWidth"));
        assertEquals(280, primeFaces.dialog.options.get("contentHeight"));
        assertEquals(Collections.singleton("trackingId"),
                primeFaces.dialog.params.keySet());
        assertEquals(Collections.singletonList("DEF789"),
                primeFaces.dialog.params.get("trackingId"));
    }

    @Test
    public void cancelClosesDialogWithEmptyString() {
        RecordingPrimeFaces primeFaces = new RecordingPrimeFaces();
        PrimeFaces.setCurrent(primeFaces);

        try {
            new ChangeArrivalDeadlineDateDialog().cancel();
        } finally {
            PrimeFaces.setCurrent(null);
        }

        assertEquals("", primeFaces.dialog.closedWith);
    }

    private static class RecordingPrimeFaces extends PrimeFaces {
        private final RecordingDialog dialog = new RecordingDialog(this);

        @Override
        public Dialog dialog() {
            return dialog;
        }
    }

    private static class RecordingDialog extends PrimeFaces.Dialog {
        private String outcome;
        private Map<String, Object> options;
        private Map<String, List<String>> params;
        private Object closedWith;

        private RecordingDialog(RecordingPrimeFaces primeFaces) {
            primeFaces.super();
        }

        @Override
        public void openDynamic(String outcome, Map<String, Object> options,
                                Map<String, List<String>> params) {
            this.outcome = outcome;
            this.options = options;
            this.params = params;
        }

        @Override
        public void closeDynamic(Object data) {
            closedWith = data;
        }
    }
}
