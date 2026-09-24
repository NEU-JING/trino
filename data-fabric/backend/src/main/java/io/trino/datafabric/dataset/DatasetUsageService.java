package io.trino.datafabric.dataset;

import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class DatasetUsageService
{
    private final DatasetUsageRepository repository;
    private final DatasetRepository datasetRepository;

    public DatasetUsageService(DatasetUsageRepository repository, DatasetRepository datasetRepository)
    {
        this.repository = repository;
        this.datasetRepository = datasetRepository;
    }

    public void record(String datasetUid, String application, String username, long latencyMs, boolean failed)
    {
        if (datasetUid == null || datasetUid.isBlank()) {
            return;
        }
        repository.record(datasetUid, application, username, Math.max(0, latencyMs), failed);
    }

    public List<DatasetUsageView> forDataset(String uid)
    {
        if (datasetRepository.findByUid(uid).isEmpty()) {
            throw new DatasetNotFoundException(uid);
        }
        return repository.findByDatasetUid(uid).stream().map(DatasetUsageView::from).toList();
    }

    public List<DatasetUsageView> all()
    {
        return repository.findAll().stream().map(DatasetUsageView::from).toList();
    }
}
