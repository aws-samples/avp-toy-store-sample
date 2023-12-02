package org.example.entities;

public class Order {
    private String orderId;
    private String storeId;
    private String productName;
    private String department;
    private String customerId;

    public String getStoreId() {
        return storeId;
    }

    public void setStoreId(String storeId) {
        this.storeId = storeId;
    }

    public String getProductName() {
        return productName;
    }

    public void setProductName(String productName) {
        this.productName = productName;
    }

    public String getDepartment() {
        return department;
    }

    public void setDepartment(String department) {
        this.department = department;
    }

    public String getCustomerId() {
        return customerId;
    }

    public void setCustomerId(String customerId) {
        this.customerId = customerId;
    }

    @java.lang.Override
    public java.lang.String toString() {
        return "Order{" +
                "orderId='" + orderId + '\'' +
                ", storeId='" + storeId + '\'' +
                ", productName='" + productName + '\'' +
                ", department='" + department + '\'' +
                ", customerId='" + customerId + '\'' +
                '}';
    }

    public String getOrderId() {
        return orderId;
    }

    public void setOrderId(String orderId) {
        this.orderId = orderId;
    }
}
