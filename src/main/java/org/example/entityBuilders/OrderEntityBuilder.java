package org.example.entityBuilders;

import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPEvent;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.example.config.EntityTypesConstants;
import org.example.datastore.MockLocalDataStore;
import org.example.entities.Order;
import software.amazon.awssdk.services.verifiedpermissions.model.AttributeValue;
import software.amazon.awssdk.services.verifiedpermissions.model.EntityIdentifier;
import software.amazon.awssdk.services.verifiedpermissions.model.EntityItem;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class OrderEntityBuilder implements EntityBuilder {

    private static final EntityItem ORDER_PARENT_ENTITY = EntityItem.builder().identifier(
            EntityIdentifier.builder().
                    entityType("avp::sample::toy::store::AllOrders").
                    entityId("all-orders").build()
    ).build();

    private final MockLocalDataStore mockLocalDataStore = new MockLocalDataStore();

    @Override
    public Pair<EntityIdentifier, List<EntityItem>> getEntities(APIGatewayV2HTTPEvent event) {
        return getEntities(event, new ArrayList());
    }

    @Override
    public Pair<EntityIdentifier, List<EntityItem>> getEntities(APIGatewayV2HTTPEvent event, List<EntityItem> masterEntities) {
        String orderId = event.getPathParameters().get("order-id");
        Order order = mockLocalDataStore.getOrder(orderId);
        if (order == null) {
            throw new IllegalStateException("Order with id " + orderId + " does not exist");
        }
        return getResourceEntities(masterEntities, order);
    }
    public Pair<EntityIdentifier, List<EntityItem>> getResourceEntities(
            List<EntityItem> masterEntities, Order order) {

        String orderId = order.getOrderId();

        EntityIdentifier orderEntityIdentifier = EntityIdentifier.builder().
                entityType(EntityTypesConstants.ORDER_ENTITY_TYPE).
                entityId(orderId).
                build();

        if (masterEntities.stream().anyMatch(
                (entityItem)-> masterEntities.contains(orderEntityIdentifier)
        )) {
            System.out.println("Order entity with id " + orderId + "already exists");
            return new ImmutablePair<>(orderEntityIdentifier, masterEntities);
        }
        if (!masterEntities.stream().anyMatch((entityItem)-> masterEntities.contains(ORDER_PARENT_ENTITY))) {
            masterEntities.add(ORDER_PARENT_ENTITY);
        }

        Pair<EntityIdentifier, List<EntityItem>> storeEntities =
                new StoreEntityBuilder().
                        getResourceEntities(order.getStoreId(), masterEntities);

        EntityItem.Builder orderEntityBuilder = EntityItem.builder();

        orderEntityBuilder.identifier(orderEntityIdentifier);

        if(!storeEntities.getLeft().entityId().equals(order.getStoreId())) {
            throw new IllegalStateException("Order does not belong to the store");
        }

        orderEntityBuilder.attributes(
                Map.of("productName",  AttributeValue.builder().string(order.getProductName()).build(),
                        "department",  AttributeValue.builder().string(order.getDepartment()).build())
        );

        orderEntityBuilder.parents(List.of(storeEntities.getLeft(), ORDER_PARENT_ENTITY.identifier()));
        masterEntities.add(orderEntityBuilder.build());
        return ImmutablePair.of(orderEntityIdentifier, masterEntities);
    }

}
