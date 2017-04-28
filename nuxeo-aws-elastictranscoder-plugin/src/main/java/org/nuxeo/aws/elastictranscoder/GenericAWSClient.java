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
import org.nuxeo.ecm.core.api.NuxeoException;
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
import com.amazonaws.regions.Region;
import com.amazonaws.regions.Regions;

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
 * GenericAWSClient awsC = new GenericAWSClient.getInstance();
 * // . . .
 * awsC.getS3Client().putObject(. . .);
 * // . . .
 * awsC.getS3Client().getObject(. . .);
 * // . . .
 * </code>
 * <p>
 *
 * @since 7.1
 */
public class GenericAWSClient {

    private static String awsAccessKeyId;

    private static String awsSecretAccessKey;

    private static int awsKeysCheckedStatus = -1;

    private static AWSCredentialsProvider awsCredentialsProvider = null;

    protected AmazonS3 s3;

    protected AmazonElasticTranscoder elasticTranscoder;

    protected AmazonSQS sqs;

    private static String buildCredentiaProviderLock = "Lock";

    public GenericAWSClient() {

        buildCredentiaProvider();

        if (awsCredentialsProvider == null) {
            throw new NuxeoException(
                    "AWS Access Key ID/Secret Access Key are missing or invalid. Are they correctly set-up in nuxeo.conf or as System variables?");

        }

        buildCredentiaProvider();
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
            if (!StringUtils.isBlank(System.getenv(AWSElasticTranscoderConstants.AWS_REGION))) {
              Region region = Region.getRegion(Regions.fromName(System.getenv(AWSElasticTranscoderConstants.AWS_REGION)));
              elasticTranscoder.setRegion(region);
          }
        }

        return elasticTranscoder;
    }

    protected void buildCredentiaProvider() {

        if (awsKeysCheckedStatus == -1) {
            synchronized (buildCredentiaProviderLock) {
                // Another thread may have filled the variables while we were
                // acquiring the lock => check again.
                if (awsKeysCheckedStatus == -1) {
                    awsAccessKeyId = Framework.getProperty(AWSElasticTranscoderConstants.CONF_AWS_KEY_ACCESS);
                    awsSecretAccessKey = Framework.getProperty(AWSElasticTranscoderConstants.CONF_AWS_KEY_SECRET);

                    // Fallback if the keys are not there
                    if (StringUtils.isBlank(awsAccessKeyId)) {
                        awsAccessKeyId = System.getenv(AWSElasticTranscoderConstants.AWS_ENV_VAR_ACCESS_KEY);
                    }
                    if (StringUtils.isBlank(awsSecretAccessKey)) {
                        awsSecretAccessKey = System.getenv(AWSElasticTranscoderConstants.AWS_ENV_VAR_SECRET_KEY);
                    }

                    if (StringUtils.isBlank(awsAccessKeyId)
                            || StringUtils.isBlank(awsSecretAccessKey)) {
                        awsKeysCheckedStatus = 0;
                    } else {
                        awsKeysCheckedStatus = 1;
                    }

                    if (awsKeysCheckedStatus == 1) {
                        awsCredentialsProvider = new SimpleAWSCredentialProvider(
                                awsAccessKeyId, awsSecretAccessKey);
                    } else {
                        awsCredentialsProvider = null;
                    }
                }
            }
        }

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
