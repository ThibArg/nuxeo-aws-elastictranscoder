/*
 * (C) Copyright 2014 Nuxeo SA (http://nuxeo.com/) and contributors.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Lesser General Public License
 * (LGPL) version 2.1 which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/lgpl-2.1.html
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * Contributors:
 *     Thibaud Arguillee
 */
package org.nuxeo.aws.elastictranscoder.notification;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 2014-12, First implementation: Beside some renaming, all this comes from AWS
 * SDK Elastic Transcoder sample code.
 *
 * @since 7.1
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class SNSNotification<MESSAGE_TYPE> {

    private String type;

    private String messageId;

    private String topicArn;

    private String subject;

    private MESSAGE_TYPE message;

    private String timestamp;

    private String signatureVersion;

    private String signature;

    private String signingCertURL;

    private String unsubscribeURL;

    @JsonProperty(value = "Type")
    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    @JsonProperty(value = "MessageId")
    public String getMessageId() {
        return messageId;
    }

    public void setMessageId(String messageId) {
        this.messageId = messageId;
    }

    @JsonProperty(value = "TopicArn")
    public String getTopicArn() {
        return topicArn;
    }

    public void setTopicArn(String topicArn) {
        this.topicArn = topicArn;
    }

    @JsonProperty(value = "Subject")
    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    @JsonProperty(value = "Message")
    public MESSAGE_TYPE getMessage() {
        return message;
    }

    public void setNotification(MESSAGE_TYPE message) {
        this.message = message;
    }

    @JsonProperty(value = "Timestamp")
    public String getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(String timestamp) {
        this.timestamp = timestamp;
    }

    @JsonProperty(value = "SignatureVersion")
    public String getSignatureVersion() {
        return signatureVersion;
    }

    public void setSignatureVersion(String signatureVersion) {
        this.signatureVersion = signatureVersion;
    }

    @JsonProperty(value = "Signature")
    public String getSignature() {
        return signature;
    }

    public void setSignature(String signature) {
        this.signature = signature;
    }

    @JsonProperty(value = "SigningCertURL")
    public String getSigningCertURL() {
        return signingCertURL;
    }

    public void setSigningCertURL(String signingCertURL) {
        this.signingCertURL = signingCertURL;
    }

    @JsonProperty(value = "UnsubscribeURL")
    public String getUnsubscribeURL() {
        return unsubscribeURL;
    }

    public void setUnsubscribeURL(String unsubscribeURL) {
        this.unsubscribeURL = unsubscribeURL;
    }

    @Override
    public String toString() {
        return "Notification [type=" + type + ", messageId=" + messageId
                + ", topicArn=" + topicArn + ", subject=" + subject
                + ", message=" + message + ", timestamp=" + timestamp
                + ", signatureVersion=" + signatureVersion + ", signature="
                + signature + ", signingCertURL=" + signingCertURL
                + ", unsubscribeURL=" + unsubscribeURL + "]";
    }
}