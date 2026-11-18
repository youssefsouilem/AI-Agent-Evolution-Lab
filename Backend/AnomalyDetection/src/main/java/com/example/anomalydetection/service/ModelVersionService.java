package com.example.anomalydetection.service;

import com.example.anomalydetection.api.ModelVersionRequest;
import com.example.anomalydetection.domain.ModelVersion;
import com.example.anomalydetection.repository.ModelVersionRepository;
import jakarta.transaction.Transactional;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class ModelVersionService {
    private final ModelVersionRepository repository;
    public ModelVersionService(ModelVersionRepository repository) { this.repository = repository; }
    public List<ModelVersion> findAll() { return repository.findAll(); }
    public ModelVersion findById(Long id) { return repository.findById(id).orElseThrow(() -> new IllegalArgumentException("Model version not found: " + id)); }
    @Transactional
    public ModelVersion create(ModelVersionRequest r) {
        if (r.name() == null || r.name().isBlank() || r.algorithm() == null || r.algorithm().isBlank()) throw new IllegalArgumentException("Name and algorithm are required");
        if (repository.existsByNameIgnoreCase(r.name())) throw new IllegalArgumentException("Model name already exists");
        ModelVersion model = new ModelVersion(r.name(), r.algorithm(), r.contamination(), r.thresholdScore(), r.features(), Boolean.TRUE.equals(r.active()));
        if (model.isActive()) deactivateAll();
        return repository.save(model);
    }
    @Transactional
    public ModelVersion update(Long id, ModelVersionRequest r) {
        ModelVersion model = findById(id);
        model.update(r.name(), r.algorithm(), r.contamination(), r.thresholdScore(), r.features());
        if (Boolean.TRUE.equals(r.active())) activate(id);
        return repository.save(model);
    }
    @Transactional
    public void delete(Long id) { repository.delete(findById(id)); }
    public List<ModelVersion> active() { return repository.findByActiveTrueOrderByTrainedAtDesc(); }
    @Transactional
    public ModelVersion activate(Long id) { deactivateAll(); ModelVersion model = findById(id); model.setActive(true); return repository.save(model); }
    @Transactional
    public ModelVersion deactivate(Long id) { ModelVersion model = findById(id); model.setActive(false); return repository.save(model); }
    private void deactivateAll() { repository.findAll().forEach(m -> m.setActive(false)); }
}
