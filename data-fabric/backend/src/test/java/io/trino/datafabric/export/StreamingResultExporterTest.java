package io.trino.datafabric.export;

import io.trino.datafabric.query.QueryExecutionView;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class StreamingResultExporterTest
{
    private static final QueryExecutionView VIEW = new QueryExecutionView(
            "q1",
            "FINISHED",
            List.of("name", "note"),
            List.of(List.of("alice", "a,b"), List.of("bob", "say \"hi\"")),
            false,
            null,
            Instant.now(),
            Instant.now());

    private final StreamingResultExporter exporter = new StreamingResultExporter();

    @Test
    void csvHasBomHeaderAndEscapedRows()
            throws Exception
    {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        exporter.writeCsv(VIEW, out);
        byte[] bytes = out.toByteArray();

        assertThat(bytes[0] & 0xFF).isEqualTo(0xEF);
        assertThat(bytes[1] & 0xFF).isEqualTo(0xBB);
        assertThat(bytes[2] & 0xFF).isEqualTo(0xBF);

        String text = new String(bytes, 3, bytes.length - 3, StandardCharsets.UTF_8);
        assertThat(text).startsWith("name,note\r\n");
        assertThat(text).contains("alice,\"a,b\"\r\n");
        assertThat(text).contains("\"say \"\"hi\"\"\"");
    }

    @Test
    void xlsxHasHeaderAndRows()
            throws Exception
    {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        exporter.writeXlsx(VIEW, out);

        try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(out.toByteArray()))) {
            Sheet sheet = workbook.getSheetAt(0);
            assertThat(sheet.getRow(0).getCell(0).getStringCellValue()).isEqualTo("name");
            assertThat(sheet.getRow(0).getCell(1).getStringCellValue()).isEqualTo("note");
            assertThat(sheet.getRow(1).getCell(0).getStringCellValue()).isEqualTo("alice");
            assertThat(sheet.getRow(1).getCell(1).getStringCellValue()).isEqualTo("a,b");
            assertThat(sheet.getLastRowNum()).isEqualTo(2);
        }
    }
}
