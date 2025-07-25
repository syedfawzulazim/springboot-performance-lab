package com.sfazim.notification.config;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class OrderCreatedMessage {
    private String orderNumber;
    private String email;
    private String firstName;
    private String lastName;

}
