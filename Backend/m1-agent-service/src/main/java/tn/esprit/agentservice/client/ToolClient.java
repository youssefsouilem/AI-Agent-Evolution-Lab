package tn.esprit.agentservice.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(
        name = "tool-service",
        url = "${tools.service.url}",
        configuration = OpenFeignConfig.class
)
public interface ToolClient {

    @GetMapping("/api/tools")
    java.util.List<ToolDefinitionResponse> list();

    @PostMapping("/api/tools/{id}/execute")
    ToolExecuteResponse execute(
            @PathVariable("id") Long id,
            @RequestBody ToolExecuteRequest request
    );
}
