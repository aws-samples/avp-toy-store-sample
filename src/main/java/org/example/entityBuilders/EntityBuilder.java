package org.example.entityBuilders;

import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPEvent;
import org.apache.commons.lang3.tuple.Pair;
import software.amazon.awssdk.services.verifiedpermissions.model.EntityIdentifier;
import software.amazon.awssdk.services.verifiedpermissions.model.EntityItem;

import java.util.List;

public interface EntityBuilder {
    public Pair<EntityIdentifier, List<EntityItem>> getEntities(APIGatewayV2HTTPEvent event);

    public Pair<EntityIdentifier, List<EntityItem>> getEntities(APIGatewayV2HTTPEvent event, List<EntityItem>  masterEntities);
}
