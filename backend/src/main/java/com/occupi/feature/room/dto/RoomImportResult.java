package com.occupi.feature.room.dto;

import java.util.List;

/**
 * Outcome of a room CSV import, returned to the Admin Panel.
 *
 * On success {@code errors} is empty and {@code created}/{@code updated} report
 * how many rooms were added versus overwritten. On a rejected import both counts
 * are {@code 0} and {@code errors} lists every offending row — the import is
 * all-or-nothing, so nothing was written in that case.
 *
 * @param created number of rooms newly created
 * @param updated number of existing rooms overwritten
 * @param errors  rejected rows; empty when the import succeeded
 */
public record RoomImportResult(int created, int updated, List<RoomImportError> errors) {}
