package org.example.datastore;

import org.example.entities.Order;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MockLocalDataStore {

    private final Map<String, Order> orders = new HashMap();
    private final Map<String, List<Order>> ordersByStore = new HashMap();
    private static final int NUMBER_OF_ORDERS_PER_STORE = 20;
    private static final int NUMBER_OF_STORES = 5;
    private static final String[] DEPARTMENTS = new String[]{"Board Game", "Soft Toy"};

    public MockLocalDataStore() {
        int orderIdCounter=1;
        for (int storeCounter = 1; storeCounter<= NUMBER_OF_STORES; storeCounter++) {
            for (int i = 1; i<= NUMBER_OF_ORDERS_PER_STORE; i++) {
                Order order = new Order();
                order.setOrderId(String.valueOf(orderIdCounter));
                String storeId = "toy store " + storeCounter;
                order.setStoreId(storeId);
                order.setProductName(toyName[i%(toyName.length-1)]);
                order.setDepartment(DEPARTMENTS[(orderIdCounter % (DEPARTMENTS.length))]);
                orders.put(order.getOrderId(), order);
                List<Order> ordersForSpecificStore = ordersByStore.get(storeId);
                if (ordersForSpecificStore == null) {
                    ordersForSpecificStore = new java.util.ArrayList<Order>();
                    ordersByStore.put(storeId, ordersForSpecificStore);
                }
                ordersForSpecificStore.add(order);
                orderIdCounter++;
            }
        }
        System.out.println("MockLocalDataStore initialized with orders" + ordersByStore);
    }

    public List<Order> getOrders(String storeId) {
        return ordersByStore.getOrDefault(storeId, new ArrayList<>());
    }
    public Order getOrder(String orderId) {
        return orders.get(orderId);
    }
    private String[] toyName = {"Pictionary","Spider Man","Barbie","Catan","Wendy","Snakes and Ladders","Dinosaur","Ticket to Ride","Pikachu","Monopoly","Optimus Prime"};
}
