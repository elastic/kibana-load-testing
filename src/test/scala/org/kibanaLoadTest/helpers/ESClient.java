package org.kibanaLoadTest.helpers;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import co.elastic.clients.elasticsearch.core.bulk.BulkResponseItem;
import co.elastic.clients.elasticsearch.indices.CreateIndexRequest;
import co.elastic.clients.elasticsearch.indices.DeleteIndexRequest;
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

import java.io.IOException;
import java.io.StringReader;
import java.net.URL;
import java.util.Arrays;
import java.util.Date;
import java.util.concurrent.TimeUnit;

public class ESClient {
    private static ESClient INSTANCE = null;
    private ElasticsearchClient client;
    private RestClient restClient;
    private Logger logger = LoggerFactory.getLogger("ES_Client");
    private int BULK_SIZE_DEFAULT = 100;

    private ESClient(URL url, String username, String password) {
        final CredentialsProvider credentialsProvider = new BasicCredentialsProvider();
        credentialsProvider.setCredentials(AuthScope.ANY, new UsernamePasswordCredentials(username, password));

        RestClientBuilder builder = RestClient
                .builder(new HttpHost(url.getHost(), url.getPort(), url.getProtocol()))
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

    public static ESClient getInstance(URL url, String username, String password) {
        if (INSTANCE == null) {
            INSTANCE = new ESClient(url, username, password);
        }
        return INSTANCE;
    }

    public void createIndex(String indexName, Json source) {
        CreateIndexRequest req = CreateIndexRequest.of(b -> b.index(indexName).withJson(new StringReader(source.toString())));
        try {
            boolean created = client.indices().create(req).acknowledged();
            if (created) {
                logger.info(String.format("Created index '%s'", indexName));
            } else {
                logger.error(String.format("Failed to create index '%s'", indexName));
            }
        } catch (IOException | ElasticsearchException ex) {
            throw new RuntimeException(String.format("Failed to create index", indexName), ex);
        }
    }

    public void deleteIndex(String indexName) {
        DeleteIndexRequest req = DeleteIndexRequest.of(b -> b.index(indexName));
        try {
            boolean deleted = client.indices().delete(req).acknowledged();
            if (deleted) {
                logger.info(String.format("Deleted index '%s'", indexName));
            } else {
                logger.error(String.format("Failed to delete index '%s'", indexName));
            }
        } catch (IOException | ElasticsearchException ex) {
            throw new RuntimeException(String.format("Failed to delete index '%s'", indexName), ex);
        }
    }

    public void bulk(String indexName, Json[] jsonArray, int chunkSize) {
        logger.info(String.format("Ingesting to %s index: %d docs", indexName, jsonArray.length));
        int bulkSize = indexName == "gatling-data" ? BULK_SIZE_DEFAULT : chunkSize;
        Date startTime = new Date();

        for (int i = 0; i < jsonArray.length; i += bulkSize) {
            Json[] chunk = Arrays.copyOfRange(jsonArray, i, Math.min(jsonArray.length, i + chunkSize));
            BulkRequest.Builder br = new BulkRequest.Builder();
            for (Json json : chunk) {
                JsonpMapper jsonpMapper = client._transport().jsonpMapper();
                JsonProvider jsonProvider = jsonpMapper.jsonProvider();
                JsonData res = JsonData.from(jsonProvider.createParser(new StringReader(json.toString())), jsonpMapper);
                br.operations(op -> op.create(idx -> idx.index(indexName).document(res)));
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
            } catch (IOException | ElasticsearchException ex) {
                throw new RuntimeException(String.format("Bulk ingest for %s", indexName), ex);
            }
        }

        long diff = Math.abs(new Date().getTime() - startTime.getTime());
        long min = TimeUnit.MILLISECONDS.toMinutes(diff);
        long sec = TimeUnit.MILLISECONDS.toSeconds(diff - min * 60 * 1000);
        logger.info(String.format("Ingestion is completed. Took: %s minutes %s seconds", min, sec));
    }

    public void closeConnection() {
        if (INSTANCE != null) {
            try {
                restClient.close();
            } catch (IOException e) {
                throw new RuntimeException("IO Exception in esClient.closeConnection", e);
            } finally {
                INSTANCE = null;
            }
        }
    }
}
