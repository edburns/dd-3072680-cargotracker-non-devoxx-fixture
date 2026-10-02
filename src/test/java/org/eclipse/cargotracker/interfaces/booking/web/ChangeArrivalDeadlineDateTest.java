package org.eclipse.cargotracker.interfaces.booking.web;

import org.eclipse.cargotracker.interfaces.booking.facade.BookingServiceFacade;
import org.eclipse.cargotracker.interfaces.booking.facade.dto.CargoRoute;
import org.eclipse.cargotracker.interfaces.booking.facade.dto.Location;
import org.eclipse.cargotracker.interfaces.booking.facade.dto.RouteCandidate;
import org.primefaces.PrimeFaces;
import org.junit.Test;

import java.lang.reflect.Field;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.fail;

public class ChangeArrivalDeadlineDateTest {

    @Test
    public void loadDelegatesTrackingIdAndParsesDisplayedDate() throws Exception {
        RecordingBookingServiceFacade facade = new RecordingBookingServiceFacade(
                cargoRoute("03/14/2025 12:00 AM UTC"));
        ChangeArrivalDeadlineDate editor = editor(facade);
        editor.setTrackingId("ABC123");

        editor.load();

        assertEquals("ABC123", facade.loadedTrackingId);
        assertSame(facade.cargo, editor.getCargo());
        assertEquals(date("03/14/2025"), editor.getArrivalDeadlineDate());
    }

    @Test
    public void loadRejectsMalformedDate() {
        RecordingBookingServiceFacade facade = new RecordingBookingServiceFacade(
                cargoRoute("02/30/2025 12:00 AM UTC"));
        ChangeArrivalDeadlineDate editor = editor(facade);
        editor.setTrackingId("ABC123");

        try {
            editor.load();
            fail("Expected malformed deadline to be rejected");
        } catch (IllegalStateException e) {
            assertEquals("Unable to parse arrival deadline date for cargo ABC123",
                    e.getMessage());
            assertEquals(ParseException.class, e.getCause().getClass());
            assertEquals(null, editor.getArrivalDeadlineDate());
        }
    }

    @Test
    public void changeArrivalDeadlineDelegatesSelectedDateAndTrackingId() {
        RecordingBookingServiceFacade facade = new RecordingBookingServiceFacade(
                cargoRoute("03/14/2025 12:00 AM UTC"));
        ChangeArrivalDeadlineDate editor = editor(facade);
        Date selectedDate = new Date(123456789L);
        editor.setTrackingId("ABC123");
        editor.setArrivalDeadlineDate(selectedDate);
        facade.failureOnChange = new RuntimeException("expected facade failure");

        try {
            editor.changeArrivalDeadline();
            fail("Expected facade failure");
        } catch (RuntimeException e) {
            assertSame(facade.failureOnChange, e);
        }

        assertEquals("ABC123", facade.changedTrackingId);
        assertSame(selectedDate, facade.changedDeadline);
        assertEquals(1, facade.changeDeadlineCalls);
    }

    @Test
    public void successfulChangeClosesDialogWithDone() {
        RecordingBookingServiceFacade facade = new RecordingBookingServiceFacade(
                cargoRoute("03/14/2025 12:00 AM UTC"));
        ChangeArrivalDeadlineDate editor = editor(facade);
        Date selectedDate = new Date(123456789L);
        editor.setTrackingId("ABC123");
        editor.setArrivalDeadlineDate(selectedDate);
        RecordingPrimeFaces primeFaces = new RecordingPrimeFaces();
        PrimeFaces.setCurrent(primeFaces);

        try {
            editor.changeArrivalDeadline();
        } finally {
            PrimeFaces.setCurrent(null);
        }

        assertEquals("DONE", primeFaces.closedWith);
        assertSame(selectedDate, facade.changedDeadline);
    }

    @Test
    public void changeArrivalDeadlineRejectsNullDate() {
        RecordingBookingServiceFacade facade = new RecordingBookingServiceFacade(
                cargoRoute("03/14/2025 12:00 AM UTC"));
        ChangeArrivalDeadlineDate editor = editor(facade);
        editor.setTrackingId("ABC123");

        try {
            editor.changeArrivalDeadline();
            fail("Expected null deadline to be rejected");
        } catch (IllegalStateException e) {
            assertEquals("An arrival deadline date is required.", e.getMessage());
        }

        assertEquals(0, facade.changeDeadlineCalls);
    }

    private static ChangeArrivalDeadlineDate editor(
            RecordingBookingServiceFacade facade) {
        ChangeArrivalDeadlineDate editor = new ChangeArrivalDeadlineDate();
        try {
            Field facadeField = ChangeArrivalDeadlineDate.class
                    .getDeclaredField("bookingServiceFacade");
            facadeField.setAccessible(true);
            facadeField.set(editor, facade);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
        return editor;
    }

    private static CargoRoute cargoRoute(final String deadline) {
        Date initialDate = new Date(0L);
        return new CargoRoute("ABC123", "CHI", "HEL", initialDate,
                false, false, "CHI", "NOT_RECEIVED") {
            @Override
            public String getArrivalDeadline() {
                return deadline;
            }
        };
    }

    private static Date date(String value) throws ParseException {
        return new SimpleDateFormat("MM/dd/yyyy").parse(value);
    }

    private static class RecordingBookingServiceFacade
            implements BookingServiceFacade {
        private final CargoRoute cargo;
        private String loadedTrackingId;
        private String changedTrackingId;
        private Date changedDeadline;
        private int changeDeadlineCalls;
        private RuntimeException failureOnChange;

        private RecordingBookingServiceFacade(CargoRoute cargo) {
            this.cargo = cargo;
        }

        @Override
        public String bookNewCargo(String origin, String destination,
                                   Date arrivalDeadline) {
            throw new AssertionError("Unexpected booking");
        }

        @Override
        public CargoRoute loadCargoForRouting(String trackingId) {
            loadedTrackingId = trackingId;
            return cargo;
        }

        @Override
        public void assignCargoToRoute(String trackingId,
                                       RouteCandidate route) {
            throw new AssertionError("Unexpected route assignment");
        }

        @Override
        public void changeDestination(String trackingId,
                                      String destinationUnLocode) {
            throw new AssertionError("Unexpected destination change");
        }

        @Override
        public void changeDeadline(String trackingId, Date arrivalDeadline) {
            changeDeadlineCalls++;
            changedTrackingId = trackingId;
            changedDeadline = arrivalDeadline;
            if (failureOnChange != null) {
                throw failureOnChange;
            }
        }

        @Override
        public List<RouteCandidate> requestPossibleRoutesForCargo(
                String trackingId) {
            throw new AssertionError("Unexpected route request");
        }

        @Override
        public List<Location> listShippingLocations() {
            throw new AssertionError("Unexpected location listing");
        }

        @Override
        public List<CargoRoute> listAllCargos() {
            throw new AssertionError("Unexpected cargo listing");
        }
    }

    private static class RecordingPrimeFaces extends PrimeFaces {
        private String closedWith;

        @Override
        public Dialog dialog() {
            return new RecordingDialog(this);
        }
    }

    private static class RecordingDialog extends PrimeFaces.Dialog {
        private final RecordingPrimeFaces primeFaces;

        private RecordingDialog(RecordingPrimeFaces primeFaces) {
            primeFaces.super();
            this.primeFaces = primeFaces;
        }

        @Override
        public void closeDynamic(Object data) {
            primeFaces.closedWith = (String) data;
        }
    }
}
