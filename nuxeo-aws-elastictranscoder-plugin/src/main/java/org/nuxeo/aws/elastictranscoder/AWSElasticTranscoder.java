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
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.ClientException;
import org.nuxeo.ecm.platform.picture.api.BlobHelper;
import org.nuxeo.runtime.api.Framework;

import com.amazonaws.services.elastictranscoder.AmazonElasticTranscoder;
import com.amazonaws.services.elastictranscoder.AmazonElasticTranscoderClient;
import com.amazonaws.services.elastictranscoder.model.CreateJobOutput;
import com.amazonaws.services.elastictranscoder.model.CreateJobRequest;
import com.amazonaws.services.elastictranscoder.model.CreateJobResult;
import com.amazonaws.services.elastictranscoder.model.Job;
import com.amazonaws.services.elastictranscoder.model.JobInput;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.AmazonClientException;
import com.amazonaws.AmazonServiceException;
import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.auth.profile.ProfileCredentialsProvider;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.amazonaws.services.s3.model.PutObjectResult;

/**
 * First draft uses polling to read job's status, this is good enough for first
 * POC, but be warned that AWS doc explicitly states that:
 * <p>
 * <quote> If you poll the Elastic Transcoder's ReadJob API to track job status,
 * you need to continuously call ReadJob on every submitted job. This
 * methodology cannot scale as the number of transcode jobs increases. To solve
 * this problem, Elastic Transcoder can publish notifications to Amazon SNS
 * which provides an event-driven mechanism for tracking job status. </quote>
 * <p>
 * Also, we don't dispatch videos in sub folders (for example
 * /userX/videos/filea, fieleb, ...
 *
 * @since 7.1
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

    protected String eTag;

    protected String presetId;

    protected String inputS3Bucket;

    protected String outputS3Bucket;

    protected String pipelineId;

    protected String awsJobId;

    protected AmazonS3 amazonS3;

    protected AmazonElasticTranscoder amazonElasticTranscoder;

    protected AWSCredentialsProvider awsCredentialsProvider;

    public AWSElasticTranscoder(Blob inBlob, String inPresetId,
            String inInputBucket, String inOutputBucket, String inPipelineId) {

        loadAccessKeysFromConf();
        if (awsKeysCheckedStatus != 1) {
            throw new ClientException(
                    "AWS Access Key ID/Secret Access Key are missing or invalid. Are they correctly set-up in nuxeo.conf or as System variables?");
        }

        blob = inBlob;
        fileOfBlob = BlobHelper.getFileFromBlob(blob);
        if (fileOfBlob == null) {
            // TODO
            // . . . create a temp file instead of giving up . . .
            throw new RuntimeException("Cannot get a File from this blob");
        }

        presetId = inPresetId;
        inputS3Bucket = inInputBucket;
        outputS3Bucket = inOutputBucket;
        pipelineId = inPipelineId;

        // Create the main AWS objects (S3 and Elastic Transcoder)
        awsCredentialsProvider = new SimpleAWSCredentialProvider(
                awsAccessKeyId, awsSecretAccessKey);
        amazonS3 = new AmazonS3Client(awsCredentialsProvider);
        amazonElasticTranscoder = new AmazonElasticTranscoderClient(
                awsCredentialsProvider);

    }

    public void transcode() {

        isRunning = true;

        // Send the file to the s3 inputS3Bucket
        sendFileToInputBucket();

        // Create the job
        createElasticTranscoderJob();
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

    /*
     * Here we are "ready" (fun to say that with such an empty method) to setup
     * the key (the file name + full path prefix) in the S3 bucket, so we could
     * put it in a folder/subfolder/etc.
     * 
     * Assume we have a valid blob in entry, default implementation: Just the
     * filename
     */
    protected String getKeyName() {

        return fileOfBlob.getName();
    }

    protected void sendFileToInputBucket() {

        try {

            // System.out.println("Uploading a new object to S3 from a file\n");
            PutObjectResult result = amazonS3.putObject(new PutObjectRequest(
                    inputS3Bucket, getKeyName(), fileOfBlob));
            eTag = result.getETag();

        } catch (AmazonServiceException ase) {
            String message = "Caught an AmazonServiceException, which "
                    + "means your request made it "
                    + "to Amazon S3, but was rejected with an error response"
                    + " for some reason.";
            message += "\nError Message:    " + ase.getMessage();
            message += "\nHTTP Status Code: " + ase.getStatusCode();
            message += "\nAWS Error Code:   " + ase.getErrorCode();
            message += "\nError Type:       " + ase.getErrorType();
            message += "\nRequest ID:       " + ase.getRequestId();

            throw new RuntimeException(message);

        } catch (AmazonClientException ace) {
            String message = "Caught an AmazonClientException, which "
                    + "means the client encountered "
                    + "an internal error while trying to "
                    + "communicate with S3, "
                    + "such as not being able to access the network.";
            message += "\nError Message: " + ace.getMessage();
            throw new RuntimeException(message);
        }

    }

    protected void createElasticTranscoderJob() {
        // (using code from the AWS code sample in
        // JobStatusNotificationsSample.java)

        // Setup the job input
        JobInput jobInput = new JobInput().withKey(getKeyName());

        // Setup the job output using the provided input key to generate an
        // output key.
        List<CreateJobOutput> outputs = new ArrayList<CreateJobOutput>();
        CreateJobOutput output = new CreateJobOutput().withKey(getKeyName());
        output.withPresetId(presetId);
        outputs.add(output);

        // Create a job on the specified pipeline and get the job ID
        CreateJobRequest createJobRequest = new CreateJobRequest();
        createJobRequest.withPipelineId(pipelineId);
        createJobRequest.withInput(jobInput);
        createJobRequest.withOutputs(outputs);

        CreateJobResult cjr = amazonElasticTranscoder.createJob(createJobRequest);
        Job job = cjr.getJob();
        awsJobId = job.getId();

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
