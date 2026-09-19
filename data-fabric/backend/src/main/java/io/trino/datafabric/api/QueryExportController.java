package io.trino.datafabric.api;

import io.trino.datafabric.auth.AuthInterceptor;
import io.trino.datafabric.auth.RequireAuthenticated;
import io.trino.datafabric.export.ResultExporter;
import io.trino.datafabric.export.ResultNotExportableException;
import io.trino.datafabric.query.QueryExecutionView;
import io.trino.datafabric.query.QueryService;
import io.trino.datafabric.user.User;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.Locale;

@RestController
@RequestMapping("/api/queries")
public class QueryExportController
{
    private final QueryService queryService;
    private final ResultExporter resultExporter;

    public QueryExportController(QueryService queryService, ResultExporter resultExporter)
    {
        this.queryService = queryService;
        this.resultExporter = resultExporter;
    }

    @GetMapping("/{id}/export")
    @RequireAuthenticated
    public void export(
            @PathVariable String id,
            @RequestParam(name = "format", defaultValue = "csv") String format,
            @RequestAttribute(AuthInterceptor.USER_ATTRIBUTE) User user,
            HttpServletResponse response)
            throws IOException
    {
        QueryExecutionView view = queryService.get(user.username(), id);
        if (!"FINISHED".equals(view.state())) {
            throw new ResultNotExportableException(id, view.state());
        }

        switch (format.toLowerCase(Locale.ROOT)) {
            case "csv" -> {
                response.setStatus(HttpServletResponse.SC_OK);
                response.setContentType("text/csv; charset=UTF-8");
                response.setHeader("Content-Disposition", "attachment; filename=\"query-" + id + ".csv\"");
                resultExporter.writeCsv(view, response.getOutputStream());
            }
            case "xlsx" -> {
                response.setStatus(HttpServletResponse.SC_OK);
                response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
                response.setHeader("Content-Disposition", "attachment; filename=\"query-" + id + ".xlsx\"");
                resultExporter.writeXlsx(view, response.getOutputStream());
            }
            default -> throw new IllegalArgumentException("Unsupported export format: " + format);
        }
    }
}
