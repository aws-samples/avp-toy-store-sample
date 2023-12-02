package org.example.entityBuilders;

public class ResourceEntityBuilderFactory {


    public EntityBuilder getResourceEntityBuilder(String httpPath) {
        String lowerCaseHttpPath = httpPath.toLowerCase();
        if (
                lowerCaseHttpPath.equals(("GET /store/{store-id}/order/{order-Id}").toLowerCase()) ||
                        lowerCaseHttpPath.equals(("DELETE /store/{store-id}/order/{order-Id}").toLowerCase()) ||
                        lowerCaseHttpPath.equals(("GET /store/{store-id}/order/{order-Id}/label").toLowerCase()) ||
                        lowerCaseHttpPath.equals(("GET /store/{store-id}/order/{order-Id}/receipt").toLowerCase()) ||
                        lowerCaseHttpPath.equals(("GET /store/{store-id}/order/{order-Id}/box_size").toLowerCase()) ||
                        lowerCaseHttpPath.equals(("GET /store/{store-id}/order/{order-Id}/permissions").toLowerCase())

        ) {
            return new OrderEntityBuilder();
        } else if (
                lowerCaseHttpPath.equals(("PUT /store/{store-id}/pack_associate/{employee-id}").toLowerCase()) ||
                        lowerCaseHttpPath.equals(("PUT /store/{store-id}/store_manager/{employee-id}").toLowerCase()) ||
                        lowerCaseHttpPath.equals(("GET /store/{store-id}/store_manager").toLowerCase()) ||
                        lowerCaseHttpPath.equals(("GET /store/{store-id}/pack_associate").toLowerCase()) ||
                        lowerCaseHttpPath.equals(("GET /store/{store-id}/orders").toLowerCase()) ||
                        lowerCaseHttpPath.equals(("GET /store/{store-id}/permissions").toLowerCase())
        ) {
            return new StoreEntityBuilder();
        } else
            throw new InternalError("Unknown HTTP Path, there is no resource builder found for the given path" + httpPath);
    }
}
