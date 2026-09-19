package io.trino.datafabric.export;

import io.trino.datafabric.query.QueryExecutionView;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class StreamingResultExporter
        implements ResultExporter
{
    private static final byte[] UTF8_BOM = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
    private static final int ROWS_IN_MEMORY = 100;

    @Override
    public void writeCsv(QueryExecutionView view, OutputStream out)
            throws IOException
    {
        out.write(UTF8_BOM);
        Writer writer = new OutputStreamWriter(out, StandardCharsets.UTF_8);
        writer.write(join(view.columns()));
        writer.write("\r\n");
        for (List<Object> row : view.rows()) {
            writer.write(row.stream().map(StreamingResultExporter::csvCell).collect(Collectors.joining(",")));
            writer.write("\r\n");
        }
        writer.flush();
    }

    @Override
    public void writeXlsx(QueryExecutionView view, OutputStream out)
            throws IOException
    {
        try (SXSSFWorkbook workbook = new SXSSFWorkbook(ROWS_IN_MEMORY)) {
            Sheet sheet = workbook.createSheet("result");
            Row header = sheet.createRow(0);
            List<String> columns = view.columns();
            for (int i = 0; i < columns.size(); i++) {
                header.createCell(i).setCellValue(columns.get(i));
            }
            int rowIndex = 1;
            for (List<Object> row : view.rows()) {
                Row excelRow = sheet.createRow(rowIndex++);
                for (int i = 0; i < row.size(); i++) {
                    Object value = row.get(i);
                    excelRow.createCell(i).setCellValue(value == null ? "" : String.valueOf(value));
                }
            }
            workbook.write(out);
            workbook.dispose();
        }
    }

    private static String join(List<String> values)
    {
        return values.stream().map(StreamingResultExporter::csvCell).collect(Collectors.joining(","));
    }

    private static String csvCell(Object value)
    {
        if (value == null) {
            return "";
        }
        String text = String.valueOf(value);
        if (text.contains(",") || text.contains("\"") || text.contains("\n") || text.contains("\r")) {
            return '"' + text.replace("\"", "\"\"") + '"';
        }
        return text;
    }
}
