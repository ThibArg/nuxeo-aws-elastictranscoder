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

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.ClientException;
import org.nuxeo.runtime.api.Framework;

public class AWSElasticTranscoder {

    private static final Log log = LogFactory.getLog(AWSElasticTranscoder.class);

    // The keys in nuxeo.conf
    public static final String CONF_AWS_KEY_ACCESS = "aws.access.key";

    public static final String CONF_AWS_KEY_SECRET = "aws.secret.access.key";

    protected static String awsKeyAccess;

    protected static String awsSecretAccessKey;

    protected static String awsFinalKeyBase64;

    protected static boolean awsKeysChecked = false;

    protected Blob blob;

    protected String preset;

    protected String inputS3Bucket;

    protected String outputS3Bucket;

    protected String pipeline;

    protected String awsJobId;

    public AWSElasticTranscoder(Blob inBlob, String inPreset) {
        
        loadAccessKeysFromConf();
        if (awsFinalKeyBase64 == null || awsFinalKeyBase64.isEmpty()) {
            throw new ClientException(
                    "AWS keys are missing or invalid. Are they correctly set-up in nuxeo.conf?");
        }

        blob = inBlob;
        preset = inPreset;

        // etc.
    }

    public void transcode() {

        // Send the file to the s3 inputS3Bucket
        // . . .

        // Start the job
        // awsJobId = . . .

    }

    public boolean done() {

        return false;
    }

    public File getTranscodedFile() {

        return null; // the transcoded file
    }

    protected synchronized void loadAccessKeysFromConf() {

        if (!awsKeysChecked) {
            awsKeyAccess = Framework.getProperty(CONF_AWS_KEY_ACCESS);
            awsSecretAccessKey = Framework.getProperty(CONF_AWS_KEY_SECRET);

            awsKeysChecked = true;
            awsFinalKeyBase64 = null;
            if (awsKeyAccess != null && awsSecretAccessKey != null) {
                String toEncode = awsKeyAccess + ":" + awsSecretAccessKey;
                awsFinalKeyBase64 = new String(
                        Base64.encodeBase64(toEncode.getBytes()));
            }
        }
    }

}
