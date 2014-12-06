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

import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;

/**
 * We want to allow using nuxeo.conf to setup the keys, so we need our basic
 * custom credential provider.
 *
 * @since 7.1
 */
public class SimpleAWSCredentialProvider implements AWSCredentialsProvider {

    protected BasicAWSCredentials basicAWSCredentials;

    public SimpleAWSCredentialProvider(String awsID, String awsSecret) {
        this.basicAWSCredentials = new BasicAWSCredentials(awsID, awsSecret);
    }

    public AWSCredentials getCredentials() {
        return basicAWSCredentials;
    }

    public void refresh() {
        // noop
    }

}
