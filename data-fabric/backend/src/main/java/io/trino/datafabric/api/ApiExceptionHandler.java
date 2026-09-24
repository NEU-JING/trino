package io.trino.datafabric.api;

import io.trino.datafabric.auth.DuplicateUserException;
import io.trino.datafabric.auth.InsufficientRoleException;
import io.trino.datafabric.auth.InvalidCredentialsException;
import io.trino.datafabric.datasource.DataSourceException;
import io.trino.datafabric.datasource.DataSourceNotFoundException;
import io.trino.datafabric.datasource.DuplicateDataSourceException;
import io.trino.datafabric.dataset.DatasetException;
import io.trino.datafabric.dataset.DatasetNotFoundException;
import io.trino.datafabric.export.ResultNotExportableException;
import io.trino.datafabric.metadata.MetadataQueryException;
import io.trino.datafabric.query.QueryNotFoundException;
import io.trino.datafabric.query.SavedQueryNotFoundException;
import io.trino.datafabric.table.TableNotFoundException;
import io.trino.datafabric.trino.TrinoQueryException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice
public class ApiExceptionHandler
{
    @ExceptionHandler(InvalidCredentialsException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public Map<String, String> invalidCredentials(InvalidCredentialsException exception)
    {
        return Map.of("error", exception.getMessage());
    }

    @ExceptionHandler(InsufficientRoleException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public Map<String, String> insufficientRole(InsufficientRoleException exception)
    {
        return Map.of("error", exception.getMessage());
    }

    @ExceptionHandler(DuplicateUserException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public Map<String, String> duplicateUser(DuplicateUserException exception)
    {
        return Map.of("error", exception.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, String> illegalArgument(IllegalArgumentException exception)
    {
        return Map.of("error", exception.getMessage());
    }

    @ExceptionHandler(DataSourceException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, String> dataSource(DataSourceException exception)
    {
        return Map.of("error", exception.getMessage());
    }

    @ExceptionHandler(DuplicateDataSourceException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public Map<String, String> duplicateDataSource(DuplicateDataSourceException exception)
    {
        return Map.of("error", exception.getMessage());
    }

    @ExceptionHandler(DataSourceNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Map<String, String> dataSourceNotFound(DataSourceNotFoundException exception)
    {
        return Map.of("error", exception.getMessage());
    }

    @ExceptionHandler(TrinoQueryException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, String> trinoQuery(TrinoQueryException exception)
    {
        return Map.of("error", exception.getMessage());
    }

    @ExceptionHandler(MetadataQueryException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, String> metadataQuery(MetadataQueryException exception)
    {
        return Map.of("error", exception.getMessage());
    }

    @ExceptionHandler(TableNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Map<String, String> tableNotFound(TableNotFoundException exception)
    {
        return Map.of("error", exception.getMessage());
    }

    @ExceptionHandler(DatasetNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Map<String, String> datasetNotFound(DatasetNotFoundException exception)
    {
        return Map.of("error", exception.getMessage());
    }

    @ExceptionHandler(DatasetException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, String> dataset(DatasetException exception)
    {
        return Map.of("error", exception.getMessage());
    }

    @ExceptionHandler(QueryNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Map<String, String> queryNotFound(QueryNotFoundException exception)
    {
        return Map.of("error", exception.getMessage());
    }

    @ExceptionHandler(SavedQueryNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Map<String, String> savedQueryNotFound(SavedQueryNotFoundException exception)
    {
        return Map.of("error", exception.getMessage());
    }

    @ExceptionHandler(ResultNotExportableException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public Map<String, String> resultNotExportable(ResultNotExportableException exception)
    {
        return Map.of("error", exception.getMessage());
    }
}
