
package com.cloudguard.backend;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.organizations.OrganizationsClient;
import software.amazon.awssdk.services.organizations.model.Account;
import software.amazon.awssdk.services.organizations.model.Child;
import software.amazon.awssdk.services.organizations.model.ChildType;
import software.amazon.awssdk.services.organizations.model.DescribeOrganizationResponse;
import software.amazon.awssdk.services.organizations.model.ListChildrenResponse;
import software.amazon.awssdk.services.organizations.model.ListOrganizationalUnitsForParentResponse;
import software.amazon.awssdk.services.organizations.model.ListRootsResponse;
import software.amazon.awssdk.services.organizations.model.OrganizationalUnit;
import software.amazon.awssdk.services.organizations.model.Root;

@Service
public class OrganizationService {

    private final AwsConnectionService connectionService;

    public OrganizationService(AwsConnectionService connectionService) {
        this.connectionService = connectionService;
    }

    public Map<String, Object> getOrganization() {

        try (OrganizationsClient client = OrganizationsClient.builder()
                .region(Region.US_EAST_1)
                .credentialsProvider(connectionService.getCredentialsProvider())
                .build()) {

            DescribeOrganizationResponse response =
                    client.describeOrganization();

            var organization = response.organization();
            String managementAccountId = organization.masterAccountId();

            List<Map<String, Object>> roots = new ArrayList<>();

            ListRootsResponse rootsResponse = client.listRoots();

            for (Root root : rootsResponse.roots()) {

                Map<String, Object> rootData = new HashMap<>();

                rootData.put("id", root.id());
                rootData.put("name", "Organization Root");
                rootData.put("type", "root");

                rootData.put(
                        "units",
                        getOrganizationalUnits(
                                client, root.id(), managementAccountId));

                rootData.put(
                        "accounts",
                        getAccountsForParent(
                                client, root.id(), managementAccountId));

                roots.add(rootData);
            }

            Map<String, Object> result = new HashMap<>();

            result.put("id", organization.id());
            result.put("arn", organization.arn());
            result.put("featureSet", organization.featureSetAsString());
            result.put("masterAccountId", managementAccountId);
            result.put("roots", roots);

            return result;
        }
    }

    private List<Map<String, Object>> getOrganizationalUnits(
            OrganizationsClient client,
            String parentId,
            String managementAccountId) {

        List<Map<String, Object>> units = new ArrayList<>();
        String nextToken = null;

        do {
            final String token = nextToken;

            ListOrganizationalUnitsForParentResponse response =
                    client.listOrganizationalUnitsForParent(builder -> {
                        builder.parentId(parentId);

                        if (token != null) {
                            builder.nextToken(token);
                        }
                    });

            for (OrganizationalUnit unit : response.organizationalUnits()) {

                Map<String, Object> unitData = new HashMap<>();

                unitData.put("id", unit.id());
                unitData.put("name", unit.name());
                unitData.put("type", "ou");

                unitData.put(
                        "accounts",
                        getAccountsForParent(
                                client, unit.id(), managementAccountId));

                unitData.put(
                        "units",
                        getOrganizationalUnits(
                                client, unit.id(), managementAccountId));

                units.add(unitData);
            }

            nextToken = response.nextToken();

        } while (nextToken != null);

        return units;
    }

    private List<Map<String, Object>> getAccountsForParent(
            OrganizationsClient client,
            String parentId,
            String managementAccountId) {

        List<Map<String, Object>> accounts = new ArrayList<>();
        String nextToken = null;

        do {
            final String token = nextToken;

            ListChildrenResponse response =
                    client.listChildren(builder -> {
                        builder.parentId(parentId);
                        builder.childType(ChildType.ACCOUNT);

                        if (token != null) {
                            builder.nextToken(token);
                        }
                    });

            for (Child child : response.children()) {

                Map<String, Object> accountData = new HashMap<>();

                try {
                    Account account = client.describeAccount(builder ->
                            builder.accountId(child.id())).account();

                    accountData.put("id", account.id());
                    accountData.put("name", account.name());
                    accountData.put("email", account.email());
                    accountData.put("status", account.stateAsString());

                    accountData.put(
                            "type",
                            child.id().equals(managementAccountId)
                                    ? "Management account"
                                    : "Member account");

                } catch (Exception exception) {
                    accountData.put("id", child.id());
                    accountData.put("name", "Account " + child.id());
                    accountData.put(
                            "type",
                            child.id().equals(managementAccountId)
                                    ? "Management account"
                                    : "Member account");
                }

                accounts.add(accountData);
            }

            nextToken = response.nextToken();

        } while (nextToken != null);

        return accounts;
    }
}