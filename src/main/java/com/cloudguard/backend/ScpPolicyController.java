package com.cloudguard.backend;

import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/scp-policies")
public class ScpPolicyController {

    private final ScpPolicyService scpPolicyService;

    public ScpPolicyController(ScpPolicyService scpPolicyService) {
        this.scpPolicyService = scpPolicyService;
    }

    @GetMapping
    public List<Map<String, Object>> getPolicies() {
        return scpPolicyService.getPolicies();
    }

    @GetMapping("/target/{targetId}")
    public List<Map<String, Object>> getPolicyAttachments(
            @PathVariable String targetId) {
        return scpPolicyService.getPolicyAttachments(targetId);
    }

    @GetMapping("/{policyId}")
    public Map<String, Object> getPolicyDetails(
            @PathVariable String policyId) {
        return scpPolicyService.getPolicyDetails(policyId);
    }
}