package com.example.anomalydetection.api;

import com.example.anomalydetection.domain.ModelVersion;
import com.example.anomalydetection.service.ModelVersionService;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/model-versions")
public class ModelVersionController {
    private final ModelVersionService service;
    public ModelVersionController(ModelVersionService service) { this.service = service; }
    @GetMapping public List<ModelVersionResponse> all() { return service.findAll().stream().map(ModelVersionResponse::from).toList(); }
    @GetMapping("/{id}") public ModelVersionResponse one(@PathVariable Long id) { return ModelVersionResponse.from(service.findById(id)); }
    @GetMapping("/active") public List<ModelVersionResponse> active() { return service.active().stream().map(ModelVersionResponse::from).toList(); }
    @PostMapping @ResponseStatus(HttpStatus.CREATED) public ModelVersionResponse create(@RequestBody ModelVersionRequest request) { return ModelVersionResponse.from(service.create(request)); }
    @PutMapping("/{id}") public ModelVersionResponse update(@PathVariable Long id, @RequestBody ModelVersionRequest request) { return ModelVersionResponse.from(service.update(id, request)); }
    @PostMapping("/{id}/activate") public ModelVersionResponse activate(@PathVariable Long id) { return ModelVersionResponse.from(service.activate(id)); }
    @PostMapping("/{id}/deactivate") public ModelVersionResponse deactivate(@PathVariable Long id) { return ModelVersionResponse.from(service.deactivate(id)); }
    @DeleteMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) public void delete(@PathVariable Long id) { service.delete(id); }
}
