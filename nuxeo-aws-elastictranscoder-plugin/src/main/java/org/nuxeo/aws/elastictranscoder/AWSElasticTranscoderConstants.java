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
package org.nuxeo.aws.elastictranscoder;

import org.nuxeo.runtime.api.Framework;

/**
 * 
 *
 * @since 7.1
 */
public class AWSElasticTranscoderConstants {

    // ================================================== KEYs for AUTHORIZATION
    // The keys in nuxeo.conf
    public static final String CONF_AWS_KEY_ACCESS = "aws.transcoder.key";

    public static final String CONF_AWS_KEY_SECRET = "aws.transcoder.secret";

    // Names of env. variable names as expected by AWS, if used instead of
    // nuxeo.conf
    public static final String AWS_ENV_VAR_ACCESS_KEY = "AWS_ACCESS_KEY_ID";

    public static final String AWS_ENV_VAR_SECRET_KEY = "AWS_SECRET_ACCESS_KEY";

    // ================================================== OTHER KEYS
    public static final String CONF_KEY_INPUT_BUCKET = "aws.transcoder.default.bucket.input";

    public static final String CONF_KEY_OUTPUT_BUCKET = "aws.transcoder.default.bucket.output";

    public static final String CONF_KEY_PIPELINE_ID = "aws.transcoder.default.pipelineid";

    public static final String CONF_KEY_PRESET_ID = "aws.transcoder.default.presetid";

    public static final String CONF_KEY_SQS_URL = "aws.transcoder.default.sqs.url";


    // ================================================== Loaded values
    private static String inputBucket;

    private static String outputBucket;

    private static String pipelineId;

    private static String presetId;

    private static String sqsQueueUrl;
    
    public static String getDefaultBucketInput() {
        if(inputBucket == null) {
            inputBucket = Framework.getProperty(CONF_KEY_INPUT_BUCKET);
        }
        
        return inputBucket;
    }
    
    public static String getDefaultBucketOutput() {
        if(outputBucket == null) {
            outputBucket = Framework.getProperty(CONF_KEY_OUTPUT_BUCKET);
        }
        
        return outputBucket;
    }
    
    public static String getDefaultPipelineId() {
        if(pipelineId == null) {
            pipelineId = Framework.getProperty(CONF_KEY_PIPELINE_ID);
        }
        
        return pipelineId;
    }
    
    public static String getDefaultPresetId() {
        if(presetId == null) {
            presetId = Framework.getProperty(CONF_KEY_PRESET_ID);
        }
        
        return presetId;
    }
    
    public static String getDefaultSqsQueueUrl() {
        if(sqsQueueUrl == null) {
            sqsQueueUrl = Framework.getProperty(CONF_KEY_SQS_URL);
        }
        
        return sqsQueueUrl;
    }

}
