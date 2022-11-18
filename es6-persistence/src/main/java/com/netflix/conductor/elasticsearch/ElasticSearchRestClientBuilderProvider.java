/*
 * Copyright 2020 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package com.netflix.conductor.elasticsearch;

import java.net.URI;
import java.util.List;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.inject.Provider;
import org.apache.http.HttpHost;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestClientBuilder;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.net.ssl.SSLContext;

import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.ssl.SSLContextBuilder;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestClientBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ElasticSearchRestClientBuilderProvider implements Provider<RestClientBuilder> {
    private static Logger logger = LoggerFactory.getLogger(ElasticSearchRestClientProvider.class);

    private final ElasticSearchConfiguration configuration;

    @Inject
    public ElasticSearchRestClientBuilderProvider(ElasticSearchConfiguration configuration) {
        this.configuration = configuration;
    }

    @Override
    public RestClientBuilder get() {
        try {
            RestClientBuilder builder = RestClient.builder(convertToHttpHosts(configuration.getURIs()));
    		final CredentialsProvider credentialsProvider = new BasicCredentialsProvider();
        	if(null != this.configuration.getUserid()) {
        		credentialsProvider.setCredentials(AuthScope.ANY, 
        			new UsernamePasswordCredentials(this.configuration.getUserid(), this.configuration.getPassword()));
        	}
        	if(configuration.getURIs().size() > 0) {
        		if("https".equalsIgnoreCase(configuration.getURIs().get(0).getScheme())) {
            		SSLContext sslContext = new SSLContextBuilder().loadTrustMaterial(null, (x509Certificates, s) -> true).build();
                    builder.setHttpClientConfigCallback(httpClientBuilder -> httpClientBuilder.setDefaultCredentialsProvider(credentialsProvider)
                      	.setSSLHostnameVerifier(NoopHostnameVerifier.INSTANCE).setSSLContext(sslContext));
        		} else if("http".equalsIgnoreCase(configuration.getURIs().get(0).getScheme())) {
                    builder.setHttpClientConfigCallback(httpClientBuilder -> httpClientBuilder.setDefaultCredentialsProvider(credentialsProvider));
        		}
        	}
            return builder;
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
            return null;
        }
    }

    private HttpHost[] convertToHttpHosts(List<URI> hosts) {
        List<HttpHost> list = hosts.stream()
                .map(host -> new HttpHost(host.getHost(), host.getPort(), host.getScheme()))
                .collect(Collectors.toList());

        return list.toArray(new HttpHost[list.size()]);
    }
}
