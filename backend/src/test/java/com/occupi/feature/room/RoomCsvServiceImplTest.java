package com.occupi.feature.room;

import com.occupi.feature.room.dto.RoomCsvParseResult;
import com.occupi.feature.room.dto.RoomImportError;
import com.occupi.feature.room.dto.RoomRequest;
import com.occupi.feature.room.dto.RoomResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("RoomCsvServiceImpl")
class RoomCsvServiceImplTest {

    private final RoomCsvServiceImpl service = new RoomCsvServiceImpl();

    private RoomCsvParseResult parse(String csv) {
        return service.parse(new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8)));
    }

    // --- export ---

    @Test
    @DisplayName("export writes a header and one row per room")
    void export_writesHeaderAndRows() {
        String csv = service.export(List.of(
                new RoomResponse("room-1", "Seminar", "Main", 1, 30),
                new RoomResponse("room-2", "Lab", "Annex", 2, 15)));

        assertThat(csv).startsWith("roomId,name,building,floor,capacity");
        assertThat(csv).contains("room-1,Seminar,Main,1,30");
        assertThat(csv).contains("room-2,Lab,Annex,2,15");
    }

    @Test
    @DisplayName("export quotes a field that contains a comma")
    void export_quotesCommas() {
        String csv = service.export(List.of(
                new RoomResponse("room-1", "Seminar, big", "Main", 1, 30)));

        assertThat(csv).contains("\"Seminar, big\"");
    }

    @Test
    @DisplayName("an exported file parses back to the same rooms (round-trip)")
    void export_roundTrips() {
        RoomCsvParseResult parsed = parse(service.export(List.of(
                new RoomResponse("room-1", "Seminar, big", "Main", 1, 30),
                new RoomResponse("room-2", "Läbör", "Annex", -1, 0))));

        assertThat(parsed.errors()).isEmpty();
        assertThat(parsed.rooms()).containsExactly(
                new RoomRequest("room-1", "Seminar, big", "Main", 1, 30),
                new RoomRequest("room-2", "Läbör", "Annex", -1, 0));
    }

    // --- parse: happy path & boundaries ---

    @Test
    @DisplayName("parse reads valid rows into room requests")
    void parse_valid() {
        RoomCsvParseResult result = parse("""
                roomId,name,building,floor,capacity
                room-1,Seminar,Main,1,30
                room-2,Lab,Annex,2,15
                """);

        assertThat(result.errors()).isEmpty();
        assertThat(result.rooms()).containsExactly(
                new RoomRequest("room-1", "Seminar", "Main", 1, 30),
                new RoomRequest("room-2", "Lab", "Annex", 2, 15));
    }

    @Test
    @DisplayName("parse trims surrounding whitespace around values")
    void parse_trims() {
        RoomCsvParseResult result = parse("""
                roomId,name,building,floor,capacity
                 room-1 , Seminar , Main , 1 , 30
                """);

        assertThat(result.errors()).isEmpty();
        assertThat(result.rooms()).containsExactly(
                new RoomRequest("room-1", "Seminar", "Main", 1, 30));
    }

    @Test
    @DisplayName("parse allows an empty building")
    void parse_emptyBuildingAllowed() {
        RoomCsvParseResult result = parse("""
                roomId,name,building,floor,capacity
                room-1,Seminar,,1,30
                """);

        assertThat(result.errors()).isEmpty();
        assertThat(result.rooms()).containsExactly(
                new RoomRequest("room-1", "Seminar", "", 1, 30));
    }

    @Test
    @DisplayName("parse strips a UTF-8 BOM before the header")
    void parse_stripsBom() {
        String bom = "\uFEFF";
        RoomCsvParseResult result =
                parse(bom + "roomId,name,building,floor,capacity\nroom-1,Seminar,Main,1,30\n");

        assertThat(result.errors()).isEmpty();
        assertThat(result.rooms()).extracting(RoomRequest::roomId).containsExactly("room-1");
    }

    // --- parse: all-or-nothing validation ---

    @Test
    @DisplayName("a single invalid row rejects the whole import")
    void parse_allOrNothing() {
        RoomCsvParseResult result = parse("""
                roomId,name,building,floor,capacity
                room-1,Seminar,Main,1,30
                room-2,Lab,Annex,x,15
                """);

        assertThat(result.rooms()).isEmpty();
        assertThat(result.errors()).hasSize(1);
        assertThat(result.errors().get(0).row()).isEqualTo(3);
        assertThat(result.errors().get(0).message()).contains("floor");
    }

    @Test
    @DisplayName("missing roomId is reported")
    void parse_missingRoomId() {
        RoomCsvParseResult result = parse("""
                roomId,name,building,floor,capacity
                ,Seminar,Main,1,30
                """);

        assertThat(result.errors()).extracting(RoomImportError::message)
                .anyMatch(m -> m.contains("roomId"));
    }

    @Test
    @DisplayName("missing name is reported")
    void parse_missingName() {
        RoomCsvParseResult result = parse("""
                roomId,name,building,floor,capacity
                room-1,,Main,1,30
                """);

        assertThat(result.errors()).extracting(RoomImportError::message)
                .anyMatch(m -> m.contains("name"));
    }

    @Test
    @DisplayName("a non-integer capacity is reported")
    void parse_nonIntegerCapacity() {
        RoomCsvParseResult result = parse("""
                roomId,name,building,floor,capacity
                room-1,Seminar,Main,1,lots
                """);

        assertThat(result.errors()).extracting(RoomImportError::message)
                .anyMatch(m -> m.contains("capacity"));
    }

    @Test
    @DisplayName("a negative capacity is reported")
    void parse_negativeCapacity() {
        RoomCsvParseResult result = parse("""
                roomId,name,building,floor,capacity
                room-1,Seminar,Main,1,-5
                """);

        assertThat(result.errors()).extracting(RoomImportError::message)
                .anyMatch(m -> m.contains("capacity"));
    }

    @Test
    @DisplayName("a duplicate roomId within the file is reported at its row")
    void parse_duplicateRoomId() {
        RoomCsvParseResult result = parse("""
                roomId,name,building,floor,capacity
                room-1,Seminar,Main,1,30
                room-1,Lab,Annex,2,15
                """);

        assertThat(result.errors()).hasSize(1);
        assertThat(result.errors().get(0).row()).isEqualTo(3);
        assertThat(result.errors().get(0).message()).contains("duplicate");
    }

    @Test
    @DisplayName("a missing required column rejects the file")
    void parse_missingColumn() {
        RoomCsvParseResult result = parse("""
                roomId,name,building,floor
                room-1,Seminar,Main,1
                """);

        assertThat(result.rooms()).isEmpty();
        assertThat(result.errors()).hasSize(1);
        assertThat(result.errors().get(0).message()).contains("capacity");
    }

    @Test
    @DisplayName("a row with too few columns is reported, not crashed on")
    void parse_shortRow() {
        RoomCsvParseResult result = parse("""
                roomId,name,building,floor,capacity
                room-1,Seminar,Main
                """);

        assertThat(result.rooms()).isEmpty();
        assertThat(result.errors()).hasSize(1);
        assertThat(result.errors().get(0).row()).isEqualTo(2);
    }

    @Test
    @DisplayName("an empty file is rejected")
    void parse_emptyFile() {
        RoomCsvParseResult result = parse("");

        assertThat(result.rooms()).isEmpty();
        assertThat(result.errors()).hasSize(1);
    }

    @Test
    @DisplayName("a header with no data rows is rejected")
    void parse_headerOnly() {
        RoomCsvParseResult result = parse("roomId,name,building,floor,capacity\n");

        assertThat(result.rooms()).isEmpty();
        assertThat(result.errors()).extracting(RoomImportError::message)
                .anyMatch(m -> m.contains("no room rows"));
    }
}
