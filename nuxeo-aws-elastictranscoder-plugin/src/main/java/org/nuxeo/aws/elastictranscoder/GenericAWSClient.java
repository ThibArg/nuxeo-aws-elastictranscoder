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
 *     thibaud
 */
package org.nuxeo.aws.elastictranscoder;

import org.apache.commons.lang.StringUtils;
import org.nuxeo.ecm.core.api.ClientException;
import org.nuxeo.runtime.api.Framework;

import com.amazonaws.AmazonClientException;
import com.amazonaws.AmazonServiceException;
import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.services.elastictranscoder.AmazonElasticTranscoder;
import com.amazonaws.services.elastictranscoder.AmazonElasticTranscoderClient;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.sqs.AmazonSQS;
import com.amazonaws.services.sqs.AmazonSQSClient;

/**
 * This class provides an umbrella to get a AWS tool client (S3, SQL, ...) while
 * handling the credentials.
 * <p>
 * The key and the secret key are first read in the nuxeo.conf. It they are not
 * found, the regular AWS environment variables are checked.
 * <p>
 * <b>Important</b>: The key ID and secret key are static, loaded and evaluated
 * only once during the whole life of the application
 * <p>
 * The tools (S3, SQS, ...) are created only once, for the first call to
 * <code>getS3Client()</code>, </code>getSLQClient()</code>, .... so you can
 * freely do something like:
 * <p>
 * <code>
 * GenericAWSClient awsC = GenericAWSClient.getInstance();
 * awsC.getS3Client().putObject(. . .);
 * awsC.getS3Client().getObject(. . .);
 *  . . .
 * </code>
 *
 * @since 7.1
 */
public class GenericAWSClient {

    // The keys in nuxeo.conf
    public static final String CONF_AWS_KEY_ACCESS = "aws.transcoder.key";

    public static final String CONF_AWS_KEY_SECRET = "aws.transcoder.secret";

    private static GenericAWSClient instance = null;

    // Names of env. variable names as expected by AWS, if used instead of
    // nuxeo.conf
    public static final String AWS_ENV_VAR_ACCESS_KEY = "AWS_ACCESS_KEY_ID";

    public static final String AWS_ENV_VAR_SECRET_KEY = "AWS_SECRET_ACCESS_KEY";

    private static String awsAccessKeyId;

    private static String awsSecretAccessKey;

    private static int awsKeysCheckedStatus = -1;

    private static AWSCredentialsProvider awsCredentialsProvider;

    private static AmazonS3 s3;

    private static AmazonElasticTranscoder elasticTranscoder;

    private static AmazonSQS sqs;

    private GenericAWSClient() {

        if (!loadAccessKeysFromConf()) {
            throw new ClientException(
                    "AWS Access Key ID/Secret Access Key are missing or invalid. Are they correctly set-up in nuxeo.conf or as System variables?");

        }

        buildCredentiaProvider();
    }

    public synchronized static GenericAWSClient getInstance() {

        if (instance == null) {
            instance = new GenericAWSClient();
        }

        return instance;
    }

    public AmazonS3 getS3Client() {

        if (s3 == null) {
            s3 = new AmazonS3Client(awsCredentialsProvider);
        }
        return s3;
    }

    public AmazonSQS getSQSClient() {

        if (sqs == null) {
            sqs = new AmazonSQSClient(awsCredentialsProvider);
        }
        return sqs;
    }

    public AmazonElasticTranscoder getElasticTranscoder() {

        if (elasticTranscoder == null) {
            elasticTranscoder = new AmazonElasticTranscoderClient(
                    awsCredentialsProvider);
        }

        return elasticTranscoder;
    }

    protected synchronized void buildCredentiaProvider() {
        if (awsCredentialsProvider == null) {
            awsCredentialsProvider = new SimpleAWSCredentialProvider(
                    awsAccessKeyId, awsSecretAccessKey);
        }
    }

    protected synchronized boolean loadAccessKeysFromConf() {

        if (awsKeysCheckedStatus == -1) {
            awsAccessKeyId = Framework.getProperty(CONF_AWS_KEY_ACCESS);
            awsSecretAccessKey = Framework.getProperty(CONF_AWS_KEY_SECRET);

            // Fallback if the keys are not here
            if (StringUtils.isBlank(awsAccessKeyId)) {
                awsAccessKeyId = System.getenv(AWS_ENV_VAR_ACCESS_KEY);
            }
            if (StringUtils.isBlank(awsSecretAccessKey)) {
                awsSecretAccessKey = System.getenv(AWS_ENV_VAR_SECRET_KEY);
            }

            if (StringUtils.isBlank(awsAccessKeyId)
                    || StringUtils.isBlank(awsSecretAccessKey)) {
                awsKeysCheckedStatus = 0;
            } else {
                awsKeysCheckedStatus = 1;
            }
        }

        return awsKeysCheckedStatus == 1;
    }

    /**
     * Utility method returning formatted details about the error. It the error
     * is not AmazonServiceException or AmazonClientException, the method just
     * returns <code>e.getMessage()</code>
     * 
     * @param e, an AmazonServiceException or AmazonClientException
     * @return
     *
     * @since 7.1
     */
    public static String buildDetailedMessageFromAWSException(Exception e) {

        String message = "";

        if (e instanceof AmazonServiceException) {
            AmazonServiceException ase = (AmazonServiceException) e;
            message = "Caught an AmazonServiceException, which "
                    + "means your request made it "
                    + "to Amazon S3, but was rejected with an error response"
                    + " for some reason.";
            message += "\nError Message:    " + ase.getMessage();
            message += "\nHTTP Status Code: " + ase.getStatusCode();
            message += "\nAWS Error Code:   " + ase.getErrorCode();
            message += "\nError Type:       " + ase.getErrorType();
            message += "\nRequest ID:       " + ase.getRequestId();

        } else if (e instanceof AmazonClientException) {
            AmazonClientException ace = (AmazonClientException) e;
            message = "Caught an AmazonClientException, which "
                    + "means the client encountered "
                    + "an internal error while trying to "
                    + "communicate with S3, "
                    + "such as not being able to access the network.";
            message += "\nError Message: " + ace.getMessage();

        } else {
            message = e.getMessage();
        }

        return message;
    }

}
