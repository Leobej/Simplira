package io.github.leobej.infrastructure.email;

import org.springframework.web.util.HtmlUtils;

// Every email goes out as plain text and HTML (§25), built here rather than in a template engine:
// four short messages do not need one. Anything that came from a user - a workspace name, an
// issue title in a notification - is escaped before it touches the HTML.
final class EmailTemplates {

    record Email(String subject, String text, String html) {}

    private EmailTemplates() {}

    static Email invitation(String workspaceName, String link) {
        return withButton("You're invited to " + oneLine(workspaceName) + " on Simplira",
                "You have been invited to join the workspace \"" + workspaceName + "\" on Simplira.",
                "Accept the invitation", link,
                "If you were not expecting this, you can ignore it.");
    }

    static Email emailVerification(String link) {
        return withButton("Confirm your email address for Simplira",
                "Confirm that this is your address to start creating and joining workspaces.",
                "Confirm my address", link,
                "If you did not create a Simplira account, you can ignore this.");
    }

    static Email passwordReset(String link) {
        return withButton("Reset your Simplira password",
                "Someone - hopefully you - asked to reset the password for this Simplira account. "
                        + "The link works once, for an hour.",
                "Choose a new password", link,
                "If you did not ask for this, ignore it: your password stays as it is.");
    }

    static Email notification(String title, String body, String appBaseUrl) {
        String text = title + (body.isBlank() ? "" : "\n\n" + body)
                + "\n\nOpen Simplira: " + appBaseUrl
                + "\nYou can choose which emails you get in each workspace's notification settings.";
        String html = "<p><strong>" + HtmlUtils.htmlEscape(title) + "</strong></p>"
                + (body.isBlank() ? "" : "<p>" + HtmlUtils.htmlEscape(body).replace("\n", "<br>") + "</p>")
                + button("Open Simplira", appBaseUrl)
                + "<p style=\"color:#6b7280;font-size:12px\">You can choose which emails you get in each "
                + "workspace's notification settings.</p>";
        return new Email(oneLine(title), text, wrap(html));
    }

    private static Email withButton(String subject, String intro, String action, String link, String footer) {
        String text = intro + "\n\n" + action + ": " + link + "\n\n" + footer;
        String html = "<p>" + HtmlUtils.htmlEscape(intro) + "</p>" + button(action, link)
                + "<p style=\"color:#6b7280;font-size:12px\">If the button does not work, open this link:<br>"
                + HtmlUtils.htmlEscape(link) + "</p>"
                + "<p style=\"color:#6b7280;font-size:12px\">" + HtmlUtils.htmlEscape(footer) + "</p>";
        return new Email(oneLine(subject), text, wrap(html));
    }

    private static String button(String label, String link) {
        return "<p><a href=\"" + HtmlUtils.htmlEscape(link) + "\" style=\"display:inline-block;padding:10px 16px;"
                + "background:#2563eb;color:#ffffff;text-decoration:none;border-radius:6px\">"
                + HtmlUtils.htmlEscape(label) + "</a></p>";
    }

    private static String wrap(String content) {
        return "<!doctype html><html><body style=\"font-family:system-ui,-apple-system,Segoe UI,sans-serif;"
                + "color:#111827;line-height:1.5;max-width:560px;margin:24px auto;padding:0 16px\">"
                + content + "</body></html>";
    }

    // A subject is one header line: a name with a line break in it must not start a new header.
    private static String oneLine(String value) {
        return value.replaceAll("[\\r\\n]+", " ").strip();
    }
}
