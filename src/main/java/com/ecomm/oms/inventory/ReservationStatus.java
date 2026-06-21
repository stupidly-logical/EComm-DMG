package com.ecomm.oms.inventory;

public enum ReservationStatus {
    /** Stock is held against the order but not yet shipped. */
    ACTIVE,
    /** Reservation fulfilled: held stock has been decremented from on-hand. */
    CONFIRMED,
    /** Reservation cancelled: held stock returned to availability. */
    RELEASED
}
