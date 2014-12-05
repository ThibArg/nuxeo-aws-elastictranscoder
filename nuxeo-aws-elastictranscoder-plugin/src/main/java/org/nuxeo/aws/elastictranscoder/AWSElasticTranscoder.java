/*
 * (C) Copyright 2014 Nuxeo SA (http://nuxeo.com/) and others.
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

package org.nuxeo.aws.elastictranscoder;

import java.io.File;
import java.io.IOException;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.ClientException;
import org.nuxeo.ecm.platform.picture.api.BlobHelper;
import org.nuxeo.runtime.api.Framework;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.AmazonClientException;
import com.amazonaws.AmazonServiceException;
import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.profile.ProfileCredentialsProvider;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.model.PutObjectRequest;

/**
 * First draft uses polling to read job's status, this is good enough for first
 * POC, but be warned that AWS doc explicitly states that:
 * <p>
 * <quote> If you poll the Elastic Transcoder's ReadJob API to track job status,
 * you need to continuously call ReadJob on every submitted job. This
 * methodology cannot scale as the number of transcode jobs increases. To solve
 * this problem, Elastic Transcoder can publish notifications to Amazon SNS
 * which provides an event-driven mechanism for tracking job status. </quote>
 * 
 *
 * @since TODO
 */
public class AWSElasticTranscoder {

    private static final Log log = LogFactory.getLog(AWSElasticTranscoder.class);

    // The keys in nuxeo.conf
    public static final String CONF_AWS_KEY_ACCESS = "aws.transcoder.key";

    public static final String CONF_AWS_KEY_SECRET = "aws.transcoder.secret";

    protected static String awsAccessKeyId;

    protected static String awsSecretAccessKey;

    protected static int awsKeysCheckedStatus = -1;

    protected boolean isRunning = true;

    protected Blob blob;
    
    protected File fileOfBlob;

    protected String preset;

    protected String inputS3Bucket;

    protected String outputS3Bucket;

    protected String pipeline;

    protected String awsJobId;

    protected AmazonS3 amazonS3;

    protected AWSCredentialsProvider awsCredentialsProvider;

    public AWSElasticTranscoder(Blob inBlob, String inPreset,
            String inInputBucket, String inOutputBucket, String inPipeline) {

        loadAccessKeysFromConf();
        if (awsKeysCheckedStatus != 1) {
            throw new ClientException(
                    "AWS Access Key ID/Secret Access Key are missing or invalid. Are they correctly set-up in nuxeo.conf or as System variables?");
        }

        blob = inBlob;
        fileOfBlob = BlobHelper.getFileFromBlob(blob);
        if(fileOfBlob == null) {
            // TODO
            // . . . create a temp file instead of giving up . . .
            throw new RuntimeException("Cannot get a File from this blob");
        }
        
        preset = inPreset;
        inputS3Bucket = inInputBucket;
        outputS3Bucket = inOutputBucket;
        pipeline = inPipeline;
        
        // THIBAUD => PAS SUR QUE CA MARCHE
        awsCredentialsProvider = new ProfileCredentialsProvider( awsAccessKeyId, awsSecretAccessKey);
        amazonS3 = new AmazonS3Client(awsCredentialsProvider);
    }

    public void transcode() {

        isRunning = true;
        
        sendFile();

        // Send the file to the s3 inputS3Bucket
        // . . .

        // Start the job
        // awsJobId = . . .

        // Wait completion
        // . . .

        isRunning = false;

    }

    public boolean done() {

        return !isRunning;
    }

    public File getTranscodedFile() {

        return null;
    }

    protected void sendFile() {
        
        try {
            
            System.out.println("Uploading a new object to S3 from a file\n");
            amazonS3.putObject(new PutObjectRequest(
                    inputS3Bucket, "toto", fileOfBlob));
            
        } catch (AmazonServiceException ase) {
            System.out.println("Caught an AmazonServiceException, which " +
                    "means your request made it " +
                    "to Amazon S3, but was rejected with an error response" +
                    " for some reason.");
            System.out.println("Error Message:    " + ase.getMessage());
            System.out.println("HTTP Status Code: " + ase.getStatusCode());
            System.out.println("AWS Error Code:   " + ase.getErrorCode());
            System.out.println("Error Type:       " + ase.getErrorType());
            System.out.println("Request ID:       " + ase.getRequestId());
        } catch (AmazonClientException ace) {
            System.out.println("Caught an AmazonClientException, which " +
                    "means the client encountered " +
                    "an internal error while trying to " +
                    "communicate with S3, " +
                    "such as not being able to access the network.");
            System.out.println("Error Message: " + ace.getMessage());
        }

    }

    protected synchronized void loadAccessKeysFromConf() {

        if (awsKeysCheckedStatus == -1) {
            awsAccessKeyId = Framework.getProperty(CONF_AWS_KEY_ACCESS);
            awsSecretAccessKey = Framework.getProperty(CONF_AWS_KEY_SECRET);

            // Fallback if the keys are not here
            if (awsAccessKeyId == null || awsAccessKeyId.isEmpty()) {
                awsAccessKeyId = System.getenv("AWS_ACCESS_KEY_ID");
            }
            if (awsSecretAccessKey == null || awsSecretAccessKey.isEmpty()) {
                awsSecretAccessKey = System.getenv("AWS_SECRET_ACCESS_KEY");
            }

            if (awsAccessKeyId == null || awsAccessKeyId.isEmpty()) {
                awsAccessKeyId = System.getenv("AWS_ACCESS_KEY_ID");
            }

            if (awsSecretAccessKey == null || awsSecretAccessKey.isEmpty()
                    || awsAccessKeyId == null || awsAccessKeyId.isEmpty()) {
                awsKeysCheckedStatus = 0;
            } else {
                awsKeysCheckedStatus = 1;
            }
        }
    }

}
