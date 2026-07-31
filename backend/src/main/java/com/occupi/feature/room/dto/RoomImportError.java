package com.occupi.feature.room.dto;

/**
 * A single problem found while importing a room CSV, tied to the line in the
 * uploaded file so the admin can fix it.
 *
 * @param row     1-based line number in the CSV file (the header is line 1, so
 *                the first data row is line 2)
 * @param message human-readable reason the row was rejected
 */
public record RoomImportError(int row, String message) {}
