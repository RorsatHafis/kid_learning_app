package com.platform.payment.web;

import com.platform.payment.entity.PaymentProviderType;
import com.platform.payment.service.PaymentService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Where ABA PayWay notifies us after a payment (public: PayWay has no session). The notice is only a hint
 * naming our order: PaymentService settles it from PayWay's own answer to our signed check, so a forged notice
 * can at most make us ask. Always answers 200 with no detail, so it tells a caller nothing about our orders.
 */
@RestController
public class PaymentCallbackController {

    private final PaymentService payments;

    public PaymentCallbackController(PaymentService payments) {

        this.payments = payments;

    }

    @PostMapping(value = "/api/v1/payments/callbacks/aba-payway", consumes = {MediaType.APPLICATION_FORM_URLENCODED_VALUE,
            MediaType.MULTIPART_FORM_DATA_VALUE})
    public ResponseEntity<Void> abaPaywayForm(@RequestParam Map<String, String> params) {

        payments.providerNotice(PaymentProviderType.ABA_PAYWAY, params.get("tran_id"));
        return ResponseEntity.ok().build();

    }

    @PostMapping(value = "/api/v1/payments/callbacks/aba-payway", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Void> abaPaywayJson(@RequestBody(required = false) Map<String, Object> body) {

        Object reference = body == null ? null : body.get("tran_id");
        payments.providerNotice(PaymentProviderType.ABA_PAYWAY, reference == null ? null : String.valueOf(reference));
        return ResponseEntity.ok().build();

    }

}
