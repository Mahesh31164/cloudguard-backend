
package com.cloudguard.backend;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/scp-policies")
public class ScpPolicyAnalyzerController {

    private final ScpPolicyService scpPolicyService;
    private final ScpPolicyAnalyzerService analyzerService;

    public ScpPolicyAnalyzerController(
            ScpPolicyService scpPolicyService,
            ScpPolicyAnalyzerService analyzerService) {
        this.scpPolicyService = scpPolicyService;
        this.analyzerService = analyzerService;
    }

    @GetMapping("/{policyId}/analysis")
    public Map<String, Object> analyzePolicy(
            @PathVariable String policyId) {

        try {
            Map<String, Object> policy =
                    scpPolicyService.getPolicyDetails(policyId);

            String policyName = String.valueOf(policy.get("name"));
            String policyContent = String.valueOf(policy.get("content"));

            return analyzerService.analyzePolicy(
                    policyId,
                    policyName,
                    policyContent);

        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    exception.getMessage(),
                    exception);
        } catch (Exception exception) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Unable to analyze this SCP policy.",
                    exception);
        }
    }

    @GetMapping("/applicable/{accountId}")
    public Map<String, Object> getApplicablePolicies(
            @PathVariable String accountId) {

        try {
            return scpPolicyService.getApplicablePolicies(accountId);

        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    exception.getMessage(),
                    exception);

        } catch (Exception exception) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Unable to retrieve applicable SCP policies.",
                    exception);
        }
    }
}
