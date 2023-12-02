package org.example.entityBuilders;

import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPEvent;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.example.config.EntityTypesConstants;
import software.amazon.awssdk.services.verifiedpermissions.model.EntityIdentifier;
import software.amazon.awssdk.services.verifiedpermissions.model.EntityItem;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class StoreEntityBuilder implements EntityBuilder {

    private static final EntityItem STORE_PARENT_ENTITY = EntityItem.builder().identifier(
            EntityIdentifier.builder().
                    entityType("avp::sample::toy::store::AllStores").
                    entityId("all-stores").build()
    ).build();
    private EntityIdentifier storeEntityIdentifier;

    @Override
    public Pair<EntityIdentifier, List<EntityItem>> getEntities(APIGatewayV2HTTPEvent event) {
        return getEntities(event,new ArrayList());
    }

    @Override
    public Pair<EntityIdentifier, List<EntityItem>> getEntities(APIGatewayV2HTTPEvent event, List<EntityItem> masterEntities) {
        return getResourceEntities(event.getPathParameters().get("store-id"), masterEntities);
    }

    public Pair<EntityIdentifier, List<EntityItem>> getResourceEntities(String storeId, List<EntityItem> masterEntities) {

        storeEntityIdentifier = EntityIdentifier.builder().
                entityType(EntityTypesConstants.STORE_ENTITY_TYPE).
                entityId(storeId).build();
        if (masterEntities.stream().anyMatch(
                (EntityItem entityItem)-> entityItem.identifier().equals(storeEntityIdentifier)
        )) {
            System.out.print("Store entity with id " + storeId + "already exists");
            return new ImmutablePair<>(storeEntityIdentifier, masterEntities);
        }


        if (!masterEntities.contains(STORE_PARENT_ENTITY)) {
            masterEntities.add(STORE_PARENT_ENTITY);
        }
        EntityItem storeEntity = EntityItem.builder().
                identifier(storeEntityIdentifier).
                parents(Collections.singleton(STORE_PARENT_ENTITY.identifier())).
                build();

        masterEntities.add(storeEntity);
        return new ImmutablePair<>(storeEntity.identifier(), masterEntities);
    }
}
