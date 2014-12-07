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

import java.io.File;
import java.io.IOException;

import org.nuxeo.ecm.core.api.impl.blob.FileBlob;
import org.nuxeo.runtime.api.Framework;

import com.amazonaws.AmazonClientException;
import com.amazonaws.AmazonServiceException;
import com.amazonaws.services.s3.model.GetObjectRequest;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.google.common.io.Files;

/**
 * Generic code, encapsulates send/get/delete file in a S3 bucket
 * <p>
 * <i>IMPORTANT</i>: Uses the <code>GenericAWSClient</code>, so the S3 client is
 * shared
 * 
 * @since 7.1
 */
public class AWSS3Handler {

    protected String bucket;

    protected GenericAWSClient genericAwsClient;

    public AWSS3Handler(String inBucket) {
        bucket = inBucket;

        genericAwsClient = GenericAWSClient.getInstance();
    }

    public void sendFile(String inKey, File inFile) throws RuntimeException {

        try {
            genericAwsClient.getS3Client().putObject(
                    new PutObjectRequest(bucket, inKey, inFile));

        } catch (AmazonServiceException ase) {
            String message = GenericAWSClient.buildDetailedMessageFromAWSException(ase);
            throw new RuntimeException(message);

        } catch (AmazonClientException ace) {
            String message = GenericAWSClient.buildDetailedMessageFromAWSException(ace);
            throw new RuntimeException(message);
        }
    }

    public FileBlob downloadFile(String inKey, String inFileName)
            throws IOException, RuntimeException {

        ObjectMetadata metadata = null;
        FileBlob result = null;

        File tmp = File.createTempFile("NxAWSET-",
                Files.getFileExtension(inFileName));
        tmp.deleteOnExit();
        Framework.trackFile(tmp, this);

        try {
            GetObjectRequest gor = new GetObjectRequest(bucket, inKey);
            metadata = genericAwsClient.getS3Client().getObject(gor, tmp);

        } catch (AmazonServiceException ase) {
            String message = GenericAWSClient.buildDetailedMessageFromAWSException(ase);
            throw new RuntimeException(message);

        } catch (AmazonClientException ace) {
            String message = GenericAWSClient.buildDetailedMessageFromAWSException(ace);
            throw new RuntimeException(message);

        }

        if (metadata != null && tmp.exists()) {
            result = new FileBlob(tmp);
            result.setFilename(inFileName);
            result.setMimeType(metadata.getContentType());
        }
        return result;
    }

    public void deleteFile(String inKey) throws RuntimeException {
        
        try {
            genericAwsClient.getS3Client().deleteObject(bucket, inKey);
        } catch (AmazonServiceException ase) {
            String message = GenericAWSClient.buildDetailedMessageFromAWSException(ase);
            throw new RuntimeException(message);

        } catch (AmazonClientException ace) {
            String message = GenericAWSClient.buildDetailedMessageFromAWSException(ace);
            throw new RuntimeException(message);

        }
    }

}
