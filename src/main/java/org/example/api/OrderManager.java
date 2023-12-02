package org.example.api;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPResponse;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.example.config.EntityTypesConstants;
import org.example.datastore.MockLocalDataStore;
import org.example.entities.Order;
import org.example.entityBuilders.OrderEntityBuilder;
import org.example.entityBuilders.UserEntityBuilder;
import org.example.util.CognitoJwtVerifier;
import org.json.simple.JSONObject;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.verifiedpermissions.VerifiedPermissionsClient;
import software.amazon.awssdk.services.verifiedpermissions.model.*;
import com.amazonaws.xray.AWSXRay;
import com.amazonaws.xray.entities.Subsegment;


public class OrderManager {
    public static final String AUTHORIZATION_HEADER_NAME = "authorization";
    private static final String POLICY_STORE_ID = System.getenv("policyStoreId");
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private final OrderEntityBuilder orderResourceEntityBuilder = new OrderEntityBuilder();
    private final MockLocalDataStore mockLocalDataStore = new MockLocalDataStore();

    private final VerifiedPermissionsClient avpClient = VerifiedPermissionsClient.builder().build();

    private final CognitoJwtVerifier cognitoJwtVerifier = new CognitoJwtVerifier();
    private final UserEntityBuilder userEntityBuilder = new UserEntityBuilder();

    public APIGatewayV2HTTPResponse getOrder(APIGatewayV2HTTPEvent event, Context context) {
        System.out.println("event: " + event.toString());
        JSONObject response = new JSONObject();
        try {
            String orderId = event.getPathParameters().get("order-id");
            Order order = mockLocalDataStore.getOrder(orderId);
            System.out.println("loaded order from data store: " + gson.toJson(order));
            return ok(gson.toJson(order));
        } catch (Exception exc) {
            return error(response, exc);
        }
    }

    public APIGatewayV2HTTPResponse listOrders(APIGatewayV2HTTPEvent event, Context context) {
        DecodedJWT token = cognitoJwtVerifier.verifyToken(event.getHeaders().get("authorization"));
        System.out.println("JWT token valid, sub is : " + token.getSubject() + "and username is : " + token.getClaim("cognito:username"));

        System.out.println("event: " + event.toString());
        JSONObject response = new JSONObject();
        try {
            //Custom environment variables created in JavaCdkStack
            JSONObject envObject = new JSONObject();
            String storeId = event.getPathParameters().get("store-id");
            System.out.println("Trying to get orders for store:" + storeId);
            List<Order> ordersForStore = mockLocalDataStore.getOrders(storeId);
            System.out.println("Order from data store: " + ordersForStore);

            List<EntityItem> entities = new ArrayList();
            EntityIdentifier userEntity = userEntityBuilder.getEntities(event, entities).getLeft();

            for (Order order : ordersForStore) {
                orderResourceEntityBuilder.getResourceEntities(entities, order);
            }
            //  System.out.println("Order entities: " + gson.toJson(entities).replaceAll("[\\t ]", ""));
            List<Order> authorizedOrders = new ArrayList<>();

            // The entity definition contains entities across all orders
            EntitiesDefinition entitiesDefinition = EntitiesDefinition.builder().
                    entityList(entities).
                    build();

            /*
                Optimized: Make a single batch authorization request across all orders

                Not Optimized: Make an authorization request for every order in the list
             */
            boolean optimized = Boolean.parseBoolean(event.getQueryStringParameters().getOrDefault("optimized", "false"));
            if (optimized) {

                System.out.println("Calling verified permissions Bulk authorizations for orders : " + ordersForStore);
                Subsegment subsegment = AWSXRay.beginSubsegment("IsAuthorizedBatch");
                try {
                    Collection<BatchIsAuthorizedInputItem> authorizationRequests = ordersForStore.stream().map(order ->
                            BatchIsAuthorizedInputItem.builder()
                                .principal(userEntity)
                                .action(ActionIdentifier.builder().actionType(
                                                EntityTypesConstants.ACTION_ENTITY_TYPE).
                                        actionId("GetOrder").
                                        build())
                                .resource(EntityIdentifier.builder().
                                        entityType(EntityTypesConstants.ORDER_ENTITY_TYPE).
                                        entityId(order.getOrderId()).
                                        build())
                                .build()
                            ).collect(Collectors.toList());
                    BatchIsAuthorizedResponse batchIsAuthorizedResponse =
                            avpClient.batchIsAuthorized(BatchIsAuthorizedRequest.builder().
                            entities(entitiesDefinition).
                            policyStoreId(POLICY_STORE_ID).
                            requests(authorizationRequests).
                            build());

                    for (int i=0;i<ordersForStore.size();i++) {
                        // BatchIsAuthorized maintains the order of the response
                        if(batchIsAuthorizedResponse.results().get(i).decision().equals(Decision.ALLOW)) {
                            authorizedOrders.add(ordersForStore.get(i));
                        }
                    }
                    System.out.println("Authorized orders: " + authorizedOrders);
                } catch (Exception e) {
                    subsegment.addException(e);
                    throw e;
                } finally {
                    AWSXRay.endSubsegment();
                }

            } else {
                for (Order order : ordersForStore) {
                    System.out.println("Calling verified permissions for order : " + order);
                    Subsegment subsegment = AWSXRay.beginSubsegment("IsAuthorized");
                    try {
                        subsegment.putAnnotation("Lambda Function", "ListOrders");
                        IsAuthorizedResponse authorizationResponse =
                                avpClient.isAuthorized(IsAuthorizedRequest.builder()
                                        .policyStoreId(POLICY_STORE_ID)
                                        .principal(userEntity)
                                        .action(ActionIdentifier.builder().actionType(
                                                        EntityTypesConstants.ACTION_ENTITY_TYPE).
                                                actionId("GetOrder").
                                                build())
                                        .resource(EntityIdentifier.builder().
                                                entityType(EntityTypesConstants.ORDER_ENTITY_TYPE).
                                                entityId(order.getOrderId()).
                                                build())
                                        .entities(entitiesDefinition)
                                        .build());

                        if (authorizationResponse.decision().equals(Decision.ALLOW)) {
                            authorizedOrders.add(order);
                        }
                        System.out.println("Authorized orders: " + authorizedOrders);
                    } catch (Exception e) {
                        subsegment.addException(e);
                        throw e;
                    } finally {
                        AWSXRay.endSubsegment();
                    }
                }
            }
            System.out.print("Got all order for store. " +
                    "This is the list: " + gson.toJson(authorizedOrders));

            return ok(gson.toJson(authorizedOrders));
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
}


