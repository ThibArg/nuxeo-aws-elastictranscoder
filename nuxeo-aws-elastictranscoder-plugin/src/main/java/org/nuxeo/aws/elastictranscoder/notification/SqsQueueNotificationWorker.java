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
 *     Thibaud Arguillere
 */
package org.nuxeo.aws.elastictranscoder.notification;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import com.amazonaws.services.sqs.AmazonSQS;
import com.amazonaws.services.sqs.model.DeleteMessageRequest;
import com.amazonaws.services.sqs.model.Message;
import com.amazonaws.services.sqs.model.ReceiveMessageRequest;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 2014-12, First implementation: Beside some renaming, all this comes from AWS
 * SDK Elastic Transcoder sample code.
 *
 * @since 7.1
 */
/**
 * Polls an SQS queue for Elastic Transcoder job status notification messages
 * and calls the handle method on all registered JobStatusNotificationHandler
 * objects before deleting the message from the queue.
 */
public class SqsQueueNotificationWorker implements Runnable {

    private static final int MAX_NUMBER_OF_MESSAGES = 5;

    private static final int VISIBILITY_TIMEOUT = 15;

    private static final int WAIT_TIME_SECONDS = 15;

    private static final ObjectMapper mapper = new ObjectMapper();

    private AmazonSQS amazonSqs;

    private String queueUrl;

    private List<JobStatusNotificationHandler> handlers;

    private volatile boolean shutdown = false;

    public SqsQueueNotificationWorker(AmazonSQS amazonSqs, String queueUrl) {
        this.amazonSqs = amazonSqs;
        this.queueUrl = queueUrl;
        this.handlers = new ArrayList<JobStatusNotificationHandler>();
    }

    public void addHandler(
            JobStatusNotificationHandler jobStatusNotificationHandler) {
        synchronized (handlers) {
            this.handlers.add(jobStatusNotificationHandler);
        }
    }

    public void removeHandler(
            JobStatusNotificationHandler jobStatusNotificationHandler) {
        synchronized (handlers) {
            this.handlers.remove(jobStatusNotificationHandler);
        }
    }

    @Override
    public void run() {
        ReceiveMessageRequest receiveMessageRequest = new ReceiveMessageRequest().withQueueUrl(
                queueUrl).withMaxNumberOfMessages(MAX_NUMBER_OF_MESSAGES).withVisibilityTimeout(
                VISIBILITY_TIMEOUT).withWaitTimeSeconds(WAIT_TIME_SECONDS);

        while (!shutdown) {
            // Long pole the SQS queue. This will return as soon as a message
            // is received, or when WAIT_TIME_SECONDS has elapsed.
            List<Message> messages = amazonSqs.receiveMessage(
                    receiveMessageRequest).getMessages();
            if (messages == null) {
                // If there were no messages during this poll period, SQS
                // will return this list as null. Continue polling.
                continue;
            }

            synchronized (handlers) {
                for (Message message : messages) {
                    try {
                        // Parse notification and call handlers.
                        JobStatusNotification notification = parseNotification(message);
                        for (JobStatusNotificationHandler handler : handlers) {
                            handler.handle(notification);
                        }
                    } catch (IOException e) {
                        System.out.println("Failed to convert notification: "
                                + e.getMessage());
                    }

                    // Delete the message from the queue.
                    amazonSqs.deleteMessage(new DeleteMessageRequest().withQueueUrl(
                            queueUrl).withReceiptHandle(
                            message.getReceiptHandle()));
                }
            }
        }
    }

    private JobStatusNotification parseNotification(Message message)
            throws IOException {
        SNSNotification<JobStatusNotification> notification = mapper.readValue(
                message.getBody(),
                new TypeReference<SNSNotification<JobStatusNotification>>() {
                });
        return notification.getMessage();
    }

    public void shutdown() {
        shutdown = true;
    }
}
