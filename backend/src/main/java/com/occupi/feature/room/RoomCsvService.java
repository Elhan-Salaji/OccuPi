package com.occupi.feature.room;

import com.occupi.feature.room.dto.RoomCsvParseResult;
import com.occupi.feature.room.dto.RoomResponse;

import java.io.InputStream;
import java.util.List;

/**
 * Converts room metadata to and from CSV. This is the format boundary: it knows
 * the CSV layout and how to validate incoming rows, but nothing about
 * persistence — {@link RoomService} owns the database side.
 */
public interface RoomCsvService {

    /**
     * Renders the given rooms as an RFC 4180 CSV document with the header
     * {@code roomId,name,building,floor,capacity}.
     */
    String export(List<RoomResponse> rooms);

    /**
     * Parses and validates an uploaded room CSV.
     *
     * <p>Reads the stream fully, so the caller keeps ownership of closing it.
     * Never throws on malformed content: every problem (missing field,
     * non-integer number, duplicate id, empty file) is reported as a row error
     * in the result rather than as an exception.
     */
    RoomCsvParseResult parse(InputStream in);
}
