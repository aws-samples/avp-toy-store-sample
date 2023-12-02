/*
 * Copyright 2015-2016 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"). You may not use this file except in compliance with the License. A copy of the License is located at
 *
 *     http://aws.amazon.com/apache2.0/
 *
 * or in the "license" file accompanying this file. This file is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */
package org.example.authorizerpolicies;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * AuthPolicy receives a set of allowed and denied methods and generates a valid
 * AWS policy for the API Gateway authorizer. The constructor receives the calling
 * user principal, the AWS account ID of the API owner, and an apiOptions object.
 * The apiOptions can contain an API Gateway RestApi Id, a region for the RestApi, and a
 * stage that calls should be allowed/denied for. For example
 * <p>
 * new AuthPolicy(principalId, AuthPolicy.PolicyDocument.getDenyAllPolicy(region, awsAccountId, restApiId, stage));
 *
 * @author Jack Kohn
 */
public class AuthPolicy {
    // IAM Policy Constants
    public static final String VERSION = "Version";
    public static final String STATEMENT = "Statement";
    public static final String EFFECT = "Effect";
    public static final String ACTION = "Action";
    public static final String NOT_ACTION = "NotAction";
    public static final String RESOURCE = "Resource";
    public static final String NOT_RESOURCE = "NotResource";
    public static final String CONDITION = "Condition";
    String principalId;
    transient PolicyDocument policyDocumentObject;
    Map<String, Object> policyDocument;
    public AuthPolicy(String principalId, PolicyDocument policyDocumentObject) {
        this.principalId = principalId;
        this.policyDocumentObject = policyDocumentObject;
    }

    public AuthPolicy() {
    }

    @Override
    public String toString() {
        return "AuthPolicy{" +
                "principalId='" + principalId + '\'' +
                ", policyDocumentObject=" + policyDocumentObject +
                ", policyDocument=" + policyDocument +
                '}';
    }

    public String getPrincipalId() {
        return principalId;
    }

    public void setPrincipalId(String principalId) {
        this.principalId = principalId;
    }

    /**
     * IAM Policies use capitalized field names, but Lambda by default will serialize object members using camel case
     * <p>
     * This method implements a custom serializer to return the IAM Policy as a well-formed JSON document, with the correct field names
     *
     * @return IAM Policy as a well-formed JSON document
     */
    public Map<String, Object> getPolicyDocument() {
        Map<String, Object> serializablePolicy = new HashMap<>();
        serializablePolicy.put(VERSION, policyDocumentObject.Version);
        Statement[] statements = policyDocumentObject.getStatement();
        Map<String, Object>[] serializableStatementArray = new Map[statements.length];
        for (int i = 0; i < statements.length; i++) {
            Map<String, Object> serializableStatement = new HashMap<>();
            Statement statement = statements[i];
            serializableStatement.put(EFFECT, statement.Effect);
            serializableStatement.put(ACTION, statement.Action);
            serializableStatement.put(RESOURCE, statement.getResource());
            serializableStatement.put(CONDITION, statement.getCondition());
            serializableStatementArray[i] = serializableStatement;
        }
        serializablePolicy.put(STATEMENT, serializableStatementArray);
        return serializablePolicy;
    }

    public void setPolicyDocument(PolicyDocument policyDocumentObject) {
        this.policyDocumentObject = policyDocumentObject;
    }

    public enum HttpMethod {
        GET, POST, PUT, DELETE, PATCH, HEAD, OPTIONS, ALL
    }

    /**
     * PolicyDocument represents an IAM Policy, specifically for the execute-api:Invoke action
     * in the context of a API Gateway Authorizer
     * <p>
     * Initialize the PolicyDocument with
     * the region where the RestApi is configured,
     * the AWS Account ID that owns the RestApi,
     * the RestApi identifier
     * and the Stage on the RestApi that the Policy will apply to
     */
    public static class PolicyDocument {

        static final String EXECUTE_API_ARN_FORMAT = "arn:aws:execute-api:%s:%s:%s/%s/%s/%s";

        String Version = "2012-10-17"; // override if necessary
        // context metadata
        transient String region;
        transient String awsAccountId;
        transient String restApiId;
        transient String stage;
        private Statement allowStatement;
        private Statement denyStatement;
        private List<Statement> statements;

        /**
         * Creates a new PolicyDocument with the given context,
         * and initializes two base Statement objects for allowing and denying access to API Gateway methods
         *
         * @param region       the region where the RestApi is configured
         * @param awsAccountId the AWS Account ID that owns the RestApi
         * @param restApiId    the RestApi identifier
         * @param stage        and the Stage on the RestApi that the Policy will apply to
         */
        public PolicyDocument(String region, String awsAccountId, String restApiId, String stage) {
            this.region = region;
            this.awsAccountId = awsAccountId;
            this.restApiId = restApiId;
            this.stage = stage;
            allowStatement = Statement.getEmptyInvokeStatement("Allow");
            denyStatement = Statement.getEmptyInvokeStatement("Deny");
            this.statements = new ArrayList<>();
            statements.add(allowStatement);
            statements.add(denyStatement);
        }

        /**
         * Generates a new PolicyDocument with a single statement that allows the requested method/resourcePath
         *
         * @param region       API Gateway region
         * @param awsAccountId AWS Account that owns the API Gateway RestApi
         * @param restApiId    RestApi identifier
         * @param stage        Stage name
         * @param method       HttpMethod to allow
         * @param resourcePath Resource path to allow
         * @return new PolicyDocument that allows the requested method/resourcePath
         */
        public static PolicyDocument getAllowOnePolicy(String region, String awsAccountId, String restApiId, String stage, HttpMethod method, String resourcePath) {
            PolicyDocument policyDocument = new PolicyDocument(region, awsAccountId, restApiId, stage);
            policyDocument.allowMethod(method, resourcePath);
            return policyDocument;

        }

        /**
         * Generates a new PolicyDocument with a single statement that denies the requested method/resourcePath
         *
         * @param region       API Gateway region
         * @param awsAccountId AWS Account that owns the API Gateway RestApi
         * @param restApiId    RestApi identifier
         * @param stage        Stage name
         * @param method       HttpMethod to deny
         * @param resourcePath Resource path to deny
         * @return new PolicyDocument that denies the requested method/resourcePath
         */
        public static PolicyDocument getDenyOnePolicy(String region, String awsAccountId, String restApiId, String stage, HttpMethod method, String resourcePath) {
            PolicyDocument policyDocument = new PolicyDocument(region, awsAccountId, restApiId, stage);
            policyDocument.denyMethod(method, resourcePath);
            return policyDocument;

        }

        public static PolicyDocument getAllowAllPolicy(String region, String awsAccountId, String restApiId, String stage) {
            return getAllowOnePolicy(region, awsAccountId, restApiId, stage, HttpMethod.ALL, "*");
        }

        public static PolicyDocument getDenyAllPolicy(String region, String awsAccountId, String restApiId, String stage) {
            return getDenyOnePolicy(region, awsAccountId, restApiId, stage, HttpMethod.ALL, "*");
        }

        public String getVersion() {
            return Version;
        }

        public void setVersion(String version) {
            Version = version;
        }

        public Statement[] getStatement() {
            return statements.toArray(new Statement[statements.size()]);
        }

        // Static methods

        public void allowMethod(HttpMethod httpMethod, String resourcePath) {
            addResourceToStatement(allowStatement, httpMethod, resourcePath);
        }

        public void denyMethod(HttpMethod httpMethod, String resourcePath) {
            addResourceToStatement(denyStatement, httpMethod, resourcePath);
        }

        public void addStatement(Statement statement) {
            statements.add(statement);
        }

        private void addResourceToStatement(Statement statement, HttpMethod httpMethod, String resourcePath) {
            // resourcePath must start with '/'
            // to specify the root resource only, resourcePath should be an empty string
            if (resourcePath.equals("/")) {
                resourcePath = "";
            }
            String resource = resourcePath.startsWith("/") ? resourcePath.substring(1) : resourcePath;
            String method = httpMethod == HttpMethod.ALL ? "*" : httpMethod.toString();
            statement.addResource(String.format(EXECUTE_API_ARN_FORMAT, region, awsAccountId, restApiId, stage, method, resource));
        }

        @Override
        public String toString() {
            return "PolicyDocument{" +
                    "Version='" + Version + '\'' +
                    ", region='" + region + '\'' +
                    ", awsAccountId='" + awsAccountId + '\'' +
                    ", restApiId='" + restApiId + '\'' +
                    ", stage='" + stage + '\'' +
                    ", allowStatement=" + allowStatement +
                    ", denyStatement=" + denyStatement +
                    ", statements=" + statements +
                    '}';
        }
    }

    static class Statement {

        String Effect;
        String Action;
        Map<String, Map<String, Object>> Condition;

        private List<String> resourceList;

        public Statement() {

        }

        public Statement(String effect, String action, List<String> resourceList, Map<String, Map<String, Object>> condition) {
            this.Effect = effect;
            this.Action = action;
            this.resourceList = resourceList;
            this.Condition = condition;
        }

        public static Statement getEmptyInvokeStatement(String effect) {
            return new Statement(effect, "execute-api:Invoke", new ArrayList<>(), new HashMap<>());
        }

        public String getEffect() {
            return Effect;
        }

        public void setEffect(String effect) {
            this.Effect = effect;
        }

        public String getAction() {
            return Action;
        }

        public void setAction(String action) {
            this.Action = action;
        }

        public String[] getResource() {
            return resourceList.toArray(new String[resourceList.size()]);
        }

        public void addResource(String resource) {
            resourceList.add(resource);
        }

        public Map<String, Map<String, Object>> getCondition() {
            return Condition;
        }

        public void addCondition(String operator, String key, Object value) {
            Condition.put(operator, Collections.singletonMap(key, value));
        }

        @Override
        public String toString() {
            return "Statement{" +
                    "Effect='" + Effect + '\'' +
                    ", Action='" + Action + '\'' +
                    ", Condition=" + Condition +
                    ", resourceList=" + resourceList +
                    '}';
        }
    }

}
