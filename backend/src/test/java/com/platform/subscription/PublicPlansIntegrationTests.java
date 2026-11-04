package com.platform.subscription;

import com.platform.TestcontainersConfiguration;
import com.platform.subscription.entity.BillingInterval;
import com.platform.subscription.entity.SubscriptionPlan;
import com.platform.subscription.repository.SubscriptionPlanRepository;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * P-1 / LAND-1: the landing page's plans come from the plan records, read by anyone - an active plan people can
 * choose shows with its price; a draft, one only an administrator assigns, or a retired one doesn't.
 */
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Tag("integration")
class PublicPlansIntegrationTests {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Value("${local.server.port}") private int port;
    @Autowired private SubscriptionPlanRepository plans;

    private final HttpClient http = HttpClient.newHttpClient();

    @Test
    void theLandingShowsWhatThePlanRecordsOffer() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        SubscriptionPlan offered = SubscriptionPlan.draft("PUB_" + suffix, "Family Plus " + suffix, "More children");
        offered.revise("Family Plus " + suffix, "More children", true, 500L, "USD", BillingInterval.MONTH);
        offered.activate();
        plans.saveAndFlush(offered);
        SubscriptionPlan draft = SubscriptionPlan.draft("DRF_" + suffix, "Draft " + suffix, null);
        draft.revise("Draft " + suffix, null, true, 900L, "USD", BillingInterval.MONTH);
        plans.saveAndFlush(draft);
        SubscriptionPlan assignedOnly = SubscriptionPlan.draft("ADM_" + suffix, "Assigned " + suffix, null);
        assignedOnly.activate();
        plans.saveAndFlush(assignedOnly);

        JsonNode shown = publicPlans();
        JsonNode plus = find(shown, "PUB_" + suffix);
        assertThat(plus).as("an active plan people choose themselves").isNotNull();
        assertThat(plus.get("amountMinor").asLong()).isEqualTo(500);
        assertThat(plus.get("currency").asString()).isEqualTo("USD");
        assertThat(plus.get("billingInterval").asString()).isEqualTo("MONTH");
        assertThat(plus.get("audience").asString()).isEqualTo("FAMILY");
        assertThat(plus.has("status")).as("no internal fields").isFalse();
        assertThat(find(shown, "DRF_" + suffix)).as("a draft").isNull();
        assertThat(find(shown, "ADM_" + suffix)).as("only an administrator assigns it").isNull();
        assertThat(shown.toString()).as("the default family plan is shown").contains("\"isDefault\":true");

        offered.retire();
        plans.saveAndFlush(offered);
        assertThat(find(publicPlans(), "PUB_" + suffix)).as("retired: no longer offered").isNull();
    }

    private JsonNode publicPlans() throws Exception {
        HttpResponse<String> response = http.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1/site/plans")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).as("read by anyone").isEqualTo(200);
        return JSON.readTree(response.body());
    }

    private static JsonNode find(JsonNode list, String code) {
        for (JsonNode plan : list) {
            if (code.equals(plan.get("code").asString())) return plan;
        }
        return null;
    }
}
