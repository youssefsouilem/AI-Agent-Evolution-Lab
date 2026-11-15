package tn.esprit.agentservice.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(
        name = "tool-service",
        url = "${tools.service.url}",
        configuration = OpenFeignConfig.class
)
public interface ToolClient {

    @PostMapping("/api/tools/{id}/execute")
    ToolExecuteResponse execute(
            @PathVariable("id") Long id,
            @RequestBody ToolExecuteRequest request
    );
}
