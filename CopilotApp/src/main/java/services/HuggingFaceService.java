package services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.util.EntityUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@ApplicationScoped
public class HuggingFaceService {
    private String apiUrl;
    private String apiToken;
    private CloseableHttpClient httpClient;
    private ObjectMapper objectMapper;

    @PostConstruct
    public void init() {
        apiUrl = "https://router.huggingface.co/v1/chat/completions";
        apiToken = "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";
        
        RequestConfig config = RequestConfig.custom()
            .setConnectTimeout(5000)
            .setConnectionRequestTimeout(5000)
            .setSocketTimeout(90000)
            .build();
            
        httpClient = HttpClientBuilder.create()
            .setDefaultRequestConfig(config)
            .build();
            
        objectMapper = new ObjectMapper();
    }

    public String getResponse(String prompt) throws IOException {


        int maxAttempts = 2;
        int retryDelay = 1000;
        
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                return tryGetResponse(prompt);
            } catch (IOException e) {
                if (attempt == maxAttempts) {
                    throw new IOException("Ошибка после " + maxAttempts + " попыток: " + e.getMessage(), e);
                }
                
                try {
                    Thread.sleep(retryDelay);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new IOException("Запрос прерван", ie);
                }
                
                retryDelay *= 2;
            }
        }
        
        throw new IOException("Не удалось получить ответ после всех попыток");
    }

    private String tryGetResponse(String prompt) throws IOException {
        HttpPost request = new HttpPost(apiUrl);
        request.setHeader("Authorization", "Bearer " + apiToken);
        request.setHeader("Content-Type", "application/json");

        ObjectNode jsonBody = objectMapper.createObjectNode();
        jsonBody.put("model", "Qwen/Qwen2.5-7B-Instruct");
        jsonBody.put("stream", false);
        
        ArrayNode messages = jsonBody.putArray("messages");
        ObjectNode userMessage = messages.addObject();
        userMessage.put("role", "user");
        userMessage.put("content", prompt);
        
        request.setEntity(new StringEntity(jsonBody.toString(), StandardCharsets.UTF_8));

        try (CloseableHttpResponse response = httpClient.execute(request)) {
            String responseBody = EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
            int statusCode = response.getStatusLine().getStatusCode();

            if (statusCode != 200) {
                try {
                    JsonNode errorNode = objectMapper.readTree(responseBody);
                    String errorMsg = errorNode.has("error") ? 
                        errorNode.get("error").asText() : 
                        errorNode.has("message") ? 
                        errorNode.get("message").asText() : 
                        responseBody;
                    throw new IOException("API Error " + statusCode + ": " + errorMsg);
                } catch (Exception e) {
                    throw new IOException("API Error " + statusCode + ": " + responseBody);
                }
            }

            JsonNode root = objectMapper.readTree(responseBody);
            if (root.has("choices") && root.get("choices").isArray() && root.get("choices").size() > 0) {
                JsonNode firstChoice = root.get("choices").get(0);
                if (firstChoice.has("message") && firstChoice.get("message").has("content")) {
                    return firstChoice.get("message").get("content").asText().trim();
                }
            }
            throw new IOException("Некорректный формат ответа: " + responseBody);
        }
    }
}