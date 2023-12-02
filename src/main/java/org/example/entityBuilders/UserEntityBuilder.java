package org.example.entityBuilders;

import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPEvent;
import com.auth0.jwt.interfaces.Claim;
import com.auth0.jwt.interfaces.DecodedJWT;
import org.apache.commons.lang3.tuple.Pair;
import org.example.config.EntityTypesConstants;
import org.example.util.CognitoJwtVerifier;
import software.amazon.awssdk.services.verifiedpermissions.model.AttributeValue;
import software.amazon.awssdk.services.verifiedpermissions.model.EntityIdentifier;
import software.amazon.awssdk.services.verifiedpermissions.model.EntityItem;
import com.google.gson.Gson;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import com.google.gson.GsonBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class UserEntityBuilder implements EntityBuilder {

    private final CognitoJwtVerifier cognitoJwtVerifier = new CognitoJwtVerifier();

    @Override
    public Pair<EntityIdentifier, List<EntityItem>> getEntities(APIGatewayV2HTTPEvent event) {
        return getEntities(event,new ArrayList());
    }

    private static final String USER_POOL_ID = System.getenv("userPoolId");

    @Override
    public Pair<EntityIdentifier, List<EntityItem>> getEntities(APIGatewayV2HTTPEvent event, List<EntityItem> masterEntities) {


        EntityItem.Builder userEntityBuilder = EntityItem.builder();

        DecodedJWT jwtToken = cognitoJwtVerifier.verifyToken(event.getHeaders().get("authorization"));




        userEntityBuilder.identifier(EntityIdentifier.builder().entityType(EntityTypesConstants.USER_ENTITY_TYPE).entityId(USER_POOL_ID + "|" + jwtToken.getSubject()).build());
        final Map<String, AttributeValue> userAttr = new HashMap<String, AttributeValue>();
        jwtToken.getClaims().entrySet().stream().forEach(
                (claim)-> {
                    String claimName = claim.getKey();
                    Claim claimValue = claim.getValue();

                    if(!claimValue.isNull()) {
                        if (claimValue.asString() != null) {
                            System.out.println("Claim name " + claimName + " value " + claimValue.asString());
                            userAttr.put(claimName, AttributeValue.builder().string(claimValue.asString()).build());
                        } else if (claimValue.asBoolean() !=null) {
                            userAttr.put(claimName, AttributeValue.builder().booleanValue(claimValue.asBoolean()).build());

                        } else if (claimValue.asLong() != null) {
                            userAttr.put(claimName, AttributeValue.builder().longValue(claimValue.asLong()).build());
                        }
                    }
                });
        userEntityBuilder.attributes(userAttr);
        EntityItem userEntity = userEntityBuilder.build();
        masterEntities.add(userEntity);

        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        System.out.println("user entity: " + gson.toJson(userEntity));


        return Pair.of(userEntity.identifier(), masterEntities);
    }



}
