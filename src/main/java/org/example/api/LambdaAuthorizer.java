package org.example.api;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPResponse;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.google.gson.Gson;
import org.apache.commons.lang3.exception.ExceptionUtils;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.apache.commons.lang3.tuple.Pair;
import org.example.authorizerpolicies.AuthPolicy;
import org.example.config.EntityTypesConstants;
import org.example.config.HttpPathToCedarActionMap;

import java.io.IOException;
import java.net.URI;
import java.util.*;
import java.util.ArrayList;
import java.util.stream.Collectors;

import com.fasterxml.jackson.core.type.TypeReference;
import org.example.entityBuilders.ResourceEntityBuilderFactory;
import org.example.entityBuilders.UserEntityBuilder;
import org.example.util.CognitoJwtVerifier;
import org.json.simple.JSONObject;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.verifiedpermissions.VerifiedPermissionsClient;
import software.amazon.awssdk.services.verifiedpermissions.VerifiedPermissionsClientBuilder;
import software.amazon.awssdk.services.verifiedpermissions.model.*;
import com.amazonaws.xray.AWSXRay;
import com.amazonaws.xray.entities.Subsegment;

public class LambdaAuthorizer {
    // All orders have this entity as the parent. It helps identity entities of type order and give permissions to all orders in the policy head
    // this will no longer be required when Cedar support the "is" operator

    private static final String AUTHORIZATION_HEADER_NAME = "authorization";
    private static final String POLICY_STORE_ID = System.getenv("policyStoreId");
    private final ResourceEntityBuilderFactory resourceEntityBuilderFactory = new ResourceEntityBuilderFactory();

    private final UserEntityBuilder userEntityBuilder = new UserEntityBuilder();

    private final VerifiedPermissionsClient avpClient = VerifiedPermissionsClient.builder().build();

    private final CognitoJwtVerifier cognitoJwtVerifier = new CognitoJwtVerifier();

    public AuthPolicy authorizeRequest(APIGatewayV2HTTPEvent event, Context context) {

        System.out.println("request" + event);

        DecodedJWT token = cognitoJwtVerifier.verifyToken(event.getHeaders().get(AUTHORIZATION_HEADER_NAME));

        List<String> authorizedActions =
                this.getAuthorizedActionsFromAvp(event);
        List<String> allActions = HttpPathToCedarActionMap.getRelatedCedarActions(event.getRouteKey());
        String sub = token.getSubject();
        AuthPolicy authPolicy = buildAuthorizerResponse(sub, event.getRequestContext(),
                authorizedActions, allActions, event.getPathParameters());
        System.out.println(authPolicy);
        return authPolicy;

    }

    public APIGatewayV2HTTPResponse getAuthorizedActions(APIGatewayV2HTTPEvent event, Context context) {
        List<String> authorizedActions = getAuthorizedActionsFromAvp(event);
        return ok(new Gson().toJson(authorizedActions));
    }

    private List<String> getAuthorizedActionsFromAvp(APIGatewayV2HTTPEvent event) {
        try {
            System.out.println("request" + event);
            List<EntityItem> entities = new ArrayList<>();
            EntityIdentifier resourceEntity =
                    resourceEntityBuilderFactory.getResourceEntityBuilder(
                            event.getRouteKey()).getEntities(event, entities).getLeft();

            List<ActionIdentifier> actionEntities =
                    HttpPathToCedarActionMap.
                            getRelatedCedarActions(event.getRouteKey()).
                            stream().map(action ->
                                    ActionIdentifier.
                                            builder().
                                            actionId(action).
                                            actionType(EntityTypesConstants.ACTION_ENTITY_TYPE).build())
                            .collect(Collectors.toList());

            EntityIdentifier userEntity = userEntityBuilder.getEntities(event, entities).getLeft();

            EntitiesDefinition entityDefinition = EntitiesDefinition.builder().
                    entityList(entities).
                    build();

            List<String> authorizedActions = new ArrayList<>();

            boolean optimized = Boolean.parseBoolean(event.getQueryStringParameters().getOrDefault("optimized", "false"));
            if (optimized) {
                Subsegment subsegment = AWSXRay.beginSubsegment("IsAuthorizedBatch");
                try {
                    Collection<BatchIsAuthorizedInputItem> batchIsAuthorizedInputItem = actionEntities.stream()
                            .map(actionEntity -> BatchIsAuthorizedInputItem.builder().principal(userEntity)
                                    .action(actionEntity)
                                    .resource(resourceEntity)
                                    .build())
                            .collect(Collectors.toList());
                    BatchIsAuthorizedResponse batchIsAuthorizedResponse = avpClient.batchIsAuthorized(BatchIsAuthorizedRequest.builder()
                            .policyStoreId(POLICY_STORE_ID)
                            .entities(entityDefinition)
                            .requests(batchIsAuthorizedInputItem).build());



                    for (int i=0;i<batchIsAuthorizedResponse.results().size();i++) {
                        // BatchIsAuthorized maintains the order of the response
                        if(batchIsAuthorizedResponse.results().get(i).decision().equals(Decision.ALLOW)) {
                            authorizedActions.add(batchIsAuthorizedResponse.results().get(i).request().action().actionId());
                        }
                    }
                    System.out.println("Authorized actions: " + authorizedActions);
                } catch (Exception e) {
                    subsegment.addException(e);
                    throw e;
                } finally {
                    AWSXRay.endSubsegment();
                }

            } else {
                for (ActionIdentifier actionEntity : actionEntities) {
                    //System.out.println("Calling verified permissions for action : " + actionEntity.actionId());
                    Subsegment subsegment = AWSXRay.beginSubsegment("IsAuthorized");
                    try {
                        subsegment.putAnnotation("Lambda Function", "LambdaAuthorizer");

                        IsAuthorizedResponse authorizationResponse =
                                avpClient.isAuthorized(IsAuthorizedRequest.builder()
                                        .policyStoreId(POLICY_STORE_ID)
                                        .principal(userEntity)
                                        .action(actionEntity)
                                        .resource(resourceEntity)
                                        .entities(entityDefinition)
                                        .build());
                        if (authorizationResponse.decision().equals(Decision.ALLOW)) {
                            authorizedActions.add(actionEntity.actionId());
                        }
                    } catch (Exception e) {
                        subsegment.addException(e);
                        throw e;
                    } finally {
                        AWSXRay.endSubsegment();
                    }
                }
            }
            return authorizedActions;
        } catch (ValidationException e) {
            System.out.println("Validation exception from AVP while processing request");
            System.out.println(e.fieldList());
            throw e;
        } catch (Exception e) {
            System.out.println("Error in processing request");
            System.out.println(e.getMessage() + " " + e.getCause());
            System.out.println(ExceptionUtils.getStackTrace(e));
            throw e;
        }
    }

    private APIGatewayV2HTTPResponse ok(String body) {
        return APIGatewayV2HTTPResponse.builder()
                .withStatusCode(200)
                .withBody(body)
                .withIsBase64Encoded(false).build();
    }

    private APIGatewayV2HTTPResponse error(JSONObject response, Exception exc) {
        String exceptionString = String.format("error: %s: %s", exc.getMessage(), Arrays.toString(exc.getStackTrace()));
        response.put("Exception", exceptionString);
        return APIGatewayV2HTTPResponse.builder()
                .withStatusCode(500)
                .withBody(response.toJSONString())
                .withIsBase64Encoded(false).build();
    }

    /*
     * This function is responsible for generating an IAM policy that is used by Amazon API-Gateway to cache the authorizer response
     *
     * The return is an IAM policy with the below details
     *       1. Principal which in my case is the user sub from the JWT token
     *       2. AccountId: AWS account id
     *       3. ApiId: API id : This is the key for the specific API
     *       4. Stage
     *       5. Statements
     *
     * Statements contains a list of allowed <HttpMethod, HttpPath> and denied <HttpMethod, HttpPath>.
     *
     * The Tuple<HttpMethod, HttpPath> is a pair of HttpMethod and HttpPath.
     * For example, if the request is for the following path
     * https://<api-id>.execute-api.<region>.amazonaws.com/<stage>/store/store-1/order/order-1
     *
     * The Tuple<HttpMethod, HttpPath> will be
     * Tuple<HttpMethod, HttpPath> - (GET, /store/store-1/order/order-1)
     *
     * API gateway caches the IAM policy and authorizes future requests accordingly
     */
    private AuthPolicy buildAuthorizerResponse(String sub, APIGatewayV2HTTPEvent.RequestContext
            requestContext, List<String> allowedActions, List<String> allActions, Map<String, String> pathParameters) {
        String pathWithoutStage = pathParameters.get("proxy");

        String accountId = requestContext.getAccountId();

        AuthPolicy.PolicyDocument policyDocument = new AuthPolicy.PolicyDocument(
                System.getenv("AWS_REGION"),
                accountId,
                requestContext.getApiId(),
                requestContext.getStage()
        );

        for (String action : allActions) {
            Pair<String, AuthPolicy.HttpMethod> httpPathForAction =
                    HttpPathToCedarActionMap.getHttpPathForAction(action, pathParameters);
            if (allowedActions.contains(action)) {
                policyDocument.allowMethod(httpPathForAction.getRight(), httpPathForAction.getLeft());
            } else {
                policyDocument.denyMethod(httpPathForAction.getRight(), httpPathForAction.getLeft());
            }
        }
        return new AuthPolicy(sub, policyDocument);
    }

}
