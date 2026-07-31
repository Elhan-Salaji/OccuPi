package com.occupi.feature.room.dto;

import java.util.List;

/**
 * Result of parsing an uploaded room CSV, before anything is persisted.
 *
 * Carries the successfully parsed rows and any per-row errors. The caller
 * applies the all-or-nothing rule: if {@code errors} is non-empty, none of the
 * {@code rooms} are written.
 *
 * @param rooms  rows that parsed and validated into room requests
 * @param errors per-row problems found while parsing
 */
public record RoomCsvParseResult(List<RoomRequest> rooms, List<RoomImportError> errors) {}
