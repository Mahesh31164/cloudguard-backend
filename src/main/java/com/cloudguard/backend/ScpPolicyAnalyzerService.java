package com.cloudguard.backend;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Service
public class ScpPolicyAnalyzerService {

	private final JsonMapper objectMapper = new JsonMapper();

    public Map<String, Object> analyzePolicy(
            String policyId,
            String policyName,
            String policyContent) throws Exception {

        JsonNode root = objectMapper.readTree(policyContent);

        List<Map<String, Object>> findings = new ArrayList<>();
        List<Map<String, Object>> statements = new ArrayList<>();

        JsonNode statementNode = root.get("Statement");

        if (statementNode == null || statementNode.isNull()) {
            throw new IllegalArgumentException(
                    "The policy does not contain a Statement field.");
        }

        if (statementNode.isArray()) {
            for (JsonNode statement : statementNode) {
                analyzeStatement(statement, statements, findings);
            }
        } else if (statementNode.isObject()) {
            analyzeStatement(statementNode, statements, findings);
        } else {
            throw new IllegalArgumentException(
                    "Statement must be a JSON object or array.");
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("policyId", policyId);
        result.put("policyName", policyName);
        result.put("version", root.path("Version").asText("Not specified"));
        result.put("statementCount", statements.size());
        result.put("findingsCount", findings.size());
        result.put("statements", statements);
        result.put("findings", findings);

        result.put(
                "analysisNote",
                "This is a structural analysis of one SCP document, " +
                "not a definitive AWS authorization decision. Effective " +
                "permissions depend on other applicable policies, " +
                "the target account, the requested action, and request context.");

        return result;
    }

    private void analyzeStatement(
            JsonNode statement,
            List<Map<String, Object>> statements,
            List<Map<String, Object>> findings) {

        String sid = statement.path("Sid").asText("Unnamed statement");
        String effect = statement.path("Effect").asText("Not specified");

        JsonNode actionNode = statement.get("Action");
        JsonNode notActionNode = statement.get("NotAction");
        JsonNode resourceNode = statement.get("Resource");
        JsonNode conditionNode = statement.get("Condition");

        Map<String, Object> details = new LinkedHashMap<>();

        details.put("sid", sid);
        details.put("effect", effect);
        details.put("action", toDisplayValue(actionNode));
        details.put("notAction", toDisplayValue(notActionNode));
        details.put("resource", toDisplayValue(resourceNode));
        details.put(
                "condition",
                conditionNode == null ? null : conditionNode);

        details.put("explanation", explainStatement(
                effect, actionNode, notActionNode, conditionNode));

        statements.add(details);

        if ("Deny".equalsIgnoreCase(effect)) {
            addFinding(
                    findings,
                    "DENY_STATEMENT",
                    "Deny statement detected",
                    sid,
                    "This statement denies matching requests when its " +
                    "action pattern and conditions apply.",
                    "Review the action patterns, resources and conditions " +
                    "to understand which requests match this statement.");
        } else if ("Allow".equalsIgnoreCase(effect)) {
            addFinding(
                    findings,
                    "ALLOW_STATEMENT",
                    "Allow statement detected",
                    sid,
                    "This statement defines an SCP allow rule. It does not " +
                    "by itself grant IAM permissions to a principal.",
                    "Check the other applicable SCPs and identity-based " +
                    "permissions before assessing effective access.");
        } else {
            addFinding(
                    findings,
                    "UNKNOWN_EFFECT",
                    "Unrecognized or missing effect",
                    sid,
                    "The statement's Effect is not a recognized Allow or Deny.",
                    "Verify the policy document and its Effect value.");
        }

        if (notActionNode != null) {
            addFinding(
                    findings,
                    "NOT_ACTION_USED",
                    "NotAction pattern used",
                    sid,
                    "NotAction describes actions excluded from the " +
                    "statement's action matching set. Its meaning depends " +
                    "on the Effect and other statement fields.",
                    "Inspect the excluded actions carefully; do not interpret " +
                    "NotAction as a simple allow list.");
        }

        if (conditionNode != null && !conditionNode.isEmpty()) {
            addFinding(
                    findings,
                    "CONDITION_PRESENT",
                    "Conditional rule detected",
                    sid,
                    "This statement depends on one or more policy conditions.",
                    "Check the condition operators, keys and expected values " +
                    "against the actual request context.");
        }

        if (resourceNode == null) {
            addFinding(
                    findings,
                    "RESOURCE_MISSING",
                    "Resource field not found",
                    sid,
                    "No Resource field was found in this statement.",
                    "Verify the policy document against the relevant AWS " +
                    "policy syntax requirements.");
        }
    }

    private String explainStatement(
            String effect,
            JsonNode actionNode,
            JsonNode notActionNode,
            JsonNode conditionNode) {

        StringBuilder explanation = new StringBuilder();

        if ("Deny".equalsIgnoreCase(effect)) {
            explanation.append(
                    "The statement denies requests matching its rules. ");
        } else if ("Allow".equalsIgnoreCase(effect)) {
            explanation.append(
                    "The statement specifies an SCP allow rule; it does " +
                    "not independently grant permissions. ");
        } else {
            explanation.append(
                    "The statement has an unrecognized or missing Effect. ");
        }

        if (actionNode != null) {
            explanation.append(
                    "Action specifies the actions this statement matches. ");
        }

        if (notActionNode != null) {
            explanation.append(
                    "NotAction specifies actions excluded from the " +
                    "statement's matching set. ");
        }

        if (conditionNode != null && !conditionNode.isEmpty()) {
            explanation.append(
                    "Conditions further determine when the statement applies. ");
        }

        explanation.append(
                "The actual result depends on the request and other applicable policies.");

        return explanation.toString();
    }

    private Object toDisplayValue(JsonNode node) {
        if (node == null) {
            return null;
        }

        if (node.isArray()) {
            List<String> values = new ArrayList<>();

            for (JsonNode item : node) {
                values.add(item.asText());
            }

            return values;
        }

        if (node.isValueNode()) {
            return node.asText();
        }

        return node;
    }

    private void addFinding(
            List<Map<String, Object>> findings,
            String code,
            String title,
            String statementId,
            String explanation,
            String recommendation) {

        Map<String, Object> finding = new LinkedHashMap<>();

        finding.put("code", code);
        finding.put("title", title);
        finding.put("statementId", statementId);
        finding.put("explanation", explanation);
        finding.put("recommendation", recommendation);

        findings.add(finding);
    }
}