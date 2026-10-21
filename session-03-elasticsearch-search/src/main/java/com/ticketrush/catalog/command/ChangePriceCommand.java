package com.ticketrush.catalog.command;

import java.math.BigDecimal;

/** Command: an intention to change state. It goes to the write model (PostgreSQL) and returns nothing. */
public record ChangePriceCommand(long eventId, long ticketTypeId, BigDecimal price) {
}
