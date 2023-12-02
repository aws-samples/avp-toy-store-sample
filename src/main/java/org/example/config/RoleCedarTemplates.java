package org.example.config;

public class RoleCedarTemplates {
    public static final String STORE_MANAGER_ROLE_TEMPLATE_ID = System.getenv("storeManagerTemplateId");
    public static final String PACK_ASSOCIATE_ROLE_TEMPLATE_ID = System.getenv("packAssociateTemplateId");

    public static String getCedarTemplateIdFromHttpPath(String httpPath) {
        switch (httpPath.toLowerCase()) {
            case "put /store/{store-id}/store_manager/{employee-id}":
            case "get /store/{store-id}/store_manager":
                return STORE_MANAGER_ROLE_TEMPLATE_ID;
            case "put /store/{store-id}/pack_associate/{employee-id}":
            case "get /store/{store-id}/pack_associate":
                return PACK_ASSOCIATE_ROLE_TEMPLATE_ID;
            default:
                throw new InternalError("No role found for " + httpPath + " in RoleCedarTemplates");
        }
    }
}
