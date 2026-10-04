package com.cloudguard.backend;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;

import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sts.StsClient;
import software.amazon.awssdk.services.sts.auth.StsAssumeRoleCredentialsProvider;
import software.amazon.awssdk.services.sts.model.AssumeRoleRequest;
import software.amazon.awssdk.services.sts.model.GetCallerIdentityResponse;

/**
 * Local-development AWS connection manager.
 *
 * The backend assumes a customer-provided IAM role using the local
 * "cloudguard" AWS profile as its source identity. Only temporary STS
 * credentials are retained. The active connection is process-wide and is
 * intended for local single-user testing; add authenticated per-user storage
 * before exposing the application to multiple users or the public internet.
 */
@Service
public class AwsConnectionService implements AutoCloseable {

    private static final Pattern ACCOUNT_ID = Pattern.compile("\\d{12}");
    private static final Pattern ROLE_ARN = Pattern.compile(
            "^arn:aws:iam::(\\d{12}):role\\/.+$");

    private final DefaultCredentialsProvider sourceCredentials =
        DefaultCredentialsProvider.create();

    private StsAssumeRoleCredentialsProvider assumedCredentials;
    private String connectedAccountId;
    private String connectedRoleArn;

    public synchronized Map<String, Object> connect(
            String accountId, String roleArn, String externalId) {

        if (accountId == null || !ACCOUNT_ID.matcher(accountId).matches()) {
            throw new IllegalArgumentException("Enter a valid 12-digit AWS account ID.");
        }
        if (roleArn == null || !ROLE_ARN.matcher(roleArn).matches()) {
            throw new IllegalArgumentException(
                    "Enter a valid IAM role ARN, for example arn:aws:iam::123456789012:role/CloudGuardReadOnly.");
        }
        var roleMatcher = ROLE_ARN.matcher(roleArn);
        roleMatcher.matches();
        if (!accountId.equals(roleMatcher.group(1))) {
            throw new IllegalArgumentException(
                    "The account ID must match the account ID in the IAM role ARN.");
        }
        if (externalId == null || externalId.isBlank()) {
            throw new IllegalArgumentException(
                    "Enter the external ID configured in the IAM role's trust policy.");
        }

        StsAssumeRoleCredentialsProvider candidate = null;
        StsClient assumeRoleClient = null;
        boolean providerInstalled = false;
        try {
            assumeRoleClient = StsClient.builder()
                    .region(Region.US_EAST_1)
                    .credentialsProvider(sourceCredentials)
                    .build();

            AssumeRoleRequest request = AssumeRoleRequest.builder()
                    .roleArn(roleArn)
                    .roleSessionName("CloudGuardLocalSession")
                    .externalId(externalId.trim())
                    .durationSeconds(3600)
                    .build();

            candidate = StsAssumeRoleCredentialsProvider.builder()
                    .stsClient(assumeRoleClient)
                    .refreshRequest(request)
                    .build();

            try (StsClient verificationClient = StsClient.builder()
                    .region(Region.US_EAST_1)
                    .credentialsProvider(candidate)
                    .build()) {
                GetCallerIdentityResponse identity = verificationClient.getCallerIdentity();
                if (!accountId.equals(identity.account())) {
                    throw new IllegalArgumentException(
                            "AWS returned account " + identity.account()
                                    + ", which does not match the account ID you entered.");
                }
            }

            if (assumedCredentials != null) {
                assumedCredentials.close();
            }
            assumedCredentials = candidate;
            candidate = null;
            providerInstalled = true;
            connectedAccountId = accountId;
            connectedRoleArn = roleArn;
            return getStatus();

        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalArgumentException(
                "Could not assume the IAM role. Check AWS credentials, " +
                "role ARN, external ID, trust policy, and sts:AssumeRole permissions.",
                exception
            );
        } finally {
            if (candidate != null) {
                candidate.close();
            } else if (assumeRoleClient != null && !providerInstalled) {
                assumeRoleClient.close();
            }
        }
    }

    public synchronized Map<String, Object> getStatus() {
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("connected", assumedCredentials != null);
        status.put("accountId", connectedAccountId);
        status.put("roleArn", connectedRoleArn);
        status.put("mode", assumedCredentials == null ? "local-profile" : "assumed-role");
        status.put("message", assumedCredentials == null
                ? "Using the local AWS profile 'cloudguard'."
                : "Connected to AWS account " + connectedAccountId + " using temporary role credentials.");
        return status;
    }

    public synchronized AwsCredentialsProvider getCredentialsProvider() {
        if (assumedCredentials == null) {
            throw new IllegalStateException(
                "No AWS account is connected. Connect an AWS account first."
            );
        }

        return assumedCredentials;
    }

    public synchronized void disconnect() {
        if (assumedCredentials != null) {
            assumedCredentials.close();
            assumedCredentials = null;
        }
        connectedAccountId = null;
        connectedRoleArn = null;
    }

    @Override
    public synchronized void close() {
        disconnect();
        sourceCredentials.close();
    }
}
