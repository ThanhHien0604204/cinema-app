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

}

