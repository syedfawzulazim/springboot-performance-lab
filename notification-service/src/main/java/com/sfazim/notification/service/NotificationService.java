package com.sfazim.notification.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sfazim.notification.config.OrderCreatedMessage;
import io.awspring.cloud.sqs.annotation.SqsListener;
import io.awspring.cloud.sqs.listener.acknowledgement.Acknowledgement;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.mail.javamail.MimeMessagePreparator;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

    private final JavaMailSender javaMailSender;

    @SqsListener(value ="${queue.name}")
    public void listen(Message<String> message) throws JsonProcessingException {
        String messageJson = message.getPayload();

        ObjectMapper mapper = new ObjectMapper();
        OrderCreatedMessage orderCreatedMessage = mapper.readValue(messageJson, OrderCreatedMessage.class);

        log.info("Got Message from order-crated with id: {}", orderCreatedMessage.getOrderNumber());
        System.out.println("Message: " +messageJson);

        //preparing email
        MimeMessagePreparator messagePreparator = mimeMessage -> {
            MimeMessageHelper messageHelper = new MimeMessageHelper(mimeMessage);
            messageHelper.setFrom("springshop@email.com");
            messageHelper.setTo(orderCreatedMessage.getEmail());
            messageHelper.setSubject(String.format("Your Order with OrderNumber %s is placed successfully", orderCreatedMessage.getOrderNumber()));
            messageHelper.setText(String.format("""
                            Hi %s %s,

                            Your order with order number %s is now placed successfully.

                            Best Regards
                            Spring Shop
                            """,
                    orderCreatedMessage.getFirstName(),
                    orderCreatedMessage.getLastName(),
                    orderCreatedMessage.getOrderNumber()));
        };

        try {
            javaMailSender.send(messagePreparator);
            log.info("Order Notification email sent!!");
            Acknowledgement.acknowledge(message);
        } catch (MailException e) {
            log.error("Exception occurred when sending mail", e);
            throw new RuntimeException("Exception occurred when sending mail to springshop@email.com", e);
        }
    }
}