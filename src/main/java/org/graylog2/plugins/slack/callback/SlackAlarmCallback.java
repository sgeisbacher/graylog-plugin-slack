package org.graylog2.plugins.slack.callback;

import com.google.common.collect.Lists;
import org.apache.commons.lang3.StringUtils;
import org.graylog2.plugin.MessageSummary;
import org.graylog2.plugins.slack.SlackClient;
import org.graylog2.plugins.slack.SlackMessage;
import org.graylog2.plugins.slack.SlackPluginBase;
import org.graylog2.plugin.alarms.AlertCondition;
import org.graylog2.plugin.alarms.callbacks.AlarmCallback;
import org.graylog2.plugin.alarms.callbacks.AlarmCallbackConfigurationException;
import org.graylog2.plugin.alarms.callbacks.AlarmCallbackException;
import org.graylog2.plugin.configuration.Configuration;
import org.graylog2.plugin.configuration.ConfigurationException;
import org.graylog2.plugin.configuration.ConfigurationRequest;
import org.graylog2.plugin.streams.Stream;

import java.util.List;
import java.util.Map;

import static com.google.common.base.Strings.isNullOrEmpty;

public class SlackAlarmCallback extends SlackPluginBase implements AlarmCallback {

    private Configuration configuration;

    @Override
    public void initialize(final Configuration config) throws AlarmCallbackConfigurationException {
        this.configuration = config;

        try {
            checkConfiguration(config);
        } catch (ConfigurationException e) {
            throw new AlarmCallbackConfigurationException("Configuration error. " + e.getMessage());
        }
    }

    @Override
    public void call(Stream stream, AlertCondition.CheckResult result) throws AlarmCallbackException {
        final SlackClient client = new SlackClient(configuration.getString(CK_WEBHOOK_URL));

        SlackMessage message = new SlackMessage(
                configuration.getString(CK_COLOR),
                configuration.getString(CK_ICON_EMOJI),
                configuration.getString(CK_ICON_URL),
                buildMessage(stream, result),
                configuration.getString(CK_USER_NAME),
                configuration.getString(CK_CHANNEL),
                configuration.getBoolean(CK_LINK_NAMES)
        );

        // Add attachments if requested.
        if(configuration.getBoolean(CK_ADD_ATTACHMENT)) {
            message.addAttachment(new SlackMessage.AttachmentField("Source(s)", buildSourcesList(result), false));
            message.addAttachment(new SlackMessage.AttachmentField("Stream ID", stream.getId(), true));
            message.addAttachment(new SlackMessage.AttachmentField("Stream Title", stream.getTitle(), false));
            message.addAttachment(new SlackMessage.AttachmentField("Stream Description", stream.getDescription(), false));
        }

        try {
            client.send(message);
        } catch (SlackClient.SlackClientException e) {
            throw new RuntimeException("Could not send message to Slack.", e);
        }
    }

    private String buildSourcesList(AlertCondition.CheckResult result) {
        if (result != null && result.getMatchingMessages() != null) {
            List<String> sources = Lists.newArrayList();
            for (MessageSummary messageSummary : result.getMatchingMessages()) {
                if (messageSummary != null) {
                    sources.add(messageSummary.getSource());
                }
            }
            return StringUtils.join(sources, ",");
        }
        return "no sources found";
    }

    public String buildMessage(Stream stream, AlertCondition.CheckResult result) {
        String graylogUri = configuration.getString(CK_GRAYLOG2_URL);
        boolean notifyChannel = configuration.getBoolean(CK_NOTIFY_CHANNEL);

        String titleLink;
        if (isSet(graylogUri)) {
            titleLink = "<" + buildStreamLink(graylogUri, stream) + "|" + stream.getTitle() + ">";
        } else {
            titleLink = "_" + stream.getTitle() + "_";
        }

        final StringBuilder message = new StringBuilder(notifyChannel ? "@channel " : "");
        message.append("*Alert for Graylog stream ").append(titleLink).append("*:\n").append("> ").append(result.getResultDescription());

        return message.toString();
    }

    private final boolean isSet(String x) {
        // Bug in graylog-server v1.2: Empty values are stored as "null" String. This is a dirty workaround.
        return !isNullOrEmpty(x) && !x.equals("null");
    }

    @Override
    public Map<String, Object> getAttributes() {
        return configuration.getSource();
    }

    @Override
    public void checkConfiguration() throws ConfigurationException {
        /* Never actually called by graylog-server */
    }

    @Override
    public ConfigurationRequest getRequestedConfiguration() {
        return configuration();
    }

    @Override
    public String getName() {
        return "Slack alarm callback";
    }
}