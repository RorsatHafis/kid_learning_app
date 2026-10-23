package com.platform.common.web;

import com.platform.identity.UiLanguage;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The words the server says to people - error details and field messages - in the language of the
 * request (its Accept-Language: en or km). Messages are written in English where they are thrown, so
 * logs and audit records stay in one language; this translates them once, at the response boundary
 * (GlobalExceptionHandler). A message with no Khmer here is never shown in English to a Khmer
 * interface: it falls back to a plain Khmer sentence for its status. {@code ServerTextCoverageTests}
 * fails when a message thrown in the code has no entry.
 *
 * <p>The Khmer was written for the product, not by a native-speaking editor: it needs that review
 * before launch (recorded in the progress document).
 */
public final class ServerText {

    /** The details the security filters write themselves (no controller, so no GlobalExceptionHandler). */
    public static final String FILTER_UNAUTHENTICATED = "Authentication is required";
    public static final String FILTER_TOKEN_REJECTED = "The access token is invalid or expired";
    public static final String FILTER_RATE_LIMITED = "Too many requests from this network. Please wait and try again.";

    private ServerText() {
    }

    /** {@code english} as the current request's language would say it. */
    public static String forCurrentRequest(String english, int status) {
        return translate(english, UiLanguage.ofCurrentRequest(), status);
    }

    /**
     * {@code english} in {@code request}'s language, for filters that answer before Spring MVC has
     * resolved the request's locale (the same resolution: Accept-Language narrowed to en/km).
     */
    public static String forRequest(jakarta.servlet.http.HttpServletRequest request, String english, int status) {
        boolean khmer = UiLanguage.KHMER.equals(UiLocaleConfig.RESOLVER.resolveLocale(request).getLanguage());
        return translate(english, khmer ? UiLanguage.KHMER : UiLanguage.ENGLISH, status);
    }

    public static String translate(String english, String language, int status) {
        if (english == null || !UiLanguage.isKhmer(language)) {
            return english;
        }
        String khmer = khmer(english);
        return khmer != null ? khmer : fallback(status);
    }

    /** The Khmer for an English message, or null when there is none. */
    static String khmer(String english) {
        String exact = KHMER.get(english);
        if (exact != null) {
            return exact;
        }
        for (Rule rule : PATTERNS) {
            Matcher matcher = rule.pattern().matcher(english);
            if (matcher.matches()) {
                return rule.khmer().apply(matcher);
            }
        }
        return null;
    }

    static String fallback(int status) {
        return switch (status) {
            case 400 -> "សំណើនេះមិនត្រឹមត្រូវទេ។ សូមពិនិត្យ ហើយព្យាយាមម្ដងទៀត។";
            case 401 -> "សូមចូលគណនីម្ដងទៀត។";
            case 403 -> "អ្នកមិនមានសិទ្ធិចូលប្រើទំព័រនេះទេ។";
            case 404 -> "រកមិនឃើញទេ។ វាប្រហែលជាត្រូវបានលុប ឬមិនអាចប្រើបានសម្រាប់អ្នក។";
            case 409 -> "មិនអាចធ្វើការនេះបានទេ ក្នុងស្ថានភាពបច្ចុប្បន្ន។ សូមផ្ទុកទំព័រឡើងវិញ ហើយព្យាយាមម្ដងទៀត។";
            case 413 -> "ឯកសារនេះធំពេក។";
            case 422 -> "មិនអាចទទួលយកការនេះបានទេ។ សូមពិនិត្យ ហើយព្យាយាមម្ដងទៀត។";
            case 423 -> "គណនីនេះត្រូវបានចាក់សោបណ្ដោះអាសន្ន។ សូមព្យាយាមម្ដងទៀតនៅពេលក្រោយ។";
            case 429 -> "សូមរង់ចាំបន្តិច ហើយព្យាយាមម្ដងទៀត។";
            default -> "មានបញ្ហាមួយបានកើតឡើង។ សូមព្យាយាមម្ដងទៀត។";
        };
    }

    private record Rule(Pattern pattern, Function<Matcher, String> khmer) {
    }

    private static Rule rule(String regex, Function<Matcher, String> khmer) {
        return new Rule(Pattern.compile(regex), khmer);
    }

    /** Messages built with values: the values are kept as they are (names, numbers). */
    private static final List<Rule> PATTERNS = List.of(
            rule("Password must be at least (\\d+) characters long",
                    m -> "ពាក្យសម្ងាត់ត្រូវមានយ៉ាងហោចណាស់ " + m.group(1) + " តួអក្សរ"),
            rule("Password is too long: use at most (\\d+) letters, digits or symbols \\(fewer if it has letters beyond A-Z, such as Khmer\\)",
                    m -> "ពាក្យសម្ងាត់វែងពេក៖ ប្រើអក្សរ លេខ ឬសញ្ញា មិនលើសពី " + m.group(1)
                            + " (តិចជាងនេះ បើមានអក្សរក្រៅពី A-Z ដូចជាអក្សរខ្មែរ)"),
            rule("Your family's plan includes up to (\\d+) children\\.",
                    m -> "កញ្ចប់របស់គ្រួសារអ្នករួមបញ្ចូលកូនរហូតដល់ " + m.group(1) + " នាក់។"),
            rule("Your school's plan includes up to (\\d+) children\\.",
                    m -> "កញ្ចប់របស់សាលារបស់អ្នករួមបញ្ចូលកុមាររហូតដល់ " + m.group(1) + " នាក់។"),
            rule("Your school's plan includes up to (\\d+) teachers\\.",
                    m -> "កញ្ចប់របស់សាលារបស់អ្នករួមបញ្ចូលគ្រូរហូតដល់ " + m.group(1) + " នាក់។"),
            rule("Your school's plan includes up to (\\d+) classes in use\\.",
                    m -> "កញ្ចប់របស់សាលារបស់អ្នករួមបញ្ចូលថ្នាក់ដែលកំពុងប្រើរហូតដល់ " + m.group(1) + "។"),
            rule("Your school's plan includes (\\d+) MB of pictures and recordings per teacher\\.",
                    m -> "កញ្ចប់របស់សាលារបស់អ្នករួមបញ្ចូលរូបភាព និងការថតសំឡេង " + m.group(1) + " MB សម្រាប់គ្រូម្នាក់ៗ។"),
            rule("Your storage is full: (\\S+) MB of (\\S+) MB is used, and this file needs (\\S+) MB\\. Remove media you no longer use, or ask for more space\\.",
                    m -> "កន្លែងផ្ទុករបស់អ្នកពេញហើយ៖ បានប្រើ " + m.group(1) + " MB ក្នុងចំណោម " + m.group(2) + " MB ហើយឯកសារនេះត្រូវការ "
                            + m.group(3) + " MB។ សូមលុបមេឌៀដែលអ្នកលែងប្រើ ឬស្នើសុំកន្លែងបន្ថែម។"),
            rule("Your family's plan includes up to (\\d+) journeys per child at a time\\.",
                    m -> "កញ្ចប់របស់គ្រួសារអ្នករួមបញ្ចូលផ្លូវសិក្សារហូតដល់ " + m.group(1) + " សម្រាប់កូនម្នាក់ៗក្នុងពេលតែមួយ។"),
            rule("(A picture|A recording) can be at most (\\d+) MB\\.",
                    m -> (m.group(1).equals("A picture") ? "រូបភាព" : "ការថតសំឡេង") + "មួយអាចមានទំហំយ៉ាងច្រើន " + m.group(2) + " MB។"),
            rule("A picture can be at most (\\d+) pixels on a side \\(this one is (\\d+) × (\\d+)\\)\\.",
                    m -> "រូបភាពមួយអាចមានទំហំយ៉ាងច្រើន " + m.group(1) + " ភីកសែលម្ខាង (រូបនេះមាន " + m.group(2) + " × "
                            + m.group(3) + ")។"),
            rule("(.+) assigns this path, so it stays while the class does\\. To stop it, end the school's access on this page\\.",
                    m -> m.group(1).replace(" at ", " នៅ ") + " បានកំណត់ផ្លូវសិក្សានេះ ដូច្នេះវានៅដដែល ដរាបណាថ្នាក់នៅតែកំណត់វា។ "
                            + "ដើម្បីបញ្ឈប់វា សូមបញ្ចប់សិទ្ធិចូលប្រើរបស់សាលានៅលើទំព័រនេះ។"),
            rule("A name needs 1 to (\\d+) characters", m -> "ឈ្មោះត្រូវមានពី 1 ដល់ " + m.group(1) + " តួអក្សរ"),
            rule("Choose between 1 and (\\d+) children", m -> "សូមជ្រើសរើសកុមារពី 1 ដល់ " + m.group(1) + " នាក់"),
            rule("A plan with the code (\\S+) already exists", m -> "កញ្ចប់ដែលមានកូដ " + m.group(1) + " មានរួចហើយ"),
            rule("Set every entitlement before activating \\(missing (\\S+)\\)",
                    m -> "សូមកំណត់សិទ្ធិទាំងអស់ មុនពេលបើកដំណើរការ (ខ្វះ " + m.group(1) + ")"),
            rule("\"(.*)\" isn't a currency code \\(use ISO 4217, such as USD or KHR\\)",
                    m -> "\"" + m.group(1) + "\" មិនមែនជាកូដរូបិយប័ណ្ណទេ (ប្រើ ISO 4217 ដូចជា USD ឬ KHR)"),
            rule("Only a draft plan can be (\\w+); this one is (\\w+)",
                    m -> "មានតែកញ្ចប់ព្រាងទេ ដែលអាចកែប្រែបែបនេះបាន។ កញ្ចប់នេះមានស្ថានភាព " + m.group(2)),
            rule("Only a draft or the current published version of a question can be added \\(question version \\S+ is \\w+\\)",
                    m -> "អាចបន្ថែមបានតែសេចក្ដីព្រាង ឬកំណែដែលបានផ្សព្វផ្សាយបច្ចុប្បន្នរបស់សំណួរប៉ុណ្ណោះ"),
            rule("An activity can hold at most (\\d+) questions", m -> "សកម្មភាពមួយអាចមានសំណួរយ៉ាងច្រើន " + m.group(1)),
            rule("Question version [0-9a-f-]+: (.*)", m -> {
                String inner = khmer(m.group(1));
                return inner != null ? inner : "សំណួរនេះមិនទាន់ពេញលេញទេ។";
            }),
            rule("size must be between (\\d+) and (\\d+)", m -> "ប្រវែងត្រូវនៅចន្លោះ " + m.group(1) + " និង " + m.group(2)),
            rule("must be greater than or equal to (-?\\d+)", m -> "ត្រូវតែធំជាង ឬស្មើ " + m.group(1)),
            rule("must be less than or equal to (-?\\d+)", m -> "ត្រូវតែតូចជាង ឬស្មើ " + m.group(1)),
            rule("must match \".*\"", m -> "ទម្រង់មិនត្រឹមត្រូវ"),
            rule("A request for operation '.*' with idempotency key '.*' is already being processed",
                    m -> "សំណើនេះកំពុងដំណើរការរួចហើយ។ សូមរង់ចាំបន្តិច។"),
            rule("Idempotency key '.*' for operation '.*' was already used for a different request",
                    m -> "សំណើនេះត្រូវបានផ្ញើរួចហើយ ដោយមានព័ត៌មានខុសគ្នា។ សូមផ្ទុកទំព័រឡើងវិញ ហើយព្យាយាមម្ដងទៀត។"),
            rule("Cannot complete attempt \\S+: (\\d+) of (\\d+) required questions have not been answered yet",
                    m -> "មិនទាន់អាចបញ្ចប់បានទេ៖ នៅសល់សំណួរ " + m.group(1) + " ក្នុងចំណោម " + m.group(2) + " មិនទាន់ឆ្លើយ")
    );

    /** Messages written as they are thrown, word for word. */
    private static final Map<String, String> KHMER = Map.ofEntries(
            // The fixed texts of GlobalExceptionHandler
            Map.entry(GlobalExceptionHandler.NOT_FOUND_DETAIL, "រកមិនឃើញទេ។ វាប្រហែលជាត្រូវបានលុប ឬមិនអាចប្រើបានសម្រាប់អ្នក។"),
            Map.entry("You do not have access to this resource", "អ្នកមិនមានសិទ្ធិចូលប្រើការនេះទេ"),
            Map.entry("Validation failed", "សូមពិនិត្យព័ត៌មានដែលបានបញ្ចូល"),
            Map.entry("An unexpected error occurred", "មានបញ្ហាដែលមិនបានរំពឹងទុកបានកើតឡើង"),
            Map.entry("The request conflicts with existing data", "សំណើនេះផ្ទុយនឹងទិន្នន័យដែលមានរួចហើយ"),
            Map.entry("A file can be at most 5 MB (a picture at most 2 MB).", "ឯកសារមួយអាចមានទំហំយ៉ាងច្រើន 5 MB (រូបភាពយ៉ាងច្រើន 2 MB)។"),
            Map.entry("This draft can't be published yet: fix the listed issues first.",
                    "សេចក្ដីព្រាងនេះមិនទាន់អាចផ្សព្វផ្សាយបានទេ៖ សូមកែបញ្ហាដែលបានរាយខាងក្រោមជាមុនសិន។"),
            // Written by the security filters, before a controller is reached
            Map.entry(FILTER_UNAUTHENTICATED, "សូមចូលគណនីជាមុនសិន"),
            Map.entry(FILTER_TOKEN_REJECTED, "វគ្គចូលរបស់អ្នកផុតកំណត់ហើយ។ សូមចូលម្ដងទៀត"),
            Map.entry(FILTER_RATE_LIMITED, "មានសំណើច្រើនពេកពីបណ្ដាញនេះ។ សូមរង់ចាំបន្តិច ហើយព្យាយាមម្ដងទៀត។"),
            Map.entry("This device can only open one child's learning space", "ឧបករណ៍នេះអាចបើកបានតែកន្លែងរៀនរបស់កុមារម្នាក់ប៉ុណ្ណោះ"),
            Map.entry("That code doesn't work. Ask a grown-up for a new one.", "លេខកូដនោះមិនដំណើរការទេ។ សូមសុំលេខកូដថ្មីពីមនុស្សធំ។"),
            Map.entry("This code has ended. Ask a grown-up for a new one.", "លេខកូដនេះបានបញ្ចប់ហើយ។ សូមសុំលេខកូដថ្មីពីមនុស្សធំ។"),
            Map.entry("No device is waiting with this code: make a new one", "គ្មានឧបករណ៍ណាកំពុងរង់ចាំជាមួយលេខកូដនេះទេ៖ សូមបង្កើតលេខកូដថ្មី"),
            Map.entry("This child already has as many devices as allowed: sign one out first",
                    "កុមារនេះមានឧបករណ៍ច្រើនតាមដែលអនុញ្ញាតហើយ៖ សូមចាកចេញពីឧបករណ៍មួយជាមុនសិន"),
            Map.entry("Could not make a code just now; try again", "មិនអាចបង្កើតលេខកូដនៅពេលនេះបានទេ សូមព្យាយាមម្ដងទៀត"),
            // Exception defaults
            Map.entry("Account is temporarily locked", "គណនីត្រូវបានចាក់សោបណ្ដោះអាសន្ន"),
            Map.entry("The current password is incorrect", "ពាក្យសម្ងាត់បច្ចុប្បន្នមិនត្រឹមត្រូវទេ"),
            Map.entry("An account already exists for this email address", "មានគណនីមួយសម្រាប់អាសយដ្ឋានអ៊ីមែលនេះរួចហើយ"),
            Map.entry("This link is invalid, has expired, or has already been used", "តំណនេះមិនត្រឹមត្រូវ ផុតកំណត់ ឬត្រូវបានប្រើរួចហើយ"),
            Map.entry("Invalid email or password", "អ៊ីមែល ឬពាក្យសម្ងាត់មិនត្រឹមត្រូវ"),
            Map.entry("An email was sent recently. Please check your inbox, or try again shortly.",
                    "អ៊ីមែលមួយទើបតែត្រូវបានផ្ញើ។ សូមពិនិត្យប្រអប់សំបុត្ររបស់អ្នក ឬព្យាយាមម្ដងទៀតបន្តិចក្រោយ។"),
            Map.entry("Session is invalid or expired; please sign in again", "វគ្គចូលមិនត្រឹមត្រូវ ឬផុតកំណត់ហើយ។ សូមចូលម្ដងទៀត"),
            Map.entry("Your family's plan includes one child.", "កញ្ចប់របស់គ្រួសារអ្នករួមបញ្ចូលកូនម្នាក់។"),
            Map.entry("Your school's plan includes one child.", "កញ្ចប់របស់សាលារបស់អ្នករួមបញ្ចូលកុមារម្នាក់។"),
            Map.entry("Your school's plan includes one teacher.", "កញ្ចប់របស់សាលារបស់អ្នករួមបញ្ចូលគ្រូម្នាក់។"),
            Map.entry("Your school's plan includes one class in use.", "កញ្ចប់របស់សាលារបស់អ្នករួមបញ្ចូលថ្នាក់ដែលកំពុងប្រើមួយ។"),
            Map.entry("This plan has already ended: give the school a plan instead", "កញ្ចប់នេះបានបញ្ចប់ហើយ៖ សូមផ្ដល់កញ្ចប់មួយឱ្យសាលាជំនួសវិញ"),
            Map.entry("This school has no subscription to change", "សាលានេះមិនមានការជាវដែលត្រូវផ្លាស់ប្ដូរទេ"),
            Map.entry("This school has no subscription to end", "សាលានេះមិនមានការជាវដែលត្រូវបញ្ចប់ទេ"),
            Map.entry("Only an active plan can be given to a school", "អាចផ្ដល់ឱ្យសាលាបានតែកញ្ចប់ដែលកំពុងដំណើរការប៉ុណ្ណោះ"),
            Map.entry("This limit belongs to another kind of plan", "ដែនកំណត់នេះជារបស់កញ្ចប់ប្រភេទផ្សេង"),
            Map.entry("This is a school plan: give it to a school", "នេះជាកញ្ចប់សាលា៖ សូមផ្ដល់វាឱ្យសាលាមួយ"),
            Map.entry("This is a family plan: give it to a family", "នេះជាកញ្ចប់គ្រួសារ៖ សូមផ្ដល់វាឱ្យគ្រួសារមួយ"),
            Map.entry("A break can be suggested after 10, 15, 20, 30 or 45 minutes", "អាចណែនាំឱ្យសម្រាកបន្ទាប់ពី 10, 15, 20, 30 ឬ 45 នាទី"),
            Map.entry("A school plan can be chosen by schools only once it has a price", "សាលាអាចជ្រើសរើសកញ្ចប់សាលាបាន លុះត្រាតែវាមានតម្លៃ"),
            Map.entry("A note is required when changing a storage allowance", "ត្រូវការកំណត់ចំណាំ នៅពេលផ្លាស់ប្ដូរកន្លែងផ្ទុកដែលបានអនុញ្ញាត"),
            Map.entry("Only teachers and principals keep media of their own", "មានតែគ្រូ និងនាយកសាលាប៉ុណ្ណោះដែលរក្សាមេឌៀផ្ទាល់ខ្លួន"),
            Map.entry("Your family's plan includes one journey per child at a time.",
                    "កញ្ចប់របស់គ្រួសារអ្នករួមបញ្ចូលផ្លូវសិក្សាមួយ សម្រាប់កូនម្នាក់ៗក្នុងពេលតែមួយ។"),
            Map.entry("This class is archived: restore it before adding to it.", "ថ្នាក់នេះត្រូវបានទុកក្នុងបណ្ណសារ៖ សូមស្ដារវាវិញ មុននឹងបន្ថែមអ្វីទៅក្នុងវា។"),
            // Validation
            Map.entry("must not be blank", "មិនអាចទុកទទេបានទេ"),
            Map.entry("must not be null", "មិនអាចទុកទទេបានទេ"),
            Map.entry("must not be empty", "មិនអាចទុកទទេបានទេ"),
            Map.entry("must be a well-formed email address", "ត្រូវតែជាអាសយដ្ឋានអ៊ីមែលត្រឹមត្រូវ"),
            Map.entry("must be a date in the past or in the present", "ត្រូវតែជាកាលបរិច្ឆេទមុន ឬថ្ងៃនេះ"),
            Map.entry("must be a future date", "ត្រូវតែជាកាលបរិច្ឆេទនាពេលអនាគត"),
            // IllegalArgumentException (400)
            Map.entry("A chapter can appear only once", "ជំពូកមួយអាចមានតែម្ដងប៉ុណ្ណោះ"),
            Map.entry("A grant must end in the future", "ការផ្ដល់កញ្ចប់ត្រូវតែបញ្ចប់នៅពេលអនាគត"),
            Map.entry("A limit can't be negative", "ដែនកំណត់មិនអាចជាចំនួនអវិជ្ជមានបានទេ"),
            Map.entry("A plan code uses capital letters, numbers and _ (2–60 characters)",
                    "កូដកញ្ចប់ប្រើអក្សរធំ លេខ និង _ (ពី 2 ដល់ 60 តួអក្សរ)"),
            Map.entry("A prerequisite must belong to the same subject", "ជំនាញមូលដ្ឋានត្រូវតែស្ថិតក្នុងមុខវិជ្ជាតែមួយ"),
            Map.entry("A price can't be negative", "តម្លៃមិនអាចជាចំនួនអវិជ្ជមានបានទេ"),
            Map.entry("A price needs an amount, a currency and a billing interval (or none of them)",
                    "តម្លៃត្រូវការចំនួនទឹកប្រាក់ រូបិយប័ណ្ណ និងរយៈពេលទូទាត់ (ឬមិនដាក់ទាំងអស់)"),
            Map.entry("A test payment can succeed, fail or be cancelled", "ការបង់ប្រាក់សាកល្បងអាចជោគជ័យ បរាជ័យ ឬត្រូវបានបោះបង់"),
            Map.entry("Enter the receipt's reference to confirm a payment", "សូមបញ្ចូលលេខយោងនៃបង្កាន់ដៃ ដើម្បីបញ្ជាក់ការបង់ប្រាក់"),
            Map.entry("Give a reason: it is kept with the payment's history", "សូមផ្ដល់មូលហេតុ៖ វាត្រូវបានរក្សាទុកជាមួយប្រវត្តិនៃការបង់ប្រាក់"),
            Map.entry("Only a bank transfer or invoice is confirmed by hand", "មានតែការផ្ទេរប្រាក់តាមធនាគារ ឬវិក្កយបត្រប៉ុណ្ណោះ ដែលត្រូវបានបញ្ជាក់ដោយដៃ"),
            Map.entry("Only a school plan can be invoiced to a school", "មានតែកញ្ចប់សាលាប៉ុណ្ណោះ ដែលអាចចេញវិក្កយបត្រឱ្យសាលាបាន"),
            Map.entry("Only a test payment can be simulated", "មានតែការបង់ប្រាក់សាកល្បងប៉ុណ្ណោះ ដែលអាចធ្វើត្រាប់តាមបាន"),
            Map.entry("Refund a PayWay payment in PayWay: it is recorded here when PayWay reports it", "សូមសងប្រាក់ការបង់តាម PayWay នៅក្នុង PayWay៖ វានឹងត្រូវបានកត់ត្រានៅទីនេះ ពេល PayWay រាយការណ៍"),
            Map.entry("This is a school plan: schools are invoiced for their plan", "នេះជាកញ្ចប់សាលា៖ សាលាទទួលវិក្កយបត្រសម្រាប់កញ្ចប់របស់ខ្លួន"),
            Map.entry("This plan isn't offered for families to choose", "កញ្ចប់នេះមិនត្រូវបានផ្ដល់ឱ្យគ្រួសារជ្រើសរើសទេ"),
            Map.entry("A payment is already in progress: finish or cancel it first", "មានការបង់ប្រាក់មួយកំពុងដំណើរការរួចហើយ៖ សូមបញ្ចប់ ឬបោះបង់វាជាមុនសិន"),
            Map.entry("Could not create a payment reference; try again", "មិនអាចបង្កើតលេខយោងការបង់ប្រាក់បានទេ សូមព្យាយាមម្ដងទៀត"),
            Map.entry("HmacSHA512 unavailable", "ប្រព័ន្ធមិនអាចចុះហត្ថលេខាលើសំណើបានទេ"),
            Map.entry("Only a payment in progress can be marked failed", "មានតែការបង់ប្រាក់ដែលកំពុងដំណើរការប៉ុណ្ណោះ ដែលអាចកត់ថាបរាជ័យបាន"),
            Map.entry("Only a payment in progress can be cancelled", "មានតែការបង់ប្រាក់ដែលកំពុងដំណើរការប៉ុណ្ណោះ ដែលអាចបោះបង់បាន"),
            Map.entry("Only a payment in progress can expire", "មានតែការបង់ប្រាក់ដែលកំពុងដំណើរការប៉ុណ្ណោះ ដែលអាចផុតកំណត់បាន"),
            Map.entry("Only a payment in progress can be changed", "មានតែការបង់ប្រាក់ដែលកំពុងដំណើរការប៉ុណ្ណោះ ដែលអាចកែប្រែបាន"),
            Map.entry("Only a payment in progress can be rejected", "មានតែការបង់ប្រាក់ដែលកំពុងដំណើរការប៉ុណ្ណោះ ដែលអាចបដិសេធបាន"),
            Map.entry("Only a payment that succeeded can be refunded", "មានតែការបង់ប្រាក់ដែលបានជោគជ័យប៉ុណ្ណោះ ដែលអាចសងប្រាក់វិញបាន"),
            Map.entry("Only an active plan can be paid for", "មានតែកញ្ចប់សកម្មប៉ុណ្ណោះ ដែលអាចបង់ប្រាក់បាន"),
            Map.entry("Test payments are switched off here", "ការបង់ប្រាក់សាកល្បងត្រូវបានបិទនៅទីនេះ"),
            Map.entry("This payment has already been settled", "ការបង់ប្រាក់នេះត្រូវបានដោះស្រាយរួចហើយ"),
            Map.entry("This payment's family no longer exists", "គ្រួសារនៃការបង់ប្រាក់នេះមិនមានទៀតទេ"),
            Map.entry("This plan has no price yet, so it can't be paid for", "កញ្ចប់នេះមិនទាន់មានតម្លៃនៅឡើយទេ ដូច្នេះមិនអាចបង់ប្រាក់បានទេ"),
            Map.entry("This plan wasn't paid for, so there is nothing to refund on it", "កញ្ចប់នេះមិនបានបង់ប្រាក់ទេ ដូច្នេះគ្មានអ្វីត្រូវសងវិញទេ"),
            Map.entry("This way of paying isn't available here", "វិធីបង់ប្រាក់នេះមិនមាននៅទីនេះទេ"),
            // M3: a child's learning day.
            Map.entry("Today's learning time is used. See you tomorrow!", "ពេលវេលារៀនសម្រាប់ថ្ងៃនេះបានប្រើអស់ហើយ។ ជួបគ្នាថ្ងៃស្អែក!"),
            Map.entry("A learning day can be 15, 20, 30, 45, 60, 90 or 120 minutes",
                    "ថ្ងៃរៀនមួយអាចមាន 15, 20, 30, 45, 60, 90 ឬ 120 នាទី"),
            Map.entry("More time can be 10, 15 or 30 minutes", "ពេលវេលាបន្ថែមអាចជា 10, 15 ឬ 30 នាទី"),
            Map.entry("There's no daily limit to extend", "មិនមានដែនកំណត់ប្រចាំថ្ងៃសម្រាប់បន្ថែមទេ"),
            Map.entry("A learning day can be extended by 2 hours at most", "ថ្ងៃរៀនមួយអាចបន្ថែមបានយ៉ាងច្រើន 2 ម៉ោង"),
            // M2: the public demo and the landing page's statements.
            Map.entry("At most 3 activities can be tried without an account", "សកម្មភាពអាចសាកល្បងបានដោយគ្មានគណនីយ៉ាងច្រើន 3 ប៉ុណ្ណោះ"),
            Map.entry("Only platform content can be tried without an account, not an author's own content",
                    "មានតែមាតិការបស់វេទិកាប៉ុណ្ណោះដែលអាចសាកល្បងបានដោយគ្មានគណនី មិនមែនមាតិកាផ្ទាល់ខ្លួនរបស់អ្នកនិពន្ធទេ"),
            Map.entry("That activity has been taken down", "សកម្មភាពនោះត្រូវបានដកចេញហើយ"),
            Map.entry("Only the current published version of an activity can be offered",
                    "មានតែកំណែដែលបានបោះពុម្ពផ្សាយបច្ចុប្បន្នរបស់សកម្មភាពប៉ុណ្ណោះដែលអាចផ្តល់ជូនបាន"),
            Map.entry("This statement was changed by someone else meanwhile. Reload it and try again.",
                    "សេចក្តីថ្លែងការណ៍នេះត្រូវបានកែប្រែដោយអ្នកផ្សេងក្នុងពេលនេះ។ សូមផ្ទុកវាឡើងវិញ ហើយព្យាយាមម្តងទៀត។"),
            Map.entry("version is required", "ត្រូវការកំណែ"),
            Map.entry("Choose whether this is Bookeyond's own statement or a testimonial",
                    "សូមជ្រើសរើសថាតើនេះជាសេចក្តីថ្លែងការណ៍ផ្ទាល់របស់ Bookeyond ឬជាសក្ខីកម្ម"),
            Map.entry("A statement needs its English and Khmer text", "សេចក្តីថ្លែងការណ៍ត្រូវការអត្ថបទជាភាសាអង់គ្លេស និងខ្មែរ"),
            Map.entry("A statement can be at most 280 characters in each language",
                    "សេចក្តីថ្លែងការណ៍អាចមានយ៉ាងច្រើន 280 តួអក្សរក្នុងភាសានីមួយៗ"),
            Map.entry("The position can't be negative", "លំដាប់មិនអាចជាលេខអវិជ្ជមានបានទេ"),
            Map.entry("A testimonial needs who said it (in both languages), where their permission is recorded, and your confirmation that it is genuine",
                    "សក្ខីកម្មត្រូវការឈ្មោះអ្នកនិយាយ (ជាពីរភាសា) កន្លែងដែលការអនុញ្ញាតរបស់ពួកគេត្រូវបានកត់ត្រា និងការបញ្ជាក់របស់អ្នកថាវាពិតប្រាកដ"),
            Map.entry("This school's subscription needs to be paid before its school services can be used", "ការជាវរបស់សាលានេះត្រូវតែបង់ប្រាក់ជាមុនសិន ទើបអាចប្រើសេវាកម្មសាលាបាន"),
            Map.entry("A school's plan starts when its payment is confirmed: invoice the school instead", "កញ្ចប់របស់សាលាចាប់ផ្ដើមនៅពេលការបង់ប្រាក់របស់វាត្រូវបានបញ្ជាក់៖ សូមចេញវិក្កយបត្រឱ្យសាលាជំនួសវិញ"),
            Map.entry("This plan isn't offered for schools to choose: ask the Bookeyond team", "កញ្ចប់នេះមិនត្រូវបានផ្ដល់ឱ្យសាលាជ្រើសរើសទេ៖ សូមសួរក្រុម Bookeyond"),
            Map.entry("The school's paid plan is still running: to change plans, ask the Bookeyond team",
                    "កញ្ចប់ដែលសាលាបានបង់ប្រាក់នៅតែដំណើរការ៖ ដើម្បីប្ដូរកញ្ចប់ សូមសួរក្រុម Bookeyond"),
            Map.entry("A question can appear only once in an activity", "សំណួរមួយអាចមានតែម្ដងក្នុងសកម្មភាពមួយ"),
            Map.entry("Agree to the terms of use and the privacy notice to create an account",
                    "សូមយល់ព្រមលើលក្ខខណ្ឌប្រើប្រាស់ និងសេចក្ដីជូនដំណឹងអំពីឯកជនភាព ដើម្បីបង្កើតគណនី"),
            Map.entry("An objective can appear only once in a curriculum", "គោលបំណងមួយអាចមានតែម្ដងក្នុងកម្មវិធីសិក្សាមួយ"),
            Map.entry("An objective's skills must belong to the objective's subject", "ជំនាញរបស់គោលបំណងត្រូវតែស្ថិតក្នុងមុខវិជ្ជារបស់គោលបំណងនោះ"),
            Map.entry("Families aren't staff: find a parent's account by their email address",
                    "គ្រួសារមិនមែនជាបុគ្គលិកទេ៖ សូមស្វែងរកគណនីឪពុកម្ដាយតាមអាសយដ្ឋានអ៊ីមែលរបស់ពួកគេ"),
            Map.entry("Not a media key", "មិនមែនជាកូដឯកសារមេឌៀទេ"),
            Map.entry("Nothing to change: give a name or a date of birth", "គ្មានអ្វីត្រូវផ្លាស់ប្ដូរទេ៖ សូមផ្ដល់ឈ្មោះ ឬថ្ងៃខែឆ្នាំកំណើត"),
            Map.entry("Only PRINCIPAL accounts can be approved here", "មានតែគណនីនាយកសាលាទេ ដែលអាចអនុម័តនៅទីនេះ"),
            Map.entry("Question is already in this activity version", "សំណួរនេះមាននៅក្នុងកំណែសកម្មភាពនេះរួចហើយ"),
            Map.entry("Skills must be active skills of the content's subject", "ជំនាញត្រូវតែជាជំនាញសកម្មនៃមុខវិជ្ជារបស់មាតិកានេះ"),
            Map.entry("Staff accounts are teachers, principals or administrators; families sign up themselves",
                    "គណនីបុគ្គលិកគឺជាគ្រូ នាយកសាលា ឬអ្នកគ្រប់គ្រង។ គ្រួសារចុះឈ្មោះដោយខ្លួនឯង"),
            Map.entry("The new end must be in the future; to stop the plan now, end it",
                    "ថ្ងៃបញ្ចប់ថ្មីត្រូវតែនៅពេលអនាគត។ ដើម្បីបញ្ឈប់កញ្ចប់ឥឡូវនេះ សូមបញ្ចប់វា"),
            Map.entry("before and beforeId go together", "សំណើនេះមិនត្រឹមត្រូវទេ"),
            Map.entry("page must be 0 or more", "សំណើនេះមិនត្រឹមត្រូវទេ"),
            Map.entry("That file is a recording, not a picture.", "ឯកសារនោះជាការថតសំឡេង មិនមែនជារូបភាពទេ។"),
            Map.entry("That file is a picture, not a recording.", "ឯកសារនោះជារូបភាព មិនមែនជាការថតសំឡេងទេ។"),
            Map.entry("Unknown learning objective", "មិនស្គាល់គោលបំណងសិក្សានេះទេ"),
            Map.entry("That activity doesn't practise this skill, or isn't published", "សកម្មភាពនោះមិនហាត់ជំនាញនេះ ឬមិនទាន់ត្រូវបានផ្សព្វផ្សាយទេ"),
            // IllegalStateException (409)
            Map.entry("AdaptiveEngine.pickActivity must never be called for CONTINUE", "មានបញ្ហាមួយបានកើតឡើង។ សូមព្យាយាមម្ដងទៀត។"),
            Map.entry("Administrator accounts can't be suspended here", "មិនអាចផ្អាកគណនីអ្នកគ្រប់គ្រងនៅទីនេះបានទេ"),
            Map.entry("Another parent shares this family; deleting a shared family isn't supported yet",
                    "ឪពុកម្ដាយម្នាក់ទៀតកំពុងចែករំលែកគ្រួសារនេះ។ ការលុបគ្រួសារដែលចែករំលែកមិនទាន់អាចធ្វើបានទេ"),
            Map.entry("Bootstrap admin requires email and password", "អ្នកគ្រប់គ្រងដំបូងត្រូវការអ៊ីមែល និងពាក្យសម្ងាត់"),
            Map.entry("Confirm your email address before giving a school access to your child's learning",
                    "សូមបញ្ជាក់អាសយដ្ឋានអ៊ីមែលរបស់អ្នក មុននឹងអនុញ្ញាតឱ្យសាលាមើលការសិក្សារបស់កូនអ្នក"),
            Map.entry("Couldn't make a new code; please try again", "មិនអាចបង្កើតកូដថ្មីបានទេ។ សូមព្យាយាមម្ដងទៀត"),
            Map.entry("Families are still offered this subject's curricula. Archive those curricula first.",
                    "គ្រួសារនៅតែអាចជ្រើសរើសកម្មវិធីសិក្សានៃមុខវិជ្ជានេះ។ សូមទុកកម្មវិធីសិក្សាទាំងនោះក្នុងបណ្ណសារជាមុនសិន។"),
            Map.entry("Its lesson is archived. Restore the lesson first.", "មេរៀនរបស់វាត្រូវបានទុកក្នុងបណ្ណសារ។ សូមស្ដារមេរៀននោះវិញជាមុនសិន។"),
            Map.entry("Only a draft can be reviewed", "មានតែសេចក្ដីព្រាងទេ ដែលអាចពិនិត្យបាន"),
            Map.entry("Only a suspended account can be reinstated", "មានតែគណនីដែលត្រូវបានផ្អាកទេ ដែលអាចស្ដារឡើងវិញបាន"),
            Map.entry("Only a version children have been given can be taken down; a draft never reached them",
                    "អាចដកចេញបានតែកំណែដែលកុមារបានទទួលរួចហើយ។ សេចក្ដីព្រាងមិនដែលទៅដល់ពួកគេទេ"),
            Map.entry("Only an active account can be suspended", "មានតែគណនីសកម្មទេ ដែលអាចផ្អាកបាន"),
            Map.entry("Only an active plan can be given to a family", "មានតែកញ្ចប់សកម្មទេ ដែលអាចផ្ដល់ឱ្យគ្រួសារបាន"),
            Map.entry("Only an active plan can be retired", "មានតែកញ្ចប់សកម្មទេ ដែលអាចបញ្ឈប់បាន"),
            Map.entry("Only an active plan can be the default", "មានតែកញ្ចប់សកម្មទេ ដែលអាចជាកញ្ចប់លំនាំដើមបាន"),
            Map.entry("Only pending accounts can be approved", "មានតែគណនីដែលកំពុងរង់ចាំទេ ដែលអាចអនុម័តបាន"),
            Map.entry("Only the person who asked can withdraw this review", "មានតែអ្នកដែលបានស្នើទេ ដែលអាចដកការពិនិត្យនេះវិញបាន"),
            Map.entry("Restore the school first.", "សូមស្ដារសាលាវិញជាមុនសិន។"),
            Map.entry("That isn't the current version of the terms and privacy notice",
                    "នោះមិនមែនជាកំណែបច្ចុប្បន្ននៃលក្ខខណ្ឌ និងសេចក្ដីជូនដំណឹងអំពីឯកជនភាពទេ"),
            Map.entry("That media is archived. Restore it in the media library to use it again.",
                    "ឯកសារមេឌៀនោះត្រូវបានទុកក្នុងបណ្ណសារ។ សូមស្ដារវាវិញនៅក្នុងបណ្ណាល័យមេឌៀ ដើម្បីប្រើវាម្ដងទៀត។"),
            Map.entry("The default plan can't be retired: make another plan the default first",
                    "មិនអាចបញ្ឈប់កញ្ចប់លំនាំដើមបានទេ៖ សូមកំណត់កញ្ចប់ផ្សេងទៀតជាលំនាំដើមជាមុនសិន"),
            Map.entry("The draft changed after the review was asked: its author asks again",
                    "សេចក្ដីព្រាងបានផ្លាស់ប្ដូរ បន្ទាប់ពីការស្នើសុំពិនិត្យ៖ អ្នកនិពន្ធត្រូវស្នើម្ដងទៀត"),
            Map.entry("This account is already being deleted", "គណនីនេះកំពុងត្រូវបានលុបរួចហើយ"),
            Map.entry("This account is not being deleted", "គណនីនេះមិនកំពុងត្រូវបានលុបទេ"),
            Map.entry("This child is not being deleted", "កូននេះមិនកំពុងត្រូវបានលុបទេ"),
            Map.entry("This class is already archived.", "ថ្នាក់នេះត្រូវបានទុកក្នុងបណ្ណសាររួចហើយ។"),
            Map.entry("This class is archived: families can't join it", "ថ្នាក់នេះត្រូវបានទុកក្នុងបណ្ណសារ៖ គ្រួសារមិនអាចចូលរួមបានទេ"),
            Map.entry("This class is not archived.", "ថ្នាក់នេះមិនត្រូវបានទុកក្នុងបណ្ណសារទេ។"),
            Map.entry("This content is published without a review", "មាតិកានេះត្រូវបានផ្សព្វផ្សាយដោយមិនចាំបាច់ពិនិត្យ"),
            Map.entry("This curriculum is archived and no longer offered.", "កម្មវិធីសិក្សានេះត្រូវបានទុកក្នុងបណ្ណសារ ហើយលែងផ្ដល់ជូនទៀតហើយ។"),
            Map.entry("This email address can't be invited as a teacher. Ask them for a different address.",
                    "មិនអាចអញ្ជើញអាសយដ្ឋានអ៊ីមែលនេះជាគ្រូបានទេ។ សូមសុំអាសយដ្ឋានផ្សេងពីពួកគេ។"),
            Map.entry("This email address is already confirmed", "អាសយដ្ឋានអ៊ីមែលនេះត្រូវបានបញ្ជាក់រួចហើយ"),
            Map.entry("This email address now belongs to another account", "អាសយដ្ឋានអ៊ីមែលនេះឥឡូវជារបស់គណនីផ្សេង"),
            Map.entry("This file is used by questions, so it can't be deleted. Archive it instead.",
                    "ឯកសារនេះកំពុងប្រើក្នុងសំណួរ ដូច្នេះមិនអាចលុបបានទេ។ សូមទុកវាក្នុងបណ្ណសារជំនួសវិញ។"),
            Map.entry("This invitation is no longer open", "ការអញ្ជើញនេះលែងបើកទៀតហើយ"),
            Map.entry("This is already your sign-in email", "នេះជាអ៊ីមែលចូលគណនីរបស់អ្នករួចហើយ"),
            Map.entry("This learning path is no longer offered.", "ផ្លូវសិក្សានេះលែងផ្ដល់ជូនទៀតហើយ។"),
            Map.entry("This lesson still has activities in use. Archive its activities first.",
                    "មេរៀននេះនៅមានសកម្មភាពកំពុងប្រើ។ សូមទុកសកម្មភាពរបស់វាក្នុងបណ្ណសារជាមុនសិន។"),
            Map.entry("This path has been stopped.", "ផ្លូវសិក្សានេះត្រូវបានបញ្ឈប់ហើយ។"),
            Map.entry("This path is already stopped.", "ផ្លូវសិក្សានេះត្រូវបានបញ្ឈប់រួចហើយ។"),
            Map.entry("This path is finished; there is nothing to stop.", "ផ្លូវសិក្សានេះបានបញ្ចប់ហើយ។ គ្មានអ្វីត្រូវបញ្ឈប់ទេ។"),
            Map.entry("This person is already on the school's staff", "បុគ្គលនេះជាបុគ្គលិករបស់សាលារួចហើយ"),
            Map.entry("This person is not on the school's staff", "បុគ្គលនេះមិនមែនជាបុគ្គលិករបស់សាលាទេ"),
            Map.entry("This plan has already ended: give the family a plan instead", "កញ្ចប់នេះបានបញ្ចប់ហើយ៖ សូមផ្ដល់កញ្ចប់ថ្មីឱ្យគ្រួសារជំនួសវិញ"),
            Map.entry("This request has already been answered", "សំណើនេះត្រូវបានឆ្លើយតបរួចហើយ"),
            Map.entry("This review has already been answered", "ការពិនិត្យនេះត្រូវបានឆ្លើយតបរួចហើយ"),
            Map.entry("This school is already archived.", "សាលានេះត្រូវបានទុកក្នុងបណ្ណសាររួចហើយ។"),
            Map.entry("This school is archived: no one can join it until its principal restores it.",
                    "សាលានេះត្រូវបានទុកក្នុងបណ្ណសារ៖ គ្មាននរណាអាចចូលរួមបានទេ រហូតដល់នាយកសាលាស្ដារវាវិញ។"),
            Map.entry("This school is archived: restore it to add classes.", "សាលានេះត្រូវបានទុកក្នុងបណ្ណសារ៖ សូមស្ដារវាវិញ ដើម្បីបន្ថែមថ្នាក់។"),
            Map.entry("This school is archived: restore it to invite staff.", "សាលានេះត្រូវបានទុកក្នុងបណ្ណសារ៖ សូមស្ដារវាវិញ ដើម្បីអញ្ជើញបុគ្គលិក។"),
            Map.entry("This school is not archived.", "សាលានេះមិនត្រូវបានទុកក្នុងបណ្ណសារទេ។"),
            Map.entry("This school's access has already ended", "សិទ្ធិចូលប្រើរបស់សាលានេះបានបញ្ចប់រួចហើយ"),
            Map.entry("This subscription has already ended", "ការជាវនេះបានបញ្ចប់រួចហើយ"),
            Map.entry("This subscription is managed by the payment provider; change it there",
                    "ការជាវនេះត្រូវបានគ្រប់គ្រងដោយអ្នកផ្ដល់សេវាទូទាត់។ សូមផ្លាស់ប្ដូរវានៅទីនោះ"),
            Map.entry("This version has already been taken down", "កំណែនេះត្រូវបានដកចេញរួចហើយ"),
            Map.entry("You asked for this review: another administrator approves it",
                    "អ្នកបានស្នើការពិនិត្យនេះ៖ អ្នកគ្រប់គ្រងម្នាក់ទៀតត្រូវអនុម័តវា"),
            Map.entry("You asked for this review: withdraw it instead", "អ្នកបានស្នើការពិនិត្យនេះ៖ សូមដកវាវិញជំនួសវិញ"),
            Map.entry("Your family is already on this plan", "គ្រួសាររបស់អ្នកកំពុងប្រើកញ្ចប់នេះរួចហើយ"),
            Map.entry("Platform content needs a second administrator's review before it is published: ask for a review",
                    "មាតិកាវេទិកាត្រូវការការពិនិត្យពីអ្នកគ្រប់គ្រងទីពីរ មុននឹងផ្សព្វផ្សាយ៖ សូមស្នើសុំការពិនិត្យ"),
            Map.entry("No review is waiting for this draft", "គ្មានការពិនិត្យណាកំពុងរង់ចាំសេចក្ដីព្រាងនេះទេ"),
            Map.entry("This version isn't taken down", "កំណែនេះមិនត្រូវបានដកចេញទេ"),
            Map.entry("It is already archived.", "វាត្រូវបានទុកក្នុងបណ្ណសាររួចហើយ។"),
            Map.entry("It isn't archived.", "វាមិនត្រូវបានទុកក្នុងបណ្ណសារទេ។"),
            Map.entry("Start a new version to change the skills this activity practises: a published version keeps the skills it was published with.",
                    "សូមចាប់ផ្ដើមកំណែថ្មី ដើម្បីផ្លាស់ប្ដូរជំនាញដែលសកម្មភាពនេះហាត់៖ កំណែដែលបានផ្សព្វផ្សាយរក្សាជំនាញដែលវាបានផ្សព្វផ្សាយជាមួយ។"),
            Map.entry("Start a new version to change the skills this question gives evidence for: a published version keeps the skills it was published with.",
                    "សូមចាប់ផ្ដើមកំណែថ្មី ដើម្បីផ្លាស់ប្ដូរជំនាញដែលសំណួរនេះផ្ដល់ភស្តុតាង៖ កំណែដែលបានផ្សព្វផ្សាយរក្សាជំនាញដែលវាបានផ្សព្វផ្សាយជាមួយ។"),
            Map.entry("Your family has no plan request open", "គ្រួសាររបស់អ្នកមិនមានសំណើកញ្ចប់ដែលកំពុងបើកទេ"),
            Map.entry("This family has no subscription to change", "គ្រួសារនេះមិនមានការជាវដែលត្រូវផ្លាស់ប្ដូរទេ"),
            Map.entry("This family has no subscription to end", "គ្រួសារនេះមិនមានការជាវដែលត្រូវបញ្ចប់ទេ"),
            Map.entry("No default subscription plan is configured", "មិនទាន់មានកញ្ចប់លំនាំដើមទេ"),
            Map.entry("Too many class codes tried. Please wait a little and try again.",
                    "បានសាកល្បងលេខកូដថ្នាក់ច្រើនពេក។ សូមរង់ចាំបន្តិច ហើយព្យាយាមម្ដងទៀត។"),
            // Answers (422 / 501)
            Map.entry("A matching answer must pair each item with a different match", "ចម្លើយផ្គូផ្គងត្រូវផ្គូធាតុនីមួយៗជាមួយគូផ្សេងគ្នា"),
            Map.entry("A matching answer must pair each item with one match", "ចម្លើយផ្គូផ្គងត្រូវផ្គូធាតុនីមួយៗជាមួយគូមួយ"),
            Map.entry("A matching answer must pair every item with one match", "ចម្លើយផ្គូផ្គងត្រូវផ្គូធាតុទាំងអស់ ម្នាក់ៗជាមួយគូមួយ"),
            Map.entry("A sorting answer must put each of this question's items in one of its groups",
                    "ចម្លើយតម្រៀបក្រុមត្រូវដាក់ធាតុនីមួយៗនៃសំណួរនេះក្នុងក្រុមមួយរបស់វា"),
            Map.entry("A sorting answer must put every item in a group", "ចម្លើយតម្រៀបក្រុមត្រូវដាក់ធាតុទាំងអស់ក្នុងក្រុម"),
            Map.entry("An ordering answer must place every item once", "ចម្លើយតម្រៀបលំដាប់ត្រូវដាក់ធាតុនីមួយៗតែម្ដង"),
            Map.entry("The submitted answer is not one of this question's options", "ចម្លើយដែលបានផ្ញើមិនមែនជាជម្រើសមួយនៃសំណួរនេះទេ"),
            Map.entry("A true/false answer must be \"true\" or \"false\"", "ចម្លើយត្រូវ/ខុស ត្រូវតែជា \"ត្រូវ\" ឬ \"ខុស\""),
            Map.entry("A numeric answer must be a number", "ចម្លើយជាលេខត្រូវតែជាលេខ"),
            Map.entry("This matching question has no pairs and cannot be scored", "សំណួរផ្គូផ្គងនេះគ្មានគូ ហើយមិនអាចដាក់ពិន្ទុបានទេ"),
            Map.entry("This ordering question has no items to put in order and cannot be scored",
                    "សំណួរតម្រៀបលំដាប់នេះគ្មានធាតុសម្រាប់តម្រៀប ហើយមិនអាចដាក់ពិន្ទុបានទេ"),
            Map.entry("This sorting question has no groups or items and cannot be scored", "សំណួរតម្រៀបក្រុមនេះគ្មានក្រុម ឬធាតុ ហើយមិនអាចដាក់ពិន្ទុបានទេ"),
            Map.entry("This true/false question has no valid correct answer and cannot be scored",
                    "សំណួរត្រូវ/ខុសនេះគ្មានចម្លើយត្រឹមត្រូវ ហើយមិនអាចដាក់ពិន្ទុបានទេ"),
            Map.entry("This numeric question has no valid correct answer and cannot be scored", "សំណួរលេខនេះគ្មានចម្លើយត្រឹមត្រូវ ហើយមិនអាចដាក់ពិន្ទុបានទេ"),
            // Media (422)
            Map.entry("That file is empty or too small to be a picture or a recording.", "ឯកសារនោះទទេ ឬតូចពេកក្នុងការជារូបភាព ឬការថតសំឡេង។"),
            Map.entry("That picture's file is damaged.", "ឯកសាររូបភាពនោះខូច។"),
            Map.entry("Upload a picture (PNG, JPEG or WebP) or a recording (MP3, M4A, Ogg or WAV).",
                    "សូមផ្ទុកឡើងរូបភាព (PNG, JPEG ឬ WebP) ឬការថតសំឡេង (MP3, M4A, Ogg ឬ WAV)។"),
            // Passwords (422)
            Map.entry("Don't use your email address in your password", "កុំប្រើអាសយដ្ឋានអ៊ីមែលរបស់អ្នកក្នុងពាក្យសម្ងាត់"),
            Map.entry("Password must not be blank", "ពាក្យសម្ងាត់មិនអាចទុកទទេបានទេ"),
            Map.entry("That password is a few characters repeated: choose one that is harder to guess",
                    "ពាក្យសម្ងាត់នោះគ្រាន់តែជាតួអក្សរពីរបីដដែលៗ៖ សូមជ្រើសរើសពាក្យសម្ងាត់ដែលពិបាកទាយជាងនេះ"),
            Map.entry("That password is counting or a row of the keyboard: choose one that is harder to guess",
                    "ពាក្យសម្ងាត់នោះជាការរាប់លេខ ឬជួរឃីបតមួយ៖ សូមជ្រើសរើសពាក្យសម្ងាត់ដែលពិបាកទាយជាងនេះ"),
            Map.entry("That password is one of the first anyone would try: choose one that is harder to guess",
                    "ពាក្យសម្ងាត់នោះជាពាក្យដំបូងគេដែលអ្នកណាក៏សាកដែរ៖ សូមជ្រើសរើសពាក្យសម្ងាត់ដែលពិបាកទាយជាងនេះ"),
            Map.entry("The new password must be different from the current one", "ពាក្យសម្ងាត់ថ្មីត្រូវតែខុសពីពាក្យសម្ងាត់បច្ចុប្បន្ន")
    );

}
