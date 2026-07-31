package com.occupi.feature.room;

import com.occupi.feature.room.dto.RoomCsvParseResult;
import com.occupi.feature.room.dto.RoomImportError;
import com.occupi.feature.room.dto.RoomRequest;
import com.occupi.feature.room.dto.RoomResponse;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Apache Commons CSV backed {@link RoomCsvService}. Reading and writing go
 * through the library so quoting and escaping follow RFC 4180 — room names may
 * contain commas or umlauts, which a hand-rolled {@code split(",")} would break.
 */
@Service
public class RoomCsvServiceImpl implements RoomCsvService {

    static final String COL_ROOM_ID = "roomId";
    static final String COL_NAME = "name";
    static final String COL_BUILDING = "building";
    static final String COL_FLOOR = "floor";
    static final String COL_CAPACITY = "capacity";

    private static final String[] HEADERS =
            {COL_ROOM_ID, COL_NAME, COL_BUILDING, COL_FLOOR, COL_CAPACITY};

    /** UTF-8 byte order mark, which spreadsheet exports (e.g. Excel) prepend. */
    private static final byte[] UTF8_BOM = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};

    @Override
    public String export(List<RoomResponse> rooms) {
        StringWriter out = new StringWriter();
        CSVFormat format = CSVFormat.DEFAULT.builder().setHeader(HEADERS).get();
        try (CSVPrinter printer = new CSVPrinter(out, format)) {
            for (RoomResponse room : rooms) {
                printer.printRecord(room.roomId(), room.name(), room.building(),
                        room.floor(), room.capacity());
            }
        } catch (IOException e) {
            // StringWriter never performs I/O, so this cannot actually happen.
            throw new IllegalStateException("Failed to write rooms CSV", e);
        }
        return out.toString();
    }

    @Override
    public RoomCsvParseResult parse(InputStream in) {
        String content;
        try {
            content = stripBom(in.readAllBytes());
        } catch (IOException e) {
            return failWith("could not read the uploaded file");
        }

        List<RoomRequest> rooms = new ArrayList<>();
        List<RoomImportError> errors = new ArrayList<>();
        Set<String> seenIds = new HashSet<>();

        CSVFormat format = CSVFormat.DEFAULT.builder()
                .setHeader()
                .setSkipHeaderRecord(true)
                .setTrim(true)
                .get();

        try (CSVParser parser = CSVParser.parse(new StringReader(content), format)) {
            List<String> missing = missingColumns(parser.getHeaderNames());
            if (!missing.isEmpty()) {
                return failWith("missing required column(s): " + String.join(", ", missing));
            }

            long dataRows = 0;
            for (CSVRecord record : parser) {
                dataRows++;
                int row = (int) record.getRecordNumber() + 1; // +1: header is line 1
                parseRow(record, row, seenIds, rooms, errors);
            }
            if (dataRows == 0) {
                return failWith("the file contains a header but no room rows");
            }
        } catch (IOException | RuntimeException e) {
            return failWith("the file is not valid CSV");
        }

        // All-or-nothing: hand back no rooms if any row was rejected, so the
        // caller cannot accidentally apply a partial import.
        return errors.isEmpty()
                ? new RoomCsvParseResult(rooms, List.of())
                : new RoomCsvParseResult(List.of(), errors);
    }

    private void parseRow(CSVRecord record, int row, Set<String> seenIds,
                          List<RoomRequest> rooms, List<RoomImportError> errors) {
        String roomId = value(record, COL_ROOM_ID);
        String name = value(record, COL_NAME);
        String building = value(record, COL_BUILDING);

        if (roomId == null || roomId.isBlank()) {
            errors.add(new RoomImportError(row, "roomId is required"));
            return;
        }
        if (name == null || name.isBlank()) {
            errors.add(new RoomImportError(row, "name is required"));
            return;
        }
        if (!seenIds.add(roomId)) {
            errors.add(new RoomImportError(row, "duplicate roomId '" + roomId + "' in the file"));
            return;
        }

        Integer floor = parseInt(value(record, COL_FLOOR));
        if (floor == null) {
            errors.add(new RoomImportError(row, "floor must be a whole number"));
            return;
        }
        Integer capacity = parseInt(value(record, COL_CAPACITY));
        if (capacity == null) {
            errors.add(new RoomImportError(row, "capacity must be a whole number"));
            return;
        }
        if (capacity < 0) {
            errors.add(new RoomImportError(row, "capacity must not be negative"));
            return;
        }

        rooms.add(new RoomRequest(roomId, name, building, floor, capacity));
    }

    private List<String> missingColumns(List<String> headerNames) {
        List<String> missing = new ArrayList<>();
        for (String required : HEADERS) {
            if (!headerNames.contains(required)) {
                missing.add(required);
            }
        }
        return missing;
    }

    /** Returns the column value, or {@code null} if the row is too short to have it. */
    private static String value(CSVRecord record, String column) {
        return record.isSet(column) ? record.get(column) : null;
    }

    private static Integer parseInt(String value) {
        if (value == null) {
            return null;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static RoomCsvParseResult failWith(String message) {
        return new RoomCsvParseResult(List.of(), List.of(new RoomImportError(1, message)));
    }

    private static String stripBom(byte[] bytes) {
        int start = 0;
        if (bytes.length >= UTF8_BOM.length
                && bytes[0] == UTF8_BOM[0]
                && bytes[1] == UTF8_BOM[1]
                && bytes[2] == UTF8_BOM[2]) {
            start = UTF8_BOM.length;
        }
        return new String(bytes, start, bytes.length - start, StandardCharsets.UTF_8);
    }
}
