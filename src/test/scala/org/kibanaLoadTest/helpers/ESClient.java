package org.kibanaLoadTest.helpers;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import co.elastic.clients.elasticsearch.core.bulk.BulkResponseItem;
import co.elastic.clients.elasticsearch.indices.CreateIndexRequest;
import co.elastic.clients.json.JsonData;
import co.elastic.clients.json.JsonpMapper;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.ElasticsearchTransport;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import io.circe.Json;
import jakarta.json.spi.JsonProvider;
import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestClientBuilder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.sql.SQLOutput;
import java.util.Arrays;

public class ESClient {

    private static ESClient INSTANCE;
    private ElasticsearchClient client;
    private RestClient restClient;
    private Logger logger = LoggerFactory.getLogger("ES_Client");
    private int BULK_SIZE_DEFAULT = 100;
    private int BULK_SIZE = 300;

    private ESClient(String hostname, String username, String password) {
        final CredentialsProvider credentialsProvider = new BasicCredentialsProvider();
        credentialsProvider.setCredentials(AuthScope.ANY, new UsernamePasswordCredentials(username, password));

        RestClientBuilder builder = RestClient
                .builder(HttpHost.create(hostname))
                .setHttpClientConfigCallback(httpClientBuilder -> httpClientBuilder
                        .setDefaultCredentialsProvider(credentialsProvider)
                )
                .setRequestConfigCallback(httpClientBuilder -> httpClientBuilder
                        .setConnectTimeout(30000)
                        .setConnectionRequestTimeout(90000)
                        .setSocketTimeout(90000)
                );

        restClient = builder.build();

        // Create the transport with a Jackson mapper
        ElasticsearchTransport transport = new RestClientTransport(restClient, new JacksonJsonpMapper());

        // And create the API client
        client = new ElasticsearchClient(transport);
    }

    public static ESClient getInstance(String hostname, String username, String password) {
        if (INSTANCE == null) {
            INSTANCE = new ESClient(hostname, username, password);
        }
        return INSTANCE;
    }

    public void createIndex(String indexName, Json source) {
        InputStream is = new ByteArrayInputStream(source.toString().getBytes());
        CreateIndexRequest req = CreateIndexRequest.of(b -> b.index(indexName).withJson(is));
        try {
            boolean created = client.indices().create(req).acknowledged();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void bulk(String indexName, Json[] jsonArray, Integer chunkSize) {
        logger.info(String.format("Ingesting to %s index: %d docs", indexName, jsonArray.length));
        int bulkSize = indexName == "gatling-data" ? BULK_SIZE_DEFAULT : chunkSize;

        for (int i = 0; i < jsonArray.length; i += bulkSize) {
            Json[] chunk = Arrays.copyOfRange(jsonArray, i, Math.min(jsonArray.length, i + chunkSize));
            BulkRequest.Builder br = new BulkRequest.Builder();
            for (Json json : chunk) {
                JsonpMapper jsonpMapper = client._transport().jsonpMapper();
                JsonProvider jsonProvider = jsonpMapper.jsonProvider();
                InputStream is = new ByteArrayInputStream(json.toString().getBytes());
                JsonData res = JsonData.from(jsonProvider.createParser(is), jsonpMapper);
                br.operations(op -> op.index(idx -> idx.index(indexName).document(res)));
            }
            try {
                BulkResponse result = client.bulk(br.build());

                if (result.errors()) {
                    logger.error("Bulk had errors");
                    for (BulkResponseItem item : result.items()) {
                        if (item.error() != null) {
                            logger.error(item.error().reason());
                        }
                    }
                }
            } catch (IOException e) {
                logger.error("Bulk upload failed");
            }
        }
    }

    public void closeConnection() {
        if (INSTANCE != null) {
            logger.info("Closing connection");
            try {
                restClient.close();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

}
