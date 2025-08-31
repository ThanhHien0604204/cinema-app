package com.ntth.spring_boot_heroku_cinema_app.Config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "zalopay")
@Getter
@Setter
public class ZpConfig {
    private int appId;
    private String key1;
    private String key2;
    private String endpointCreate;
    private String endpointRefund;
    private String endpointGateway; // optional (order_url host)
    private String callbackIpn;

    public int getAppId() { return appId; }
    public void setAppId(int appId) { this.appId = appId; }
    public String getKey1() { return key1; }
    public void setKey1(String key1) { this.key1 = key1; }

    public String getKey2() {
        return key2;
    }

    public void setKey2(String key2) {
        this.key2 = key2;
    }

    public String getEndpointCreate() {
        return endpointCreate;
    }

    public void setEndpointCreate(String endpointCreate) {
        this.endpointCreate = endpointCreate;
    }

    public String getEndpointRefund() {
        return endpointRefund;
    }

    public void setEndpointRefund(String endpointRefund) {
        this.endpointRefund = endpointRefund;
    }

    public String getEndpointGateway() {
        return endpointGateway;
    }

    public void setEndpointGateway(String endpointGateway) {
        this.endpointGateway = endpointGateway;
    }

    public String getCallbackIpn() {
        return callbackIpn;
    }

    public void setCallbackIpn(String callbackIpn) {
        this.callbackIpn = callbackIpn;
    }
}

