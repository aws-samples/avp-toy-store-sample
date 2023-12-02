package org.example.api;

import com.amazonaws.services.cognitoidp.AWSCognitoIdentityProvider;
import com.amazonaws.services.cognitoidp.AWSCognitoIdentityProviderClientBuilder;
import com.amazonaws.services.cognitoidp.model.*;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPResponse;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.google.gson.Gson;
import org.example.config.RoleCedarTemplates;
import org.example.util.CognitoJwtVerifier;
import software.amazon.awssdk.services.verifiedpermissions.VerifiedPermissionsClient;
import software.amazon.awssdk.services.verifiedpermissions.model.*;
import org.json.simple.JSONObject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class PermissionHandler {

    public static final String POLICY_STORE_ID = System.getenv("policyStoreId");

    public static final String USER_POOL_ID = System.getenv("userPoolId");
    public final AWSCognitoIdentityProvider awsCognitoIdentityProvider
            = AWSCognitoIdentityProviderClientBuilder.defaultClient();

    private final VerifiedPermissionsClient avpClient = VerifiedPermissionsClient.builder().build();

    private final CognitoJwtVerifier cognitoJwtVerifier = new CognitoJwtVerifier();

    public APIGatewayV2HTTPResponse listUsers(APIGatewayV2HTTPEvent event, Context context) {
        try {

            String storeId = event.getPathParameters().get("store-id");

            String templateForRole = RoleCedarTemplates.getCedarTemplateIdFromHttpPath(event.getRouteKey());

            EntityIdentifier store = EntityIdentifier.builder().
                    entityId(storeId).
                    entityType("avp::sample::toy::store::Store").build();

            List<PolicyItem> policies = getPoliciesForRole(templateForRole, store);
            List<String> users = getUsersFromPolicies(policies);

            APIGatewayV2HTTPResponse response = new APIGatewayV2HTTPResponse();
            response.setStatusCode(200);
            response.setBody(new Gson().toJson(users));

            return response;
        } catch (ValidationException e) {
            System.out.println("Validation exception from AVP while processing request" );
            System.out.println(e.fieldList());
            throw e;
        }
    }

    private List<PolicyItem> getPoliciesForRole(String templateForRole, EntityIdentifier store) {
        List<PolicyItem> policies = avpClient.listPolicies(
                ListPoliciesRequest.builder().
                        policyStoreId(POLICY_STORE_ID).
                        filter(PolicyFilter.builder().
                                policyTemplateId(templateForRole).
                                policyType(PolicyType.TEMPLATE_LINKED).
                                resource(EntityReference.builder().
                                        identifier(store).build()).build()
                        ).build()).
                policies();
        return policies;
    }
    /*
     * This function is responsible for extracting the username from the sub. It implements this by calling Amazon Cognito's 
     * ListUsers API once per user . In a production application, we recommend batching multiple sub's in the request and calling it once. 
     * You might also consider caching the response based if you expect you application to hit Cognito API request limits (https://docs.aws.amazon.com/cognito/latest/developerguide/limits.html) 
     * 
     */
    private List<String> getUsersFromPolicies(List<PolicyItem> policies) {
        List<String> principals = new ArrayList<>();
        policies.forEach((policyItem -> {
            String principal = policyItem.principal().entityId();
            String sub = principal.split("\\|")[1];
            System.out.println("Trying to get username for sub " + sub + " with filter " + "sub=\"" + sub + "\"");
            List<UserType> users = awsCognitoIdentityProvider.listUsers(new ListUsersRequest().
                    withUserPoolId(USER_POOL_ID).withFilter("sub=\"" + sub + "\"")
            ).getUsers();
            if (users.size() == 0) {
                throw new InternalError("No user found with sub " + sub);
            }
            String username = users.get(0).getUsername();
            System.out.println("Got username " + username + "for sub " + sub);
            principals.add(username);
        }));
        return principals;
    }
    public APIGatewayV2HTTPResponse grantAccess(APIGatewayV2HTTPEvent event, Context context) {
            try {


                System.out.println(event);
                String employeeId = event.getPathParameters().get("employee-id");
                String sub = getSubFromUsername(employeeId);
                String storeId = event.getPathParameters().get("store-id");

                CreatePolicyRequest.Builder createPolicyRequestBuilder = CreatePolicyRequest.builder().
                        policyStoreId(POLICY_STORE_ID);
                EntityIdentifier user = EntityIdentifier.builder().
                        entityId(USER_POOL_ID + "|" + sub).
                        entityType("avp::sample::toy::store::User").build();
                EntityIdentifier store = EntityIdentifier.builder().
                        entityId(storeId).
                        entityType("avp::sample::toy::store::Store").build();

                ListPoliciesRequest.Builder listPolicyRequestBuilder = ListPoliciesRequest.builder();
                listPolicyRequestBuilder.policyStoreId(POLICY_STORE_ID);
                PolicyFilter policyFilter = PolicyFilter.builder().policyType(PolicyType.TEMPLATE_LINKED).
                        policyTemplateId(RoleCedarTemplates.getCedarTemplateIdFromHttpPath(event.getRouteKey())).
                        principal(EntityReference.builder().identifier(user).build()).
                        resource(EntityReference.builder().identifier(store).build()).build();
                listPolicyRequestBuilder.filter(policyFilter);

                List<PolicyItem> policies = avpClient.listPolicies(
                                ListPoliciesRequest.builder().policyStoreId(POLICY_STORE_ID).
                                        filter(policyFilter).build()).
                        policies();
                System.out.println("Filters " + policyFilter.toString());
                System.out.println("Got these policies " + policies.toString());
                if (policies.size() > 0) {
                    APIGatewayV2HTTPResponse response = new APIGatewayV2HTTPResponse();
                    System.out.println("The policy already exists for principal " + employeeId + " and store " + storeId);
                    response.setStatusCode(200);
                    JSONObject bodyJson = new JSONObject();
                    bodyJson.put("policy-id", "createPolicyResponse.policyId()");
                    response.setBody(bodyJson.toJSONString());
                    return response;
                }
                System.out.println("Creating policy for principal " + employeeId + " with principal id " + USER_POOL_ID + "|" + sub + " and store " + storeId);

                createPolicyRequestBuilder.definition(
                        PolicyDefinition.builder().
                                templateLinked(
                                        TemplateLinkedPolicyDefinition.builder().
                                                policyTemplateId(
                                                        RoleCedarTemplates.getCedarTemplateIdFromHttpPath(
                                                                event.getRouteKey())).
                                                principal(user).
                                                resource(store).
                                                build()
                                ).build());


                CreatePolicyResponse createPolicyResponse = avpClient.createPolicy(createPolicyRequestBuilder.build());

                APIGatewayV2HTTPResponse response = new APIGatewayV2HTTPResponse();
                response.setStatusCode(200);
                response.setBody("{\"policy-id\":\"" + createPolicyResponse.policyId() + "\"}");
                return response;
            } catch (ValidationException e) {
                System.out.println("Validation exception from AVP while processing request" );
                System.out.println(e.fieldList());
                throw e;
            }
    }

    private String getSubFromUsername(String employeeId) {
        AdminGetUserRequest adminGetUserRequest = new AdminGetUserRequest();
        adminGetUserRequest.setUsername(employeeId);
        adminGetUserRequest.setUserPoolId(USER_POOL_ID);
        List<AttributeType> attributes = awsCognitoIdentityProvider.adminGetUser(adminGetUserRequest).getUserAttributes();
        return attributes.stream()
                .filter(attribute -> attribute.getName().equals("sub"))
                .collect(Collectors.toList()).stream().findFirst().orElseThrow(() -> {
                    throw new InternalError("No sub attribute present in JWT token");
                }).getValue();
    }
}
