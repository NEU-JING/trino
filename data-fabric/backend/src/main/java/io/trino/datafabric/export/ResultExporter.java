package io.trino.datafabric.export;

import io.trino.datafabric.query.QueryExecutionView;

import java.io.IOException;
import java.io.OutputStream;

public interface ResultExporter
{
    void writeCsv(QueryExecutionView view, OutputStream out)
            throws IOException;

    void writeXlsx(QueryExecutionView view, OutputStream out)
            throws IOException;
}
