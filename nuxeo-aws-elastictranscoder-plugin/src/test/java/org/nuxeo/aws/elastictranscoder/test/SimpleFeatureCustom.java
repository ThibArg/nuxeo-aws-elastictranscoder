/*
 * (C) Copyright 2016 Nuxeo SA (http://nuxeo.com/) and others.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Contributors:
 *     Thibaud Arguillere
 */
package org.nuxeo.aws.elastictranscoder.test;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;

import org.nuxeo.aws.elastictranscoder.AWSElasticTranscoderConstants;
import org.nuxeo.common.utils.FileUtils;
import org.nuxeo.runtime.test.runner.FeaturesRunner;
import org.nuxeo.runtime.test.runner.SimpleFeature;

/**
 * Important: To test the feature, we don't want to hard code the AWS keys
 * (since this code could be published on GitHub for example) and we don't want
 * to hard code the bucket name or the distant object key, since everyone will
 * have a different one. So, the principles used are the following:
 * <ul>
 * <li>We have a file named aws-test.conf at src/test/resources/</li>
 * <li>The file contains the keys, the buckets, , ... using the kes defined
 * below</li>
 * <li>The .gitignore config file ignores this file, so it is not sent on GitHub
 * </li>
 * </ul>
 * So, basically to run the test, create this file at n/test/resources/ and set
 * the following properties:
 *
 * <pre>
 * {@code
 * aws.et.test.key=VALUE_HERE
 * aws.et.test.secret=VALUE_HERE
 * aws.et.test.bucket.input=VALUE_HERE
 * aws.et.test.bucket.output=VALUE_HERE
 * aws.et.test.pipeline=VALUE_HERE
 * aws.et.test.pipelineid=VALUE_HERE
 * aws.et.test.sqs.queue.url=VALUE_HERE
 * }
 * </pre>
 * <p>
 * These properties will be loaded and set in the environment, so the default
 * contributions will use them.
 *
 * @since 8.3
 */
public class SimpleFeatureCustom extends SimpleFeature {

    public static final String TEST_CONF_FILE = "aws-test.conf";

    public static final String TEST_KEY_AWS_KEY = "aws.et.test.key";

    public static final String TEST_KEY_AWS_SECRET = "aws.et.test.secret";

    public static final String TEST_KEY_AWS_INPUT_BUCKET = "aws.et.test.bucket.input";

    public static final String TEST_KEY_AWS_OUTPUT_BUCKET = "aws.et.test.bucket.output";

    public static final String TEST_KEY_AWS_PIPE_LINE = "aws.et.test.pipeline";

    public static final String TEST_KEY_AWS_PIPELINE_ID = "aws.et.test.pipelineid";

    public static final String TEST_KEY_AWS_SQS_QUEUE_URL = "aws.et.test.sqs.queue.url";

    protected static Properties props = null;

    public static String getLocalProperty(String key) {

        if (props != null) {
            return props.getProperty(key);
        }

        return null;
    }

    public static boolean hasLocalTestConfiguration() {
        return props != null;
    }

    @Override
    public void initialize(FeaturesRunner runner) throws Exception {

        File file = null;
        FileInputStream fileInput = null;
        try {
            file = FileUtils.getResourceFileFromContext(TEST_CONF_FILE);
            fileInput = new FileInputStream(file);
            props = new Properties();
            props.load(fileInput);

        } catch (Exception e) {
            props = null;
        } finally {
            if (fileInput != null) {
                try {
                    fileInput.close();
                } catch (IOException e) {
                    // Ignore
                }
                fileInput = null;
            }
        }

        if (props != null) {

            Properties systemProps = System.getProperties();
            systemProps.setProperty(AWSElasticTranscoderConstants.CONF_AWS_KEY_ACCESS,
                    props.getProperty(TEST_KEY_AWS_KEY));
            systemProps.setProperty(AWSElasticTranscoderConstants.CONF_AWS_KEY_SECRET,
                    props.getProperty(TEST_KEY_AWS_SECRET));
            systemProps.setProperty(AWSElasticTranscoderConstants.CONF_KEY_INPUT_BUCKET,
                    props.getProperty(TEST_KEY_AWS_INPUT_BUCKET));
            systemProps.setProperty(
                    AWSElasticTranscoderConstants.CONF_KEY_OUTPUT_BUCKET,
                    props.getProperty(TEST_KEY_AWS_OUTPUT_BUCKET));
            systemProps.setProperty(
                    AWSElasticTranscoderConstants.CONF_KEY_PIPELINE_ID,
                    props.getProperty(TEST_KEY_AWS_PIPELINE_ID));
            systemProps.setProperty(
                    AWSElasticTranscoderConstants.CONF_KEY_SQS_URL,
                    props.getProperty(TEST_KEY_AWS_SQS_QUEUE_URL));

        }
    }

    @Override
    public void stop(FeaturesRunner runner) throws Exception {

        Properties p = System.getProperties();
        p.remove(AWSElasticTranscoderConstants.CONF_AWS_KEY_ACCESS);
        p.remove(AWSElasticTranscoderConstants.CONF_AWS_KEY_SECRET);
        p.remove(AWSElasticTranscoderConstants.CONF_KEY_INPUT_BUCKET);
        p.remove(AWSElasticTranscoderConstants.CONF_KEY_OUTPUT_BUCKET);
        p.remove(AWSElasticTranscoderConstants.CONF_KEY_PIPELINE_ID);
        p.remove(AWSElasticTranscoderConstants.CONF_KEY_SQS_URL);
    }

}
