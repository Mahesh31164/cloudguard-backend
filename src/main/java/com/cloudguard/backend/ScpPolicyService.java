
package com.cloudguard.backend;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.organizations.OrganizationsClient;
import software.amazon.awssdk.services.organizations.model.DescribePolicyResponse;
import software.amazon.awssdk.services.organizations.model.ListPoliciesResponse;
import software.amazon.awssdk.services.organizations.model.ListPoliciesForTargetResponse;
import software.amazon.awssdk.services.organizations.model.PolicySummary;
import software.amazon.awssdk.services.organizations.model.PolicyType;

@Service
public class ScpPolicyService {

    private final AwsConnectionService connectionService;

    public ScpPolicyService(AwsConnectionService connectionService) {
        this.connectionService = connectionService;
    }

    private OrganizationsClient createClient() {
        return OrganizationsClient.builder()
                .region(Region.US_EAST_1)
                .credentialsProvider(connectionService.getCredentialsProvider())
                .build();
    }

    public List<Map<String, Object>> getPolicies() {
        List<Map<String, Object>> policies = new ArrayList<>();

        try (OrganizationsClient client = createClient()) {
            String nextToken = null;

            do {
                final String token = nextToken;

                ListPoliciesResponse response = client.listPolicies(builder -> {
                    builder.filter(PolicyType.SERVICE_CONTROL_POLICY);

                    if (token != null) {
                        builder.nextToken(token);
                    }
                });

                for (PolicySummary policy : response.policies()) {
                    Map<String, Object> item = new HashMap<>();
                    item.put("id", policy.id());
                    item.put("name", policy.name());
                    item.put("description", policy.description());
                    item.put("arn", policy.arn());
                    policies.add(item);
                }

                nextToken = response.nextToken();
            } while (nextToken != null);
        }

        return policies;
    }

    public List<Map<String, Object>> getPolicyAttachments(String targetId) {
        List<Map<String, Object>> attachments = new ArrayList<>();

        try (OrganizationsClient client = createClient()) {
            attachments.addAll(getPoliciesAttachedTo(client, targetId));
        }

        return attachments;
    }

    public Map<String, Object> getPolicyDetails(String policyId) {
        try (OrganizationsClient client = createClient()) {
            DescribePolicyResponse response = client.describePolicy(builder ->
                    builder.policyId(policyId));

            var policy = response.policy();

            Map<String, Object> details = new HashMap<>();
            details.put("id", policy.policySummary().id());
            details.put("name", policy.policySummary().name());
            details.put("description", policy.policySummary().description());
            details.put("arn", policy.policySummary().arn());
            details.put("content", policy.content());

            return details;
        }
    }

    /**
     * Returns SCPs attached directly to an account, its parent OUs,
     * and its organization root.
     *
     * This reports policy attachments. It does not evaluate whether
     * a specific AWS request is allowed or denied.
     */
    public Map<String, Object> getApplicablePolicies(String accountId) {

        if (accountId == null || !accountId.matches("\\d{12}")) {
            throw new IllegalArgumentException(
                    "Provide a valid 12-digit AWS account ID.");
        }

        Map<String, Object> result = new LinkedHashMap<>();
        List<Map<String, Object>> applicablePolicies = new ArrayList<>();
        List<String> checkedTargets = new ArrayList<>();

        try (OrganizationsClient client = createClient()) {

            String managementAccountId = client.describeOrganization()
                    .organization()
                    .masterAccountId();

            result.put("accountId", accountId);

            if (accountId.equals(managementAccountId)) {
                result.put("managementAccount", true);
                result.put("checkedTargets", checkedTargets);
                result.put("policies", applicablePolicies);
                result.put("policyCount", 0);
                result.put(
                        "note",
                        "SCPs do not restrict the AWS Organizations " +
                        "management account. No effective SCP evaluation " +
                        "was performed.");
                return result;
            }

            String currentTarget = accountId;

            while (currentTarget != null) {
                checkedTargets.add(currentTarget);

                applicablePolicies.addAll(
                        getPoliciesAttachedTo(client, currentTarget));

                if (currentTarget.startsWith("r-")) {
                    break;
                }

                final String childId = currentTarget;

                var parentsResponse = client.listParents(builder ->
                        builder.childId(childId));

                if (parentsResponse.parents().isEmpty()) {
                    throw new IllegalStateException(
                            "No parent was found for organization target: "
                                    + childId);
                }

                currentTarget = parentsResponse.parents().get(0).id();
            }

            result.put("managementAccount", false);
            result.put("checkedTargets", checkedTargets);
            result.put("policies", applicablePolicies);
            result.put("policyCount", applicablePolicies.size());
            result.put(
                    "note",
                    "These SCPs are attached directly to the account or " +
                    "its parent hierarchy. This report does not prove that " +
                    "a specific request was denied by an SCP.");

            return result;
        }
    }

    private List<Map<String, Object>> getPoliciesAttachedTo(
            OrganizationsClient client,
            String targetId) {

        List<Map<String, Object>> policies = new ArrayList<>();
        String nextToken = null;

        do {
            final String token = nextToken;

            ListPoliciesForTargetResponse response =
                    client.listPoliciesForTarget(builder -> {
                        builder.targetId(targetId);
                        builder.filter(PolicyType.SERVICE_CONTROL_POLICY);

                        if (token != null) {
                            builder.nextToken(token);
                        }
                    });

            for (PolicySummary policy : response.policies()) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("id", policy.id());
                item.put("name", policy.name());
                item.put("description", policy.description());
                item.put("arn", policy.arn());
                item.put("attachedTo", targetId);
                policies.add(item);
            }

            nextToken = response.nextToken();

        } while (nextToken != null);

        return policies;
    }
}
