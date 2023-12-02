package org.example.config;

import com.amazonaws.util.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.example.authorizerpolicies.AuthPolicy;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class HttpPathToCedarActionMap {

    public static Map<String, Pair<String, AuthPolicy.HttpMethod>> actionToHttpRoute = new HashMap<>();
    static {
        actionToHttpRoute.put("ListOrders", Pair.of("/store/{store-id}/orders", AuthPolicy.HttpMethod.GET));
        actionToHttpRoute.put("GetOrder", Pair.of("/store/{store-id}/order/{order-id}", AuthPolicy.HttpMethod.GET));
        actionToHttpRoute.put("GetOrderLabel", Pair.of("/store/{store-id}/order/{order-id}/label", AuthPolicy.HttpMethod.GET));
        actionToHttpRoute.put("GetOrderReceipt", Pair.of("/store/{store-id}/order/{order-id}/receipt", AuthPolicy.HttpMethod.GET));
        actionToHttpRoute.put("ReRouteOrder", Pair.of("/store/{store-id}/order/{order-id}/re_route", AuthPolicy.HttpMethod.POST));
        actionToHttpRoute.put("SetOrderShipped", Pair.of("/store/{store-id}/order/{order-id}/status/shipped", AuthPolicy.HttpMethod.PUT));
        actionToHttpRoute.put("GetOrderBoxSize", Pair.of("/store/{store-id}/order/{order-id}/box_size", AuthPolicy.HttpMethod.GET));
        actionToHttpRoute.put("DeleteOrder", Pair.of("/store/{store-id}/order/{order-id}", AuthPolicy.HttpMethod.DELETE));
        actionToHttpRoute.put("AddPackAssociate", Pair.of("/store/{store-id}/pack_associate/{employee-id}", AuthPolicy.HttpMethod.PUT));
        actionToHttpRoute.put("AddStoreManager", Pair.of("/store/{store-id}/store_manager/{employee-id}", AuthPolicy.HttpMethod.PUT));
        actionToHttpRoute.put("ListPackAssociates", Pair.of("/store/{store-id}/pack_associate", AuthPolicy.HttpMethod.GET));
        actionToHttpRoute.put("ListStoreManagers", Pair.of("/store/{store-id}/store_manager", AuthPolicy.HttpMethod.GET));
        actionToHttpRoute.put("ListStorePermissions", Pair.of("/store/{store-id}/permissions", AuthPolicy.HttpMethod.GET));
        actionToHttpRoute.put("ListOrderPermissions", Pair.of("/store/{store-id}/order/{order-id}/permissions", AuthPolicy.HttpMethod.GET));
    }

    public static Map<String, Map<String, String>> wildcards = Map.of(
            "AddPackAssociate", Map.of("{employee-id}","*"),
            "AddStoreManager", Map.of("{employee-id}","*")
    );

    public static Map<String, List<String>> actionForRoute;

    static {
        List<String> storeLevelActions = List.of("ListOrders", "ListPackAssociates", "ListStoreManagers", "AddPackAssociate", "AddStoreManager");
        List<String> orderLevelActions = List.of("GetOrder", "GetOrderLabel", "GetOrderReceipt", "GetOrderBoxSize", "DeleteOrder", "ReRouteOrder", "SetOrderShipped");
        actionForRoute = new HashMap<>();
        actionForRoute.put(StringUtils.lowerCase("GET /store/{store-id}/order/{order-id}/receipt"), orderLevelActions);
        actionForRoute.put(StringUtils.lowerCase("GET /store/{store-id}/order/{order-id}/box_size"), orderLevelActions);
        actionForRoute.put(StringUtils.lowerCase("GET /store/{store-id}/order/{order-id}/label"), orderLevelActions);
        actionForRoute.put(StringUtils.lowerCase("DELETE /store/{store-id}/order/{order-id}"), orderLevelActions);
        actionForRoute.put(StringUtils.lowerCase("GET /store/{store-id}/order/{order-id}"), orderLevelActions);
        actionForRoute.put(StringUtils.lowerCase("GET /store/{store-id}/order/{order-id}/permissions"), orderLevelActions);
        actionForRoute.put(StringUtils.lowerCase("GET /store/{store-id}/order/{order-id}/permissions"), orderLevelActions);
        actionForRoute.put(StringUtils.lowerCase("GET /store/{store-id}/orders"), storeLevelActions);
        actionForRoute.put(StringUtils.lowerCase("GET /store/{store-id}/pack_associate"), storeLevelActions);
        actionForRoute.put(StringUtils.lowerCase("GET /store/{store-id}/store_manager"), storeLevelActions);
        actionForRoute.put(StringUtils.lowerCase("GET /store/{store-id}/permissions"), storeLevelActions);
        actionForRoute.put(StringUtils.lowerCase("PUT /store/{store-id}/store_manager/{employee-id}"), storeLevelActions);
        actionForRoute.put(StringUtils.lowerCase("PUT /store/{store-id}/pack_associate/{employee-id}"), storeLevelActions);

    }

    public static Pair<String, AuthPolicy.HttpMethod> getHttpPathForAction(String action, Map<String, String> parameters) {
        String path = actionToHttpRoute.get(action).getLeft();
        //System.out.println("Route for [" + action + "] is " + path);
        if (path == null)
            return null;
        String route = path;
        for (String parameter : parameters.keySet()) {
             route = route.replace("{" + parameter + "}", parameters.get(parameter));
        }

        for (Map.Entry<String, String> wildcard : wildcards.getOrDefault(action, Collections.emptyMap()).entrySet()) {
            route = route.replace(wildcard.getKey(), wildcard.getValue());
        }
        //System.out.println("Path for [" + action + "] is " + route);
        AuthPolicy.HttpMethod httpMethod = actionToHttpRoute.get(action).getRight();
        return Pair.of(route, httpMethod);
    }

    public static List<String> getRelatedCedarActions(String httpRouteKey) {
        List<String> cedarActions = actionForRoute.get(StringUtils.lowerCase(httpRouteKey));
        System.out.println("Action for [" + httpRouteKey +"] is "+cedarActions);
        if (cedarActions == null)
            throw new RuntimeException("No CedarAction found for [" + httpRouteKey +"]");
        return cedarActions;
    }
}
